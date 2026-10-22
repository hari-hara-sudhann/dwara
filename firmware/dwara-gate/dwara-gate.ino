/**
 * ============================================================================
 *  DWARA — ESP32 Gate Controller (servo on D27, pluggable backend transport)
 * ============================================================================
 *  Spec: dwara-architecture-spec.md §3.3/§3.5 (flows), §6 (BLE), §7 (firmware),
 *  D1/D5/D6/D7/D15/D17.
 *
 *  Hardware: ESP32 (20-pin dev board), MG90S servo signal on D27 (GPIO27),
 *  networking-mode jumper on D26 (GPIO26, INPUT_PULLUP).
 *
 *  Networking is abstract (BackendTransport.h). At boot the firmware samples
 *  NETWORK_MODE_PIN:
 *    - HIGH (not grounded) -> WiFiBackendTransport : WiFi station + HTTPClient.
 *      This is the production path.
 *    - LOW  (jumper to GND) -> SerialBackendTransport : one-line JSON frames on
 *      the USB serial monitor, relayed by tools/serial-bridge/bridge.py to the
 *      locally running Spring Boot app. Demo only, never production.
 *
 *  AuthService is RemoteAuthService (backend is the trust anchor). Set
 *  DWARA_REMOTE_AUTH to 0 in config.h for the offline permissive bench build.
 *
 *  BLE behaviour (unchanged from spec §6):
 *    - Advertises as "DWARA-<facilityCode>".
 *    - App writes {"sessionId":"<uuid>","intent":"ENTRY"|"EXIT"|"CLOSE"} to
 *      the command characteristic.
 *    - Firmware replies on the status characteristic with
 *      {"sessionId","result":"OPENED|DENIED|FAULT|CLOSED","reason"}.
 *    - ENTRY/EXIT open the gate (fixed-timer auto-close after 8 s, spec D17).
 *    - CLOSE closes immediately (test/admin path; reports CLOSED).
 * ============================================================================
 */

#include <Arduino.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#include "config.h"
#include "GateTypes.h"
#include "GateActuator.h"
#include "ServoGateActuator.h"
#include "AuthService.h"
#include "BackendTransport.h"
#include "WiFiBackendTransport.h"
#include "SerialBackendTransport.h"
#include "BackendClient.h"
#if DWARA_REMOTE_AUTH
#include "RemoteAuthService.h"
#else
#include "PermissiveAuthService.h"
#endif

// ---------------------------------------------------------------------------
// DwaraBle — GATT peripheral (spec §6; full table: docs/ble-protocol.md)
// ---------------------------------------------------------------------------

class GateCommandCallbacks;

class DwaraBle {
 public:
  void begin(GateCommandCallbacks *callbacks);

  // Publish the outcome to the status characteristic (read + notify).
  void notifyOutcome(const GateOutcome &outcome);

  bool connected() const { return _clientConnected; }

 private:
  class ServerCallbacks final : public BLEServerCallbacks {
   public:
    explicit ServerCallbacks(DwaraBle *owner) : _owner(owner) {}
    void onConnect(BLEServer *) override { _owner->_clientConnected = true; }
    void onDisconnect(BLEServer *server) override {
      _owner->_clientConnected = false;
      server->getAdvertising()->start();  // resume advertising for the next try
    }
   private:
    DwaraBle *_owner;
  };

  friend class GateCommandCallbacks;

  BLEServer *_server = nullptr;
  BLECharacteristic *_commandChar = nullptr;
  BLECharacteristic *_statusChar = nullptr;
  volatile bool _clientConnected = false;
};

class GateCommandCallbacks final : public BLECharacteristicCallbacks {
 public:
  explicit GateCommandCallbacks(DwaraBle *ble) : _ble(ble) {}

  // The app writes: {"sessionId":"<uuid>","intent":"ENTRY"|"EXIT"|"CLOSE"}
  // Plaintext by design in v1 (spec D6): the command is only a request here;
  // RemoteAuthService re-validates it against the backend before opening.
  void onWrite(BLECharacteristic *characteristic) override {
    const String value = characteristic->getValue();
    if (value.length() == 0 || value.length() > COMMAND_PAYLOAD_MAX) {
      enqueue(GateCommand{"", GateIntent::ENTRY, false});
      return;
    }

    JsonDocument doc;
    if (deserializeJson(doc, value) != DeserializationError::Ok) {
      enqueue(GateCommand{"", GateIntent::ENTRY, false});
      return;
    }

    GateCommand cmd{};
    strlcpy(cmd.sessionId, doc["sessionId"] | "", sizeof(cmd.sessionId));
    const char *intent = doc["intent"] | "";
    if (strcmp(intent, "EXIT") == 0) {
      cmd.intent = GateIntent::EXIT;
    } else if (strcmp(intent, "CLOSE") == 0) {
      cmd.intent = GateIntent::CLOSE;
    } else {
      cmd.intent = GateIntent::ENTRY;
    }
    cmd.valid = (strlen(cmd.sessionId) > 0);
    enqueue(cmd);
  }

  static void enqueue(const GateCommand &cmd) {
    _queue[_writeIdx % kQueueSize] = cmd;
    _writeIdx++;
  }

  static bool dequeue(GateCommand &out) {
    if (_readIdx == _writeIdx) {
      return false;
    }
    out = _queue[_readIdx % kQueueSize];
    _readIdx++;
    return true;
  }

 private:
  static constexpr size_t kQueueSize = 4;
  static GateCommand _queue[kQueueSize];
  static volatile size_t _readIdx;
  static volatile size_t _writeIdx;

  DwaraBle *_ble;
};

GateCommand GateCommandCallbacks::_queue[kQueueSize];
volatile size_t GateCommandCallbacks::_readIdx = 0;
volatile size_t GateCommandCallbacks::_writeIdx = 0;

void DwaraBle::begin(GateCommandCallbacks *callbacks) {
  BLEDevice::init(String(DWARA_PREFIX) + FACILITY_CODE);
  _server = BLEDevice::createServer();
  _server->setCallbacks(new ServerCallbacks(this));

  BLEService *service = _server->createService(DWARA_SERVICE_UUID);

  _commandChar = service->createCharacteristic(
      DWARA_COMMAND_CHAR_UUID, BLECharacteristic::PROPERTY_WRITE);
  _commandChar->setCallbacks(callbacks);

  _statusChar = service->createCharacteristic(
      DWARA_STATUS_CHAR_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  _statusChar->addDescriptor(new BLE2902());

  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(DWARA_SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();
  Serial.println("[ble] advertising as " + String(DWARA_PREFIX) + FACILITY_CODE);
}

void DwaraBle::notifyOutcome(const GateOutcome &outcome) {
  JsonDocument doc;
  doc["sessionId"] = outcome.sessionId;
  doc["result"] = gateResultString(outcome.result);
  doc["reason"] = denyReasonString(outcome.reason);

  char body[STATUS_PAYLOAD_MAX];
  const size_t n = serializeJson(doc, body, sizeof(body));
  _statusChar->setValue((uint8_t *)body, n);
  _statusChar->notify();
}

// ---------------------------------------------------------------------------
// GateController — orchestration (BLE command -> auth service -> actuator)
// ---------------------------------------------------------------------------

class GateController {
 public:
  // backend is null in the permissive bench build (DWARA_REMOTE_AUTH=0): the
  // remote-command poller is then simply disabled.
  GateController(AuthService &auth, DwaraBle &ble, GateActuator &actuator,
                 BackendClient *backend)
      : _auth(auth), _ble(ble), _actuator(actuator), _backend(backend) {}

  void begin() {
    _actuator.begin();
    _auth.begin();
    _nextPollAt = millis() + COMMAND_POLL_INTERVAL_MS;
  }

  void tick() {
    handleQueuedCommands();
    handleAutoClose();
    handleRemoteCommands();
    _actuator.tick();
    _auth.tick();
  }

 private:
  // Step 1: any BLE-written command -> authorize via the injected service ->
  // actuate. The CLOSE intent closes immediately instead of opening.
  void handleQueuedCommands() {
    GateCommand cmd;
    while (GateCommandCallbacks::dequeue(cmd)) {
      if (!cmd.valid) {
        report(cmd.sessionId, GateResult::DENIED, DenyReason::MALFORMED_COMMAND);
        continue;
      }

      // CLOSE: no authorization round-trip needed for an immediate close
      // (closing never weakens security).
      if (cmd.intent == GateIntent::CLOSE) {
        handleClose(cmd);
        continue;
      }

      Serial.printf("[gate] authorizing session=%s intent=%s\n", cmd.sessionId,
                    gateIntentString(cmd.intent));

      if (_actuator.isBusy()) {
        // One gate movement at a time; the app can simply retry.
        report(cmd.sessionId, GateResult::DENIED, DenyReason::ACTUATOR_FAULT);
        continue;
      }

      const AuthDecision d = _auth.authorize(cmd.sessionId, cmd.intent);
      if (d.decision != GateResult::OPENED) {
        report(cmd.sessionId, GateResult::DENIED, d.reason);
        continue;
      }

      if (!_actuator.open()) {
        report(cmd.sessionId, GateResult::DENIED, DenyReason::ACTUATOR_FAULT);
        continue;
      }

      OpenSession(cmd);
      _pendingReport = true;  // report OPENED after the actuator settles
      Serial.println("[gate] authorized -> opening");
    }
  }

  // Step 2: fixed-timer auto-close (spec D17) and outcome reporting.
  void handleAutoClose() {
    // Report an OPENED outcome only once the movement is finished, and never
    // claim OPENED if the actuator latched a fault (spec §10 #12).
    if (_pendingReport && !_actuator.isBusy()) {
      _pendingReport = false;
      const bool faulted = _actuator.hasFaulted();
      report(_outcome.sessionId,
             faulted ? GateResult::FAULT : GateResult::OPENED,
             faulted ? DenyReason::ACTUATOR_FAULT : DenyReason::NONE);
      if (faulted) {
        _open = false;
        return;
      }
    }

    if (_open && !_actuator.isBusy()) {
      if (_actuator.hasFaulted()) {
        report(_sessionId, GateResult::FAULT, DenyReason::ACTUATOR_FAULT);
        _open = false;
        return;
      }
      if (millis() - _openedAt >= GATE_OPEN_HOLD_MS) {
        Serial.println("[gate] hold timer elapsed -> close");
        _actuator.close();
        _open = false;
        _closePendingReport = true;  // report CLOSED when the close settles
      }
    }

    if (_closePendingReport && !_open && !_actuator.isBusy()) {
      _closePendingReport = false;
      const bool faulted = _actuator.hasFaulted();
      report(_sessionId,
             faulted ? GateResult::FAULT : GateResult::CLOSED,
             faulted ? DenyReason::ACTUATOR_FAULT : DenyReason::NONE);
    }
  }

  // Step 3: remote-command poller (spec D11). The backend can open the gate
  // for an attendant override / fail-open recovery; the ESP32 polls and the
  // poll is the acknowledgement. Only polls while the gate is idle so a slow
  // request can never interrupt a movement or the auto-close timer, and backs
  // off when the backend is unreachable.
  void handleRemoteCommands() {
    if (_backend == nullptr) {
      return;
    }

    const uint32_t now = millis();
    if ((int32_t)(now - _nextPollAt) < 0) {
      return;  // wraparound-safe "not due yet"
    }

    if (_open || _actuator.isBusy()) {
      _nextPollAt = now + 500;  // try again shortly, never mid-movement
      return;
    }

    bool reachable = false;
    const RemoteCommand command = _backend->pollCommands(&reachable);

    if (command == RemoteCommand::OPEN) {
      Serial.println("[remote] OPEN from backend -> opening");
      GateCommand remote{};
      strlcpy(remote.sessionId, "remote", sizeof(remote.sessionId));
      if (_actuator.open()) {
        OpenSession(remote);
        _pendingReport = true;
      } else {
        report(remote.sessionId, GateResult::FAULT, DenyReason::ACTUATOR_FAULT);
      }
    } else if (command == RemoteCommand::KEEP_CLOSED) {
      Serial.println("[remote] KEEP_CLOSED from backend");
      if (_open || _actuator.isBusy()) {
        _actuator.close();
        _open = false;
      }
    }

    _nextPollAt = millis() +
                  (reachable ? COMMAND_POLL_INTERVAL_MS : BACKEND_RETRY_INTERVAL_MS);
  }

  // Immediate close on the explicit CLOSE intent. Reports CLOSED once the
  // servo has had its settle window, or right away if already closed.
  void handleClose(const GateCommand &cmd) {
    strlcpy(_sessionId, cmd.sessionId, sizeof(_sessionId));

    if (!_open && !_actuator.isBusy()) {
      // Already closed — confirm immediately so the app button resolves.
      report(cmd.sessionId, GateResult::CLOSED, DenyReason::NONE);
      return;
    }

    if (_actuator.close()) {
      _open = false;
      _pendingReport = false;      // supersede any in-flight OPENED report
      _closePendingReport = true;  // report CLOSED after the sweep settles
      Serial.println("[gate] CLOSE -> closing now");
    } else {
      report(cmd.sessionId, GateResult::DENIED, DenyReason::ACTUATOR_FAULT);
    }
  }

  void OpenSession(const GateCommand &cmd) {
    _open = true;
    _openedAt = millis();  // start of the fixed hold window (spec D17)
    _closePendingReport = false;
    strlcpy(_sessionId, cmd.sessionId, sizeof(_sessionId));
    _outcome = GateOutcome{"", GateResult::OPENED, DenyReason::NONE};
    strlcpy(_outcome.sessionId, cmd.sessionId, sizeof(_outcome.sessionId));
  }

  void report(const char *sessionId, GateResult result, DenyReason reason) {
    Serial.printf("[gate] outcome=%s reason=%s\n", gateResultString(result),
                  denyReasonString(reason));
    GateOutcome outcome;
    strlcpy(outcome.sessionId, sessionId, sizeof(outcome.sessionId));
    outcome.result = result;
    outcome.reason = reason;
    _ble.notifyOutcome(outcome);
  }

  AuthService &_auth;
  DwaraBle &_ble;
  GateActuator &_actuator;
  BackendClient *_backend;
  uint32_t _nextPollAt = 0;

  bool _open = false;
  uint32_t _openedAt = 0;
  char _sessionId[40] = "";
  GateOutcome _outcome{};
  bool _pendingReport = false;       // waiting to report an OPENED outcome
  bool _closePendingReport = false;  // waiting to report a CLOSED outcome
};

// ---------------------------------------------------------------------------
// Networking transport selection
// ---------------------------------------------------------------------------

static WiFiBackendTransport wifiTransport;
static SerialBackendTransport serialTransport(Serial);

// Sample the mode jumper once, at boot. HIGH (internal pull-up) means the pin
// is left open -> WiFi. LOW means it is jumpered to GND -> serial bridge.
static BackendTransport *selectTransport() {
  pinMode(NETWORK_MODE_PIN, INPUT_PULLUP);
  delay(10);  // let the pull-up settle before the first sample

  if (digitalRead(NETWORK_MODE_PIN) == LOW) {
    Serial.printf("[net] GPIO%u LOW -> serial bridge transport (demo)\n",
                  (unsigned)NETWORK_MODE_PIN);
    return &serialTransport;
  }

  Serial.printf("[net] GPIO%u HIGH -> WiFi transport (production)\n",
                (unsigned)NETWORK_MODE_PIN);
  return &wifiTransport;
}

// ---------------------------------------------------------------------------
// Global objects + Arduino entry points
// ---------------------------------------------------------------------------

static BackendClient backend;
#if DWARA_REMOTE_AUTH
static RemoteAuthService auth(backend);
static const char *AUTH_MODE_NAME = "remote";
#else
static PermissiveAuthService auth;
static const char *AUTH_MODE_NAME = "permissive";
#endif
static DwaraBle ble;
static ServoGateActuator actuator(ACTUATOR_PIN, OPEN_ANGLE, CLOSED_ANGLE);  // MG90S on D27
static GateController controller(auth, ble, actuator,
#if DWARA_REMOTE_AUTH
                                 &backend
#else
                                 nullptr
#endif
);

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.printf("\n[dwara] boot — facility %s (servo + %s auth)\n",
                FACILITY_CODE, AUTH_MODE_NAME);

  // Pick the request transport before anything can authorize a command. The
  // gate flow above this layer is identical in both modes.
  BackendTransport *transport = selectTransport();
#if DWARA_REMOTE_AUTH
  if (!backend.begin(*transport)) {
    Serial.println("[net] transport unavailable — requests will fail closed");
  }
#else
  (void)transport;
  Serial.println("[net] permissive auth build — backend calls disabled");
#endif

  // Fail-closed boot: actuator first (servo forced to CLOSED), then BLE.
  controller.begin();

  static GateCommandCallbacks commandCallbacks(&ble);
  ble.begin(&commandCallbacks);
}

void loop() {
  controller.tick();
  delay(2);  // yield to the BLE task
}
