/**
 * ============================================================================
 *  DWARA — PermissiveAuthService (offline TEST build)
 * ============================================================================
 *  Authorizes EVERY request without contacting any backend. For bench testing
 *  of the BLE -> servo path while the Dwara backend is still being built.
 *
 *  NOT FOR PRODUCTION: anyone within radio range who can write to the command
 *  characteristic can open the gate. The production RemoteAuthService
 *  (POST /gate/validate, device secret bearer auth) replaces this via a
 *  one-line swap in dwara-gate.ino.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include "AuthService.h"

class PermissiveAuthService final : public AuthService {
 public:
  AuthDecision authorize(const char *sessionId, GateIntent intent) override {
    Serial.printf("[auth] permissive: authorizing session=%s intent=%s\n",
                  sessionId, gateIntentString(intent));
    return AuthDecision{GateResult::OPENED, DenyReason::NONE};
  }
};
