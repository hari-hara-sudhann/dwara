/**
 * ============================================================================
 *  DWARA — ESP32 Gate Controller (TEST build: servo on D27, permissive auth)
 * ============================================================================
 *  Spec: dwara-architecture-spec.md §3.3/§3.5 (flows), §6 (BLE), §7 (firmware),
 *  D1/D5/D6/D7/D15/D17.
 *
 *  Hardware: ESP32 (20-pin dev board), MG90S servo signal on D27 (GPIO27).
 *
 *  TEST build configuration (backend not wired yet):
 *    - AuthService       : PermissiveAuthService — authorizes EVERY request,
 *                          no WiFi/backend involved. Swap to
 *                          RemoteAuthService when the backend ships
 *                          (BackendClient.h holds the parked HTTP client and
 *                          the re-integration checklist).
 *    - GateActuator      : ServoGateActuator — MG90S, OPEN/CLOSED angles.
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
#include "PermissiveAuthService.h"

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
  // Plaintext by design in v1 (spec D6): authorization is a pluggable
  // service; the PermissiveAuthService accepts everything (TEST build).
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
  GateController(AuthService &auth, DwaraBle &ble, GateActuator &actuator)
      : _auth(auth), _ble(ble), _actuator(actuator) {}

  void begin() {
    _actuator.begin();
    _auth.begin();
  }

  void tick() {
    handleQueuedCommands();
    handleAutoClose();
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
    if (_pendingReport && _actuator.hasSettled()) {
      _pendingReport = false;
      report(_outcome.sessionId, _outcome.result, _outcome.reason);
    }

    if (_open && !_actuator.isBusy()) {
      if (_actuator.hasFaulted()) {
        report(_sessionId, GateResult::FAULT, DenyReason::ACTUATOR_FAULT);
        _open = false;
        _actuator.close();  // best effort back to closed
        return;
      }
      if (millis() - _openedAt >= GATE_OPEN_HOLD_MS) {
        Serial.println("[gate] hold timer elapsed -> close");
        _actuator.close();
        _open = false;
        _closePendingReport = true;  // report CLOSED when the close settles
      }
    }

    if (_closePendingReport && !_open && _actuator.hasSettled()) {
      _closePendingReport = false;
      report(_sessionId, GateResult::CLOSED, DenyReason::NONE);
    }
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

  bool _open = false;
  uint32_t _openedAt = 0;
  char _sessionId[40] = "";
  GateOutcome _outcome{};
  bool _pendingReport = false;       // waiting to report an OPENED outcome
  bool _closePendingReport = false;  // waiting to report a CLOSED outcome
};

// ---------------------------------------------------------------------------
// Global objects + Arduino entry points
// ---------------------------------------------------------------------------

static PermissiveAuthService auth;  // TEST build; swap for RemoteAuthService when backend ships
static DwaraBle ble;
static ServoGateActuator actuator(ACTUATOR_PIN, OPEN_ANGLE, CLOSED_ANGLE);  // MG90S on D27
static GateController controller(auth, ble, actuator);

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println(String("\n[dwara] boot — facility ") + FACILITY_CODE +
                 " (TEST build: servo + permissive auth)");

  // Fail-closed boot: actuator first (servo forced to CLOSED), then BLE.
  controller.begin();

  static GateCommandCallbacks commandCallbacks(&ble);
  ble.begin(&commandCallbacks);
}

void loop() {
  controller.tick();
  delay(2);  // yield to the BLE task
}
