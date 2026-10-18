/**
 * ============================================================================
 *  DWARA — BackendClient (parked: NOT compiled into the test build)
 * ============================================================================
 *  Kept verbatim from the backend-integrated build so the production
 *  RemoteAuthService can be built on top of it later. The current test build
 *  uses PermissiveAuthService and never touches WiFi/HTTP.
 *
 *  Re-integration checklist (when the backend ships):
 *    1. Implement RemoteAuthService : AuthService in a new header, wrapping
 *       BackendClient::validate() and translating its ValidateResponse into
 *       AuthDecision.
 *    2. In dwara-gate.ino swap `PermissiveAuthService auth` for the remote
 *       service and pass it to GateController.
 *    3. Restore the /devices/{id}/commands poller (spec D11) — either inside
 *       the remote auth service's tick() or re-added to GateController.
 *    4. Restore the WiFi include + ensureWifi() call in setup().
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

#include "config.h"
#include "GateTypes.h"

struct ValidateResponse {
  GateResult decision;   // OPENED ("open") or DENIED ("keep_closed")
  DenyReason reason;
  bool parsed;
};

class BackendClient {
 public:
  bool ensureWifi(uint32_t timeoutMs = 10000) {
    if (WiFi.status() == WL_CONNECTED) {
      return true;
    }
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    const uint32_t deadline = millis() + timeoutMs;
    while (WiFi.status() != WL_CONNECTED && millis() < deadline) {
      delay(100);
    }
    return WiFi.status() == WL_CONNECTED;
  }

  // POST /gate/validate  { deviceId, sessionId, intent }
  // 200 { "decision": "open" | "keep_closed", "reason": "..." }
  ValidateResponse validate(const char *sessionId, GateIntent intent) {
    ValidateResponse out{GateResult::DENIED, DenyReason::BACKEND_UNREACHABLE,
                         false};

    if (!ensureWifi(BACKEND_TIMEOUT_MS)) {
      Serial.println("[backend] WiFi unavailable");
      return out;
    }

    HTTPClient http;
    char url[160];
    snprintf(url, sizeof(url), "%s/gate/validate", BACKEND_BASE_URL);
    http.begin(url);
    http.setTimeout(BACKEND_TIMEOUT_MS);
    http.setConnectTimeout(BACKEND_TIMEOUT_MS);
    http.addHeader("Content-Type", "application/json");
    char auth[96];
    snprintf(auth, sizeof(auth), "Bearer %s", DEVICE_SECRET);
    http.addHeader("Authorization", auth);

    JsonDocument req;
    req["deviceId"] = DEVICE_ID;
    req["sessionId"] = sessionId;
    req["intent"] = gateIntentString(intent);

    char body[COMMAND_PAYLOAD_MAX];
    serializeJson(req, body, sizeof(body));

    const int code = http.POST(body);
    if (code <= 0) {
      Serial.printf("[backend] /gate/validate connection error: %d\n", code);
      http.end();
      return out;
    }

    if (code != 200) {
      Serial.printf("[backend] /gate/validate HTTP %d\n", code);
      out.reason = DenyReason::BACKEND_ERROR;
      out.parsed = true;
      http.end();
      return out;
    }

    const String payload = http.getString();
    http.end();

    JsonDocument res;
    if (deserializeJson(res, payload) != DeserializationError::Ok) {
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
    if (WiFi.status() != WL_CONNECTED) {
      return RemoteCommand::NONE;
    }

    HTTPClient http;
    char url[160];
    snprintf(url, sizeof(url), "%s/devices/%s/commands", BACKEND_BASE_URL,
             DEVICE_ID);
    http.begin(url);
    http.setTimeout(BACKEND_TIMEOUT_MS);
    http.setConnectTimeout(BACKEND_TIMEOUT_MS);
    char auth[96];
    snprintf(auth, sizeof(auth), "Bearer %s", DEVICE_SECRET);
    http.addHeader("Authorization", auth);

    const int code = http.GET();
    RemoteCommand cmd = RemoteCommand::NONE;
    if (code == 200) {
      const String payload = http.getString();
      JsonDocument res;
      if (deserializeJson(res, payload) == DeserializationError::Ok) {
        const char *c = res["command"] | "NONE";
        if (strcmp(c, "OPEN") == 0) cmd = RemoteCommand::OPEN;
        else if (strcmp(c, "KEEP_CLOSED") == 0) cmd = RemoteCommand::KEEP_CLOSED;
      }
    }
    http.end();
    return cmd;
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
};
