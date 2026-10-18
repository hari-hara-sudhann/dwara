/**
 * ============================================================================
 *  DWARA — Gate types shared by BLE, backend client, and actuator layers
 * ============================================================================
 *  Spec references: §3.3/§3.5 (flows), §6 (BLE protocol), §5 (/gate/validate),
 *  §10 (edge cases 3, 4, 5, 12).
 * ============================================================================
 */

#pragma once

#include <Arduino.h>

// Direction the app declares on the command characteristic. The app UI makes
// a wrong intent impossible (spec interview, round 6): entry button is only
// offered for a CREATED session, exit button only after payment success.
enum class GateIntent : uint8_t {
  ENTRY = 0,
  EXIT = 1,
  CLOSE = 2,  // test/admin: explicit immediate close (no session semantics)
};

// Raw intent byte carried in the BLE JSON payload.
enum class GateResult : uint8_t {
  OPENED = 0,   // backend said open AND actuator settled
  DENIED = 1,   // backend said keep_closed (or request invalid)
  FAULT = 2,    // actuator did not settle / local failure
  CLOSED = 3,   // CLOSE intent completed (gate back in closed position)
};

// Why a command was denied or failed. Mirrors the backend `reason` string and
// the app-facing status payload.
enum class DenyReason : uint8_t {
  NONE = 0,

  // From the backend /gate/validate response:
  STALE_SESSION = 1,        // ABORTED or never validated in time (D20)
  WRONG_STATE_FOR_ENTRY = 2,  // ENTRY but session already PARKED (§10 #3)
  PAYMENT_REQUIRED = 3,     // EXIT but no SUCCESS payment (§10 #4)
  WRONG_FACILITY = 4,       // session's facility != this device's facility
  UNKNOWN_SESSION = 5,      // sessionId not found
  BACKEND_UNREACHABLE = 6,  // WiFi/HTTP failure (fail closed, spec §7)
  BACKEND_ERROR = 7,        // 5xx or malformed response

  // Local:
  ACTUATOR_FAULT = 8,       // did not settle within ACTUATOR_SETTLE_TIMEOUT_MS
  MALFORMED_COMMAND = 9,    // BLE payload not parseable JSON / bad fields
};

// A parsed gate command from the BLE command characteristic.
struct GateCommand {
  char sessionId[40];  // 36-char UUID + NUL
  GateIntent intent;
  bool valid;
};

// The outcome the firmware reports back to the app (status characteristic).
struct GateOutcome {
  char sessionId[40];
  GateResult result;
  DenyReason reason;
};

// A command fetched from the backend remote-command queue (spec D11).
enum class RemoteCommand : uint8_t {
  NONE = 0,
  OPEN = 1,         // attendant/override/fail-open recovery
  KEEP_CLOSED = 2,  // explicit close/hold
};

// Map DenyReason to the short string used in the BLE status payload and logs.
inline const char *denyReasonString(DenyReason r) {
  switch (r) {
    case DenyReason::NONE: return "NONE";
    case DenyReason::STALE_SESSION: return "STALE_SESSION";
    case DenyReason::WRONG_STATE_FOR_ENTRY: return "WRONG_STATE_FOR_ENTRY";
    case DenyReason::PAYMENT_REQUIRED: return "PAYMENT_REQUIRED";
    case DenyReason::WRONG_FACILITY: return "WRONG_FACILITY";
    case DenyReason::UNKNOWN_SESSION: return "UNKNOWN_SESSION";
    case DenyReason::BACKEND_UNREACHABLE: return "BACKEND_UNREACHABLE";
    case DenyReason::BACKEND_ERROR: return "BACKEND_ERROR";
    case DenyReason::ACTUATOR_FAULT: return "ACTUATOR_FAULT";
    case DenyReason::MALFORMED_COMMAND: return "MALFORMED_COMMAND";
  }
  return "UNKNOWN";
}

// Map backend `decision` strings to a compact form.
inline const char *gateResultString(GateResult r) {
  switch (r) {
    case GateResult::OPENED: return "OPENED";
    case GateResult::DENIED: return "DENIED";
    case GateResult::FAULT: return "FAULT";
    case GateResult::CLOSED: return "CLOSED";
  }
  return "UNKNOWN";
}

// Intent string used in BLE logs / backend payload.
inline const char *gateIntentString(GateIntent i) {
  switch (i) {
    case GateIntent::ENTRY: return "ENTRY";
    case GateIntent::EXIT: return "EXIT";
    case GateIntent::CLOSE: return "CLOSE";
  }
  return "UNKNOWN";
}
