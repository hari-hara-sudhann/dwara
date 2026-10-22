# BLE Protocol (App ↔ ESP32)

The gate is a BLE peripheral. The app discovers it by name, then exchanges JSON
payloads over a custom GATT service. The BLE link carries only a **request**:
the ESP32 validates every ENTRY/EXIT against the backend before moving the
servo.

## Advertisement

| Field | Value |
|---|---|
| Local name | `DWARA-<facilityCode>`, e.g. `DWARA-FAC-0001` |
| Service UUID | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` |
| Scan mode | Active scan, service-UUID filtered (firmware uses `setScanResponse(true)`) |

`<facilityCode>` is flashed into `config.h` (`FACILITY_CODE`) and must match the
`facilityCode` of a facility in the backend, otherwise the app's
`GET /facilities/{code}` returns "Facility does not exist."

## GATT table

| Characteristic | UUID | Properties | Purpose |
|---|---|---|---|
| Command | `beb5483e-36e1-4688-b7f5-ea07361b26a8` | Write | App → gate command |
| Status | `c7d4a2d0-5f3a-4c3e-9b1a-8e2f6d4c5b7a` | Read + Notify | Gate → app outcome |

No pairing/bonding is required in v1 (spec D6). The payload is plaintext; the
security boundary is the backend validation, not the BLE link.

## Command payload (write)

```json
{ "sessionId": "21289f59-cab7-44a6-ac15-8ba1ca488cbb", "intent": "ENTRY" }
```

| Intent | Meaning |
|---|---|
| `ENTRY` | Validate the session for entry and open if allowed |
| `EXIT` | Validate the payment for exit and open if allowed |
| `CLOSE` | Test/admin: close immediately (no backend call) |

Maximum accepted payload: 128 bytes. Invalid JSON, an empty body, or a missing
`sessionId` produces `DENIED/MALFORMED_COMMAND`.

## Status payload (notify + read)

```json
{ "sessionId": "21289f59-cab7-44a6-ac15-8ba1ca488cbb", "result": "OPENED", "reason": "NONE" }
```

| `result` | Meaning |
|---|---|
| `OPENED` | Backend authorized and the servo settled in the open position |
| `DENIED` | Backend refused; see `reason` |
| `FAULT` | Actuator fault / watchdog trip |
| `CLOSED` | Close completed (auto-close, `CLOSE` intent, or `KEEP_CLOSED`) |

`reason` values: `NONE`, `STALE_SESSION`, `WRONG_STATE_FOR_ENTRY`,
`PAYMENT_REQUIRED`, `WRONG_FACILITY`, `UNKNOWN_SESSION`, `BACKEND_UNREACHABLE`,
`BACKEND_ERROR`, `ACTUATOR_FAULT`, `MALFORMED_COMMAND`.

For an attendant override queued with `POST /devices/{id}/commands`, the
firmware reports `sessionId: "remote"`; the app ignores notifications that do
not match the session it is waiting for.

## Timing

| Constant | Value |
|---|---|
| `GATE_OPEN_HOLD_MS` | 8000 ms — fixed auto-close timer (no sensors) |
| `SERVO_SETTLE_MS` | 800 ms — sweep completion before `OPENED` is reported |
| `ACTUATOR_SETTLE_TIMEOUT_MS` | 3000 ms — watchdog; forces closed and latches `FAULT` |
| `COMMAND_POLL_INTERVAL_MS` | 5000 ms — backend remote-command poll |
| `BACKEND_RETRY_INTERVAL_MS` | 2000 ms — backoff after an unreachable backend |

The app allows 15 s for a full round-trip (backend call + settle + notify).

## MTU and reconnects

- The app requests an MTU of 185; both payloads are far smaller.
- The ESP32 resumes advertising on disconnect, so a reconnect only needs a new
  scan/connect — no re-pairing.
- The app keeps one connection per facility code and drops it on a failed
  command so the next attempt re-scans.
