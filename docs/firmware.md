# ESP32 Gate Controller Firmware

> Status: **Implemented (temporary LED actuator build)** · Board: ESP32 20-pin dev board · Framework: Arduino (spec D18) · Spec: [`dwara-architecture-spec.md`](../dwara-architecture-spec.md) §7

## Source layout

```
firmware/dwara-gate/
├── dwara-gate.ino       # main sketch: wiring of BLE + backend + gate state machine
├── config.h             # flash-time config: identity, WiFi, BLE UUIDs, pins, timings (spec D7)
├── GateTypes.h          # shared enums/structs: intent, result, deny reasons, commands
├── GateActuator.h       # ABSTRACT gate mechanism interface (spec D15)
├── LedGateActuator.h    # concrete impl (temporary): LED blink stand-in for the servo
└── LedGateActuator.cpp
```

Arduino IDE: open the `firmware/dwara-gate` folder as a sketch (the `.ino` gives it its name). PlatformIO: set `src_dir = firmware/dwara-gate` or copy the folder into `src/`.

**Required libraries** (Arduino Library Manager):

| Library | Used for |
|---|---|
| ESP32 board package (`esp32` by Espressif) | core, BLE, WiFi |
| `ArduinoJson` (v7) | BLE payload + backend response parsing |
| `BLE2902` / BLE built-ins | come with the ESP32 board package |

## Abstract actuator (spec D15)

The gate mechanism is deliberately abstract — "the gate opens / the gate closes" — and everything else (BLE, backend client, state machine) only talks to the interface in `GateActuator.h`:

```cpp
class GateActuator {
 public:
  virtual void begin() = 0;          // init pins, force CLOSED (fail-closed boot)
  virtual bool open()  = 0;          // non-blocking, false if busy
  virtual bool close() = 0;          // non-blocking, false if busy
  virtual void tick()  = 0;          // drive timing from loop()
  virtual bool isBusy() const = 0;
  virtual bool hasSettled() const = 0;
  virtual bool hasFaulted() const = 0;   // stall watchdog (spec §10 #12)
};
```

Swapping mechanisms = writing one new class + changing one line in `dwara-gate.ino`. No BLE/backend code changes.

## TEST build (current): servo on D27 + permissive auth

> **Status change.** The current build drives the **MG90S servo on D27 (GPIO27)** directly and **does not contact the backend**: authorization is delegated to `PermissiveAuthService` (`AuthService.h` defines the abstract interface), which approves every request. This exists purely to bench-test the phone → BLE → servo loop while the backend is being built.

```cpp
// dwara-gate.ino (current):
static PermissiveAuthService auth;                                          // TEST: authorizes everything
static ServoGateActuator actuator(ACTUATOR_PIN, OPEN_ANGLE, CLOSED_ANGLE);  // MG90S on D27
// Production swap (backend ready):
// static RemoteAuthService auth;   // wraps BackendClient (parked in BackendClient.h)
```

- **CLOSE intent:** the app can now also write `{"sessionId":"<uuid>","intent":"CLOSE"}` — the gate closes immediately and reports `result: "CLOSED"`. Used by the in-app Gate Test screen (Profile → Gate Test).
- **No WiFi/HTTP** is compiled into the test boot path; `BackendClient.h` holds the parked HTTP client plus the re-integration checklist.
- ⚠️ **Not for production:** anyone in BLE range can open the gate. Swap to `RemoteAuthService` (POST `/gate/validate` with the device bearer secret) before any deployment.
- Dependencies: **ESP32Servo** library (Arduino Library Manager).

## Historical: temporary LED workaround (servo power issue)

> **Context.** The MG90S servo previously had a **power-supply problem** (voltage sag/brownout when the motor starts under load; the board resets), so the gate action was stood in for by an LED. The servo is now wired on D27 (see TEST build above); keep this section for the wiring notes.

**Semantics of the temporary build:**

| Command | LED behavior on **D18** (GPIO18) |
|---|---|
| `openGate` | LED blinks **3 times** |
| `closeGate` | LED blinks **2 times** |

Implementation: `LedGateActuator` (`LedGateActuator.h` / `LedGateActuator.cpp`), selected by a one-line change in `dwara-gate.ino`:

```cpp
static LedGateActuator actuator(ACTUATOR_PIN);   // TEMPORARY LED build
// Production swap (servo power fixed):
// static ServoGateActuator actuator(ACTUATOR_PIN, OPEN_ANGLE, CLOSED_ANGLE);
```

Behavioral details:

- Blink timing is fully **non-blocking** (`tick()`-driven): BLE and backend polling keep running during a sequence (`LED_BLINK_ON_MS` / `LED_BLINK_OFF_MS` / `LED_SETTLE_GAP_MS` in `config.h`).
- A `close()` arriving **during** an open sequence (the fixed-timer auto-close, spec D17) is deferred and runs immediately after the open blinks finish.
- `hasSettled()` becomes true when the pattern completes; the LED is dark in both end states. `hasFaulted()` never fires on this build (an LED cannot stall) but the contract is kept so the watchdog path stays identical for the servo build.
- This build changes **nothing** in the flows of the spec (§3.3, §3.5): session validation, `open`/`keep_closed` decisions, auto-close timer, and remote commands behave exactly as specified.

**Revert path (when servo power is fixed):**

1. Implement `ServoGateActuator` against `GateActuator` (non-blocking sweep toward `OPEN_ANGLE`/`CLOSED_ANGLE`, settle watchdog using `ACTUATOR_SETTLE_TIMEOUT_MS`, PWM via ESP32Servo or LEDC).
2. Swap the one instantiation line in `dwara-gate.ino`.
3. Wire the MG90S per `docs/hardware.md` (external 5 V supply, common ground, decoupling capacitor — **do not** power the servo from the 3V3 rail).
4. Delete this section and mark `docs/hardware.md` as active; keep the decision-log entry (R7 below) as history.

## Firmware behavior summary (TEST build)

- **Boot:** actuator `begin()` first (servo forced to CLOSED, fail-closed), then BLE advertising as `DWARA-<facilityCode>` (spec D1). No WiFi/backend in the test build.
- **BLE write** `{"sessionId":"<uuid>","intent":"ENTRY"|"EXIT"|"CLOSE"}` → queued → `AuthService.authorize()` (permissive: always open) → verdict:
  - `ENTRY`/`EXIT` → `actuator.open()` (servo to 90°), notify `OPENED` after the settle window.
  - `CLOSE` → `actuator.close()` immediately (servo to 0°), notify `CLOSED`.
  - Malformed payload → `DENIED/MALFORMED_COMMAND`.
- **Auto-close:** `GATE_OPEN_HOLD_MS` (8 s default) after a successful open, `actuator.close()` runs and reports `CLOSED`.
- **Remote commands:** parked with `BackendClient.h` until the backend ships.

## Backend contract expected by this firmware

The sketch will run end-to-end once the backend implements (design in spec §5, `docs/api-reference.md`):

| Call | Auth | Body / Response |
|---|---|---|
| `POST /gate/validate` | `Authorization: Bearer <deviceSecret>` | `{deviceId, sessionId, intent}` → `200 {decision: "open"\|"keep_closed", reason: "..."}` |
| `GET /devices/{deviceId}/commands` | same | `200 {command: "NONE"\|"OPEN"\|"KEEP_CLOSED"}` |

Until then, the TEST build runs fully offline via `PermissiveAuthService` — no backend calls are made.
  - `open` → `actuator.open()` (3 blinks), notify `OPENED` on the status characteristic after settle.
  - `keep_closed` → notify `DENIED` + reason (`STALE_SESSION`, `PAYMENT_REQUIRED`, `WRONG_FACILITY`, `WRONG_STATE_FOR_ENTRY`, `UNKNOWN_SESSION`, …).
  - Backend unreachable → notify `DENIED/BACKEND_UNREACHABLE` (fail closed at the gate; post-payment recovery is the remote-command path, spec D11/D16).
- **Auto-close:** `GATE_OPEN_HOLD_MS` (8 s default) after a successful open, `actuator.close()` runs (2 blinks).
- **Remote commands:** poll `GET /devices/{id}/commands` every `COMMAND_POLL_INTERVAL_MS`; `OPEN` opens without session context, `KEEP_CLOSED` force-closes. Never interrupts an in-flight user command.
- **Malformed BLE payload** → `DENIED/MALFORMED_COMMAND`; one gate movement at a time.

## Backend contract expected by this firmware

The sketch will run end-to-end once the backend implements (design in spec §5, `docs/api-reference.md`):

| Call | Auth | Body / Response |
|---|---|---|
| `POST /gate/validate` | `Authorization: Bearer <deviceSecret>` | `{deviceId, sessionId, intent}` → `200 {decision: "open"\|"keep_closed", reason: "..."}` |
| `GET /devices/{deviceId}/commands` | same | `200 {command: "NONE"\|"OPEN"\|"KEEP_CLOSED"}` |

Until then, `POST /gate/validate` returns `keep_closed`/non-200 and the LED stays dark — expected.

## Wiring (temporary LED build)

- LED anode → GPIO18 (D18) through a ~220 Ω series resistor; cathode → GND.
- No external supply needed. (Servo build: see `docs/hardware.md`.)

## Known limitations

- Flash-time secrets in `config.h` are placeholders — fill `WIFI_*`, `DEVICE_ID`, `DEVICE_SECRET`, `BACKEND_BASE_URL`, `FACILITY_CODE` per device before flashing (spec D7).
- Plaintext BLE by design in v1 (spec D6); HTTP (not HTTPS) for bring-up — switch `BACKEND_BASE_URL` to TLS before real deployment.
- `dwara-gate.ino` compiles only for ESP32 targets (BLE + WiFi are board-specific).
