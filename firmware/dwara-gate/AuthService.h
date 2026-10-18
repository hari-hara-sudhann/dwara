/**
 * ============================================================================
 *  DWARA — Abstract gate authorization service (dependency inversion)
 * ============================================================================
 *  The gate controller must never talk to the backend directly: validation is
 *  an injectable service so the same firmware image can run
 *
 *    - PermissiveAuthService  — offline test build, authorizes every request
 *    - RemoteAuthService      — production build, POST /gate/validate
 *                               (implemented when the backend ships)
 *
 *  The controller only sees AuthDecision; which authority produced it is a
 *  construction-time detail.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include "GateTypes.h"

// Verdict produced by an authorization service. Shape mirrors the backend
// /gate/validate response (decision + reason) so the remote implementation is
// a thin translation layer.
struct AuthDecision {
  GateResult decision;   // OPENED or DENIED
  DenyReason reason;     // NONE when OPENED
};

class AuthService {
 public:
  // Decide whether the given session may perform the given intent. MUST be
  // non-blocking beyond BACKEND_TIMEOUT_MS in remote implementations; the BLE
  // command loop and actuator tick share this thread.
  virtual AuthDecision authorize(const char *sessionId, GateIntent intent) = 0;

  // One-time init (radio bring-up, credential checks). Safe no-op for offline
  // implementations. Called once from setup().
  virtual void begin() {}

  // Periodic maintenance (reconnects, retries). Called from loop(); offline
  // implementations can leave it empty.
  virtual void tick() {}

  virtual ~AuthService() = default;
};
