# Serial Bridge (ESP32 ↔ Spring Boot)

> **Demonstration only.** WiFi is the primary and production transport for the ESP32 gate controller. This host-side bridge exists so the backend flow can be demonstrated without a WiFi network and will not ship to production. Firmware overview: [`docs/firmware.md`](firmware.md).

## What it does

Grounding **GPIO26 (D26)** on the ESP32 selects the serial transport at boot. The firmware then writes each backend request as one framed line on the USB serial monitor. `tools/serial-bridge/bridge.py` reads those frames, performs the real HTTP call against the locally running Spring Boot app, and writes the response back.

```
ESP32 (GPIO26 → GND)                 host (this repo)                Spring Boot
┌──────────────────────┐   USB   ┌──────────────────────┐   HTTP   ┌───────────────┐
│ RemoteAuthService    │         │ tools/serial-bridge  │ ───────> │ /gate/validate│
│ BackendClient        │ ──────> │ bridge.py            │ <─────── │ /devices/...  │
│ SerialBackendTransport│ <────── │                      │          │               │
└──────────────────────┘         └──────────────────────┘          └───────────────┘
```

The gate logic, payloads, auth header, and fail-closed behavior are identical to the WiFi path; only the byte transport differs.

## Wire protocol

Both directions are UTF-8, one frame per line (`\n`), with a prefix that is distinct from ordinary firmware log lines. The bridge ignores everything that is not a `DWRAREQ` frame, so `[gate]`/`[auth]` logs remain readable in the monitor.

### Request (ESP32 → host)

```
DWRAREQ {"id":7,"method":"POST","path":"/gate/validate","authorization":"Bearer <deviceSecret>","body":"{\"deviceId\":\"...\",\"sessionId\":\"...\",\"intent\":\"ENTRY\"}"}
```

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Monotonic request id; the response must echo it. |
| `method` | yes | `GET` or `POST`. |
| `path` | yes | Path appended to the backend base URL (must start with `/`). |
| `authorization` | no | Full `Authorization` header value. |
| `body` | no | JSON request body, encoded as a **string** inside the frame. Sent with `Content-Type: application/json`. |

### Response (host → ESP32)

```
DWARERES {"id":7,"status":200,"body":"{\"decision\":\"open\",\"reason\":\"NONE\"}"}
```

| Field | Meaning |
|---|---|
| `id` | Echo of the request id; stale/mismatched frames are ignored by the firmware. |
| `status` | HTTP status code, or `0` when the backend could not be reached. |
| `body` | Raw response body (empty on transport failure). |

The firmware waits `SERIAL_BRIDGE_TIMEOUT_MS` (6 s) for the matching response and then denies the gate command with `BACKEND_UNREACHABLE`.

## Setup

Requirements: Python 3.9+, the ESP32 connected over USB, and the Spring Boot app running locally.

```bash
cd tools/serial-bridge
python3 -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python bridge.py
```

`bridge.py` auto-detects Espressif/CP210x/CH340-style ports. Otherwise pass one explicitly:

```bash
python bridge.py --port /dev/tty.usbserial-0001 --backend http://localhost:8080
```

| Flag | Default | Purpose |
|---|---|---|
| `--port` | auto-detect / `$DWARA_SERIAL_PORT` | Serial device of the ESP32. |
| `--baud` | `115200` | Must match `Serial.begin()` in the sketch. |
| `--backend` | `http://localhost:8080` / `$DWARA_BACKEND_URL` | Spring Boot base URL. |
| `--timeout` | `3.0` | HTTP timeout in seconds. |
| `--verbose` | off | Print full response bodies instead of truncating. |

Start the bridge **before** issuing a BLE command; a frame sent while the bridge is down waits 6 s and then fails closed. Opening the serial port can reset some ESP32 boards — if that happens, wait for it to reboot and start the BLE connection again.

## Expected backend calls

The bridge forwards exactly what the firmware would have sent over WiFi:

| Call | Auth | Body / Response |
|---|---|---|
| `POST /gate/validate` | `Authorization: Bearer <deviceSecret>` | `{deviceId, sessionId, intent}` → `200 {decision: "open"\|"keep_closed", reason: "..."}` |
| `GET /devices/{deviceId}/commands` | same | `200 {command: "NONE"\|"OPEN"\|"KEEP_CLOSED"}` |

A `404` in the bridge log means the Spring Boot app does not expose that endpoint yet; the firmware treats it as `BACKEND_ERROR` and keeps the gate closed.

## Troubleshooting

| Symptom | Check |
|---|---|
| `could not pick a serial port automatically` | Pass `--port`; the help output lists detected ports. |
| `cannot open … Permission denied` | Add your user to the serial group (Linux) or close other serial monitors (Arduino IDE, `screen`, PlatformIO). |
| `bridge timed out …` on the ESP32 | Bridge not running, wrong port/baud, or the backend call exceeded 6 s. |
| Every request is `status 0` | Spring Boot is not running or `--backend` points at the wrong host/port. |
| Response is `404` | The REST endpoint is not implemented in the backend yet. |
| No frames at all | GPIO26 is not grounded at boot, or the board was reset after the jumper changed; check the boot log for `GPIO26 LOW`. |

## Security notes

- The bridge is a **local bench tool**: it accepts frames from whatever is on the serial port and injects the device bearer header supplied by the firmware.
- The USB link is not authenticated or encrypted; do not use this mode outside a trusted host.
- The `deviceSecret` is visible in the firmware frame and in the bridge's outbound request. Keep `config.h` and the serial console private.
- Serial mode must stay a demonstration path; production uses the WiFi transport with TLS.
