/**
 * ============================================================================
 *  DWARA — BackendClient (backend contract, transport-agnostic)
 * ============================================================================
 *  Builds the two backend requests used by this firmware and maps the
 *  responses into firmware types:
 *
 *    POST /gate/validate              -> open / keep_closed + reason
 *    GET  /devices/{id}/commands      -> remote command for the poller
 *
 *  It does NOT know whether the bytes travel over WiFi or the serial bridge;
 *  the chosen BackendTransport is injected at boot (dwara-gate.ino).
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>

#include "BackendTransport.h"
#include "config.h"
#include "GateTypes.h"

struct ValidateResponse {
  GateResult decision;  // OPENED ("open") or DENIED ("keep_closed")
  DenyReason reason;
  bool parsed;
};

class BackendClient {
 public:
  // Attach the transport selected from NETWORK_MODE_PIN and bring it up.
  bool begin(BackendTransport &transport) {
    _transport = &transport;
    const bool ready = _transport->begin();
    Serial.printf("[backend] transport=%s ready=%s\n", _transport->name(),
                  ready ? "yes" : "no");
    return ready;
  }

  // POST /gate/validate  { deviceId, sessionId, intent }
  // 200 { "decision": "open" | "keep_closed", "reason": "..." }
  ValidateResponse validate(const char *sessionId, GateIntent intent) {
    ValidateResponse out{GateResult::DENIED, DenyReason::BACKEND_UNREACHABLE,
                         false};
    if (_transport == nullptr) {
      return out;
    }

    JsonDocument req;
    req["deviceId"] = DEVICE_ID;
    req["sessionId"] = sessionId;
    req["intent"] = gateIntentString(intent);

    char body[BACKEND_BODY_MAX];
    serializeJson(req, body, sizeof(body));

    char auth[BACKEND_AUTH_MAX];
    snprintf(auth, sizeof(auth), "Bearer %s", DEVICE_SECRET);

    const BackendResponse http =
        _transport->send(BackendRequest{"POST", "/gate/validate", auth, body});
    if (http.status <= 0) {
      Serial.printf("[backend] /gate/validate unreachable (%s)\n",
                    _transport->name());
      return out;
    }
    if (http.status != 200) {
      Serial.printf("[backend] /gate/validate HTTP %d\n", http.status);
      out.reason = DenyReason::BACKEND_ERROR;
      out.parsed = true;
      return out;
    }

    JsonDocument res;
    if (deserializeJson(res, http.body) != DeserializationError::Ok) {
      Serial.println("[backend] /gate/validate: malformed JSON");
      out.reason = DenyReason::BACKEND_ERROR;
      out.parsed = false;
      return out;
    }

    const char *decision = res["decision"] | "keep_closed";
    out.parsed = true;
    if (strcmp(decision, "open") == 0) {
      out.decision = GateResult::OPENED;
      out.reason = DenyReason::NONE;
    } else {
      out.decision = GateResult::DENIED;
      out.reason = reasonFromString(res["reason"] | "BACKEND_ERROR");
    }
    return out;
  }

  // GET /devices/{deviceId}/commands  (spec D11, the only backend-initiated
  // channel in scope). Expected 200:
  // { "command": "NONE" | "OPEN" | "KEEP_CLOSED" }
  RemoteCommand pollCommands() {
    if (_transport == nullptr) {
      return RemoteCommand::NONE;
    }

    char path[96];
    snprintf(path, sizeof(path), "/devices/%s/commands", DEVICE_ID);

    char auth[BACKEND_AUTH_MAX];
    snprintf(auth, sizeof(auth), "Bearer %s", DEVICE_SECRET);

    const BackendResponse http =
        _transport->send(BackendRequest{"GET", path, auth, nullptr});
    if (http.status != 200) {
      return RemoteCommand::NONE;
    }

    JsonDocument res;
    if (deserializeJson(res, http.body) != DeserializationError::Ok) {
      return RemoteCommand::NONE;
    }

    const char *command = res["command"] | "NONE";
    if (strcmp(command, "OPEN") == 0) {
      return RemoteCommand::OPEN;
    }
    if (strcmp(command, "KEEP_CLOSED") == 0) {
      return RemoteCommand::KEEP_CLOSED;
    }
    return RemoteCommand::NONE;
  }

  const char *transportName() const {
    return _transport == nullptr ? "none" : _transport->name();
  }

 private:
  static DenyReason reasonFromString(const char *r) {
    if (strcmp(r, "STALE_SESSION") == 0) return DenyReason::STALE_SESSION;
    if (strcmp(r, "WRONG_STATE_FOR_ENTRY") == 0) return DenyReason::WRONG_STATE_FOR_ENTRY;
    if (strcmp(r, "PAYMENT_REQUIRED") == 0) return DenyReason::PAYMENT_REQUIRED;
    if (strcmp(r, "WRONG_FACILITY") == 0) return DenyReason::WRONG_FACILITY;
    if (strcmp(r, "UNKNOWN_SESSION") == 0) return DenyReason::UNKNOWN_SESSION;
    return DenyReason::BACKEND_ERROR;
  }

  BackendTransport *_transport = nullptr;
};
