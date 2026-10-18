/**
 * ============================================================================
 *  DWARA — Gate Controller Firmware Configuration
 * ============================================================================
 *  Flash-time configuration (spec decision D7): facilityCode, deviceId and
 *  deviceSecret are BAKED IN at build/flash time. There is no runtime
 *  onboarding in v1.
 *
 *  Fill these in per physical device before flashing.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>

// ---------------------------------------------------------------------------
// Device identity (spec D7 — flash-time config)
// ---------------------------------------------------------------------------

// The parking facility this gate belongs to. Also broadcast in the BLE
// advertisement name as "DWARA-<facilityCode>" (spec D1, discovery via BLE
// beacon).
static const char *FACILITY_CODE = "FAC-0001";

// Registered device id (backend POST /devices, static admin key). Must match
// the row in the gate_controller table for /gate/validate and
// /devices/{id}/commands.
static const char *DEVICE_ID = "00000000-0000-0000-0000-000000000000";

// Per-device secret (spec D6/D11). Hashed server-side; the plaintext lives
// ONLY here. Sent as "Authorization: Bearer <deviceSecret>" on backend calls.
static const char *DEVICE_SECRET = "replace-me-device-secret";

// ---------------------------------------------------------------------------
// WiFi (station mode, spec D5/D11)
// ---------------------------------------------------------------------------

static const char *WIFI_SSID = "replace-me-ssid";
static const char *WIFI_PASSWORD = "replace-me-password";

// Backend base URL, no trailing slash. Use http:// during bring-up; switch to
// https:// with a verified CA before any real deployment.
static const char *BACKEND_BASE_URL = "http://192.168.1.100:8080";

// ---------------------------------------------------------------------------
// BLE (peripheral, spec D1/D6; full GATT table in docs/ble-protocol.md)
// ---------------------------------------------------------------------------

// Advertisement local name prefix used by the app for discovery. The full
// advertised name is DWARA_PREFIX + FACILITY_CODE.
static const char *DWARA_PREFIX = "DWARA-";

// Custom GATT service (spec §6). One well-known service is fine for v1.
#define DWARA_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"

// Write characteristic: the app writes the gate command here
// (sessionId + intent — see GateCommand in GateTypes.h).
#define DWARA_COMMAND_CHAR_UUID   "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// Read/notify characteristic: firmware reports the outcome
// (sessionId + result + reason) for the app UI.
#define DWARA_STATUS_CHAR_UUID    "c7d4a2d0-5f3a-4c3e-9b1a-8e2f6d4c5b7a"

// Maximum JSON payload we accept on the command characteristic. Fits
// {"sessionId":"<36>","intent":"ENTRY"} comfortably within one MTU.
static const size_t COMMAND_PAYLOAD_MAX = 128;
static const size_t STATUS_PAYLOAD_MAX = 160;

// ---------------------------------------------------------------------------
// Actuator (spec D15 — abstract angle spec; wiring in docs/hardware.md)
// ---------------------------------------------------------------------------

// GPIO driving the actuator signal line. MG90S servo signal on D27 (GPIO27).
static const uint8_t ACTUATOR_PIN = 27;  // D27 on common 20-pin boards

// Abstract gate angles (spec D15 — mechanics are NOT a firmware concern).
// Servo build: degrees, pulse timing handled by the servo driver.
// LED build: ignored — LedGateActuator defines its own blink semantics
// (3 blinks = OPEN, 2 blinks = CLOSE; docs/firmware.md).
static const uint8_t OPEN_ANGLE = 90;    // degrees — gate OPEN position
static const uint8_t CLOSED_ANGLE = 0;   // degrees — gate CLOSED position

// ---------------------------------------------------------------------------
// Servo build (MG90S on ACTUATOR_PIN)
// ---------------------------------------------------------------------------

// Time to let the sweep finish before the gate is reported as settled/opened.
// MG90S does ~0.1 s / 60 deg at 4.8 V; a full 90 deg swing fits comfortably.
static const uint32_t SERVO_SETTLE_MS = 800;

// ---------------------------------------------------------------------------
// Gate timing (spec D17 — fixed-timer close, no sensors)
// ---------------------------------------------------------------------------

// How long the gate stays OPEN before auto-closing. No sensors exist (spec
// D8/D15); this timer is the only close mechanism.
static const uint32_t GATE_OPEN_HOLD_MS = 8000;  // 8 s

// Safety bound for the actuator to reach its commanded position. If it has
// not settled by then, treat it as a fault (spec §10, edge case 12).
static const uint32_t ACTUATOR_SETTLE_TIMEOUT_MS = 3000;

// ---------------------------------------------------------------------------
// Backend client (spec §3.3/§3.5 — ESP32 is the validation authority)
// ---------------------------------------------------------------------------

// Timeout for POST /gate/validate and GET /devices/{id}/commands.
static const uint32_t BACKEND_TIMEOUT_MS = 4000;

// Period between remote-command polls (spec D11 — the only backend-initiated
// channel in scope).
static const uint32_t COMMAND_POLL_INTERVAL_MS = 5000;  // 5 s

// Retry cadence when the backend is unreachable (BLE replies DENIED /
// BACKEND_UNREACHABLE meanwhile — fail closed at the gate, spec §7).
static const uint32_t BACKEND_RETRY_INTERVAL_MS = 2000;

// ---------------------------------------------------------------------------
// LED activity indicator (temporary workaround build — docs/firmware.md)
// ---------------------------------------------------------------------------

// Blink cadence for the temporary LedGateActuator: 3 blinks = OPEN command
// accepted, 2 blinks = CLOSE command accepted (docs/firmware.md).
static const uint32_t LED_BLINK_ON_MS = 250;
static const uint32_t LED_BLINK_OFF_MS = 250;
static const uint32_t LED_SETTLE_GAP_MS = 500;
