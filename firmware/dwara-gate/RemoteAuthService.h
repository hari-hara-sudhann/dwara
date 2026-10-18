/**
 * ============================================================================
 *  DWARA — RemoteAuthService (production authority)
 * ============================================================================
 *  Thin AuthService wrapper around BackendClient::validate(). The transport
 *  (WiFi or serial bridge) is chosen at boot, so this service works unchanged
 *  in both networking modes.
 *
 *  Fail-closed: any transport failure or malformed response becomes
 *  DENIED/BACKEND_UNREACHABLE or DENIED/BACKEND_ERROR, and the gate stays shut.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>

#include "AuthService.h"
#include "BackendClient.h"

class RemoteAuthService final : public AuthService {
 public:
  explicit RemoteAuthService(BackendClient &client) : _client(client) {}

  AuthDecision authorize(const char *sessionId, GateIntent intent) override {
    const ValidateResponse response = _client.validate(sessionId, intent);
    if (!response.parsed) {
      Serial.printf("[auth] backend did not answer via %s\n",
                    _client.transportName());
    }
    return AuthDecision{response.decision, response.reason};
  }

 private:
  BackendClient &_client;
};
