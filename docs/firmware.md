# ESP32 Gate Controller Firmware

> Status: **Implemented** · Board: ESP32 20-pin dev board · Framework: Arduino (spec D18) · Spec: [`dwara-architecture-spec.md`](../dwara-architecture-spec.md) §7

The firmware drives the MG90S gate servo, exposes the BLE command/status service, and validates every open request against the backend. The backend link is an **abstract transport** so the same gate logic runs either over WiFi (production) or over the USB serial monitor through a small host-side bridge (demo only).

## Source layout

```
firmware/dwara-gate/
├── dwara-gate.ino            # sketch: boot-time mode selection, BLE, gate state machine
├── config.h                  # identity, pins, WiFi, timeouts, transport framing
├── GateTypes.h               # intents, results, deny reasons, commands
├── BackendTransport.h        # ABSTRACT request/response link
├── WiFiBackendTransport.h    # concrete transport: WiFi station + HTTPClient (production)
├── SerialBackendTransport.h  # concrete transport: framed JSON over USB serial (demo)
├── BackendClient.h           # backend contract, independent of the transport
├── RemoteAuthService.h       # AuthService backed by POST /gate/validate
├── PermissiveAuthService.h   # offline bench alternative (approves everything)
├── AuthService.h             # abstract authorization interface
├── GateActuator.h            # ABSTRACT gate mechanism (spec D15)
├── ServoGateActuator.h/.cpp  # MG90S on D27
└── LedGateActuator.h/.cpp    # legacy LED stand-in, kept for bench use
```

The serial demo also uses [`tools/serial-bridge/bridge.py`](../tools/serial-bridge/bridge.py); its protocol and usage are documented in [`docs/serial-bridge.md`](serial-bridge.md).

Arduino IDE: open the `firmware/dwara-gate` folder as a sketch (the `.ino` gives it its name). PlatformIO: set `src_dir = firmware/dwara-gate` or copy the folder into `src/`.

> **Partition scheme:** select **Huge APP (3 MB No OTA / 1 MB SPIFFS)** (Arduino IDE → Tools → Partition Scheme, or `PartitionScheme=huge_app` with `arduino-cli`). The WiFi + BLE build is ~1.8 MB, which overflows the board's default 1.2 MB application partition.

**Required libraries** (Arduino Library Manager):

| Library | Used for |
|---|---|
| ESP32 board package (`esp32` by Espressif) | core, BLE, WiFi |
| `ArduinoJson` (v7) | BLE payload, backend response, serial-frame parsing |
| `ESP32Servo` | MG90S servo control |

## Networking modes (`NETWORK_MODE_PIN`)

GPIO26 (D26) is configured as `INPUT_PULLUP` at boot and sampled **once**. The internal pull-up keeps it HIGH when nothing is attached; jumper it to GND to select the serial bridge.

| GPIO26 | Transport | Use |
|---|---|---|
| **HIGH** (pin open) | `WiFiBackendTransport` | **Production.** WiFi station + `HTTPClient` to `BACKEND_BASE_URL`. |
| **LOW** (pin grounded) | `SerialBackendTransport` | **Demo only.** Requests are framed on the USB serial monitor and relayed by `tools/serial-bridge` to the same Spring Boot app. |

```cpp
// dwara-gate.ino (setup()):
static BackendTransport *selectTransport() {
  pinMode(NETWORK_MODE_PIN, INPUT_PULLUP);
  delay(10);
  if (digitalRead(NETWORK_MODE_PIN) == LOW) return &serialTransport;
  return &wifiTransport;
}
```

Everything above the transport is shared: `RemoteAuthService` → `BackendClient` → `BackendTransport`. Swapping WiFi for serial changes no gate logic, payload, timeout handling, or fail-closed behavior.

> The pin is read at boot only. Change the jumper and reset the board to switch modes. Do **not** move this input to GPIO34–39 — those pins are input-only and cannot enable the internal pull-up.

## Wiring

| Signal | ESP32 pin | Notes |
|---|---|---|
| Servo signal | GPIO27 (D27) | MG90S signal; power from an **external 5 V supply** with a common ground and decoupling capacitor — do not power the servo from 3V3. |
| Networking mode | GPIO26 (D26) | Leave open for WiFi. Jumper to GND for the serial bridge. |

Servo angles are abstract in `config.h`: `OPEN_ANGLE = 90`, `CLOSED_ANGLE = 0`, with `SERVO_SETTLE_MS = 800` and a `ACTUATOR_SETTLE_TIMEOUT_MS = 3000` stall watchdog. The gate auto-closes `GATE_OPEN_HOLD_MS = 8000` after opening (spec D17).

## Production path (WiFi)

`WiFiBackendTransport` brings up station mode with `WIFI_SSID`/`WIFI_PASSWORD`, then sends:

| Call | Auth | Body / Response |
|---|---|---|
| `POST /gate/validate` | `Authorization: Bearer <deviceSecret>` | `{deviceId, sessionId, intent}` → `200 {decision: "open"\|"keep_closed", reason: "..."}` |
| `GET /devices/{deviceId}/commands` | same | `200 {command: "NONE"\|"OPEN"\|"KEEP_CLOSED"}` |

Every request times out after `BACKEND_TIMEOUT_MS` (4 s). A WiFi or HTTP failure is reported to the app as `DENIED/BACKEND_UNREACHABLE`; a non-200 or malformed body is `DENIED/BACKEND_ERROR`. The gate never opens on a failed validation (fail closed, spec §7).

## Remote-command poller (D11)

The gate loop also polls `GET /devices/{deviceId}/commands` every
`COMMAND_POLL_INTERVAL_MS` (5 s). The poll is the acknowledgement: a queued
`OPEN` or `KEEP_CLOSED` is delivered once and never repeated.

- Polls only while the gate is closed and the actuator is idle, so a slow
  request can never interrupt a movement or the 8 s auto-close timer.
- On an unreachable backend it backs off to `BACKEND_RETRY_INTERVAL_MS` (2 s)
  instead of hammering the server; the gate stays closed.
- `OPEN` opens the gate (attendant override / fail-open recovery) and reports
  the outcome over BLE with `sessionId: "remote"`. `KEEP_CLOSED` closes an open
  gate.
- Enabled only in the remote-auth build; the permissive bench build passes a
  null backend and the poller is compiled out.

## Serial demo path (not production)

With GPIO26 grounded, `SerialBackendTransport` writes one framed line per request and waits for the bridge's answer:

```
DWRAREQ {"id":7,"method":"POST","path":"/gate/validate","authorization":"Bearer ...","body":"{\"deviceId\":\"...\",\"sessionId\":\"...\",\"intent\":\"ENTRY\"}"}
DWARERES {"id":7,"status":200,"body":"{\"decision\":\"open\",\"reason\":\"NONE\"}"}
```

- Frames are newline-terminated and prefixed, so normal `[gate]`/`[auth]` log lines remain readable in the monitor.
- The firmware waits `SERIAL_BRIDGE_TIMEOUT_MS` (6 s) for the matching `id`, then fails closed.
- The host bridge performs the real HTTP call to `--backend` (default `http://localhost:8080`), so the Spring Boot app must be running.
- This path exists purely to demonstrate the backend flow without WiFi. It is **not** a production transport: it depends on a laptop, is unauthenticated at the USB layer, and is slower than WiFi.

Run it with:

```bash
cd tools/serial-bridge
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python bridge.py                       # auto-detects the ESP32 port
# or: python bridge.py --port /dev/tty.usbserial-0001 --backend http://localhost:8080
```

Full details, protocol fields, and troubleshooting: [`docs/serial-bridge.md`](serial-bridge.md).

## Authorization modes

`DWARA_REMOTE_AUTH` in `config.h` selects the authority:

| Value | Service | Behavior |
|---|---|---|
| `1` (default) | `RemoteAuthService` | Backend decides every ENTRY/EXIT; serial or WiFi transport is used. |
| `0` | `PermissiveAuthService` | Bench build: approves every request locally, no backend calls. |

⚠️ `PermissiveAuthService` is not for production — anyone in BLE range can open the gate. The backend is the only trust anchor in the real system (spec D6).

## Firmware behavior summary

- **Boot:** sample GPIO26 → select transport → bring it up (when remote auth is enabled) → `actuator.begin()` forces the servo CLOSED → BLE advertising as `DWARA-<facilityCode>`.
- **BLE write** `{"sessionId":"<uuid>","intent":"ENTRY"|"EXIT"|"CLOSE"}` is queued:
  - `ENTRY`/`EXIT` → `AuthService.authorize()` → `actuator.open()` on approval; status `OPENED` after the servo settles, otherwise `DENIED` with the backend reason.
  - `CLOSE` → closes immediately (no authorization round-trip); status `CLOSED`.
  - Malformed payload → `DENIED/MALFORMED_COMMAND`.
- **Auto-close:** fixed timer (`GATE_OPEN_HOLD_MS`, 8 s) after opening, then `CLOSED` once the servo settles.
- **One movement at a time:** a command arriving while the actuator is busy is denied with `ACTUATOR_FAULT`; the app can retry.
- **Watchdog:** if a sweep exceeds `ACTUATOR_SETTLE_TIMEOUT_MS`, the servo is driven back to `CLOSED`, the PWM signal is released (no standing current in a jammed servo), `FAULT` is latched and `DENIED/ACTUATOR_FAULT` reported. The gate then stays closed until reset.
- **Remote commands:** the backend can queue `OPEN` / `KEEP_CLOSED`; the poller executes them only while the gate is idle.

## Config reference (`config.h`)

| Constant | Default | Purpose |
|---|---|---|
| `NETWORK_MODE_PIN` | `26` | Mode jumper, `INPUT_PULLUP` |
| `DWARA_REMOTE_AUTH` | `1` | Remote vs permissive authorization |
| `BACKEND_BASE_URL` | `http://192.168.1.100:8080` | WiFi transport target (no trailing slash) |
| `BACKEND_TIMEOUT_MS` | `4000` | HTTP request timeout |
| `WIFI_CONNECT_TIMEOUT_MS` | `10000` | WiFi association wait |
| `SERIAL_BRIDGE_TIMEOUT_MS` | `6000` | Serial demo frame wait |
| `COMMAND_POLL_INTERVAL_MS` | `5000` | Remote-command poll period |
| `BACKEND_RETRY_INTERVAL_MS` | `2000` | Backoff after an unreachable backend |
| `ACTUATOR_PIN` | `27` | Servo signal |
| `SERVO_SETTLE_MS` | `800` | Sweep completion window before reporting `OPENED` |
| `ACTUATOR_SETTLE_TIMEOUT_MS` | `3000` | Stall watchdog: force closed, release PWM, latch `FAULT` |
| `DWARA_FORCE_ACTUATOR_FAULT` | `0` | Bench-only: `1` makes the watchdog fire on purpose |
| `GATE_OPEN_HOLD_MS` | `8000` | Auto-close delay |

## Legacy: LED actuator

`LedGateActuator` remains available as a stand-in when the servo cannot be powered: `open()` blinks 3 times, `close()` blinks 2 times, both non-blocking and driven from `tick()`. Swap the one instantiation line in `dwara-gate.ino`:

```cpp
static LedGateActuator actuator(ACTUATOR_PIN);
// static ServoGateActuator actuator(ACTUATOR_PIN, OPEN_ANGLE, CLOSED_ANGLE);
```

The LED was the original workaround for MG90S supply sag; the servo build is current. Keep the external-supply wiring notes above.

## Known limitations

- Secrets in `config.h` are placeholders — fill `WIFI_*`, `DEVICE_ID`, `DEVICE_SECRET`, `BACKEND_BASE_URL`, and `FACILITY_CODE` per device before flashing (spec D7).
- Plaintext BLE by design in v1 (spec D6); HTTP (not HTTPS) for bring-up — switch `BACKEND_BASE_URL` to TLS before real deployment.
- The serial bridge is a demonstration aid and will not ship in production.
- There is no servo position feedback, so a physically stalled arm is detected only by the bounded settle window, not by measuring force or position.
- `dwara-gate.ino` compiles only for ESP32 targets (BLE + WiFi are board-specific).

## Compile check

```bash
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app firmware/dwara-gate
```
