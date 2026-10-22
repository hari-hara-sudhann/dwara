# Dwara Documentation

Dwara is a phone-controlled, per-second-billed parking gate. The phone discovers
a gate over BLE, the ESP32 asks the backend whether a session may pass, and the
backend is the single authority for entry, billing and exit.

```
 Expo app  ──REST (JWT)──▶  Spring Boot backend  ◀──REST (device secret)──  ESP32 gate
    │                              │                                          │
    └──────── BLE (JSON) ──────────┴────────────── servo + fixed timer ────────┘
```

## Contents

| Document | What it covers |
|---|---|
| [api-reference.md](api-reference.md) | Every endpoint, payloads, auth schemes, gate decision table |
| [mobile-app.md](mobile-app.md) | Expo setup, API URL, screens, BLE flow, iOS caveats |
| [ble-protocol.md](ble-protocol.md) | GATT UUIDs, advertisement, command/status payloads, timing |
| [hardware.md](hardware.md) | BOM, wiring, external 5 V supply, fault path, bench checklist |
| [firmware.md](firmware.md) | Firmware layout, transports, remote poller, config reference |
| [serial-bridge.md](serial-bridge.md) | Demo-only serial transport and its host bridge |
| [testing.md](testing.md) | `./scripts/docker-test.sh`, reports, exit codes |

## Quick start

```bash
cp .env.example .env          # if you do not have one; then set DB_*/JWT_*
docker compose up -d --build  # Postgres + backend on APP_PORT (default 8080)
./scripts/docker-test.sh      # image build + mvn verify + JaCoCo reports
```

The backend seeds an `ACTIVE` demo facility `FAC-0001` unless
`DWARA_SEED_DEMO_FACILITY=false`.

Register the physical gate once (admin key from `DWARA_ADMIN_KEY`):

```bash
curl -s -X POST http://localhost:8080/devices \
  -H "X-Admin-Key: $DWARA_ADMIN_KEY" -H 'Content-Type: application/json' \
  -d '{"facilityCode":"FAC-0001","gateType":"ENTRY","firmwareVersion":"1.0.0"}'
```

Put the returned `controllerId`/`deviceSecret` into `firmware/dwara-gate/config.h`
and flash.

## Session lifecycle

```
CREATED ──gate ENTRY──▶ PARKED ──pay──▶ PAYMENT_PENDING ──gate EXIT──▶ EXITED
   │
   └──cancel / 15-min stale sweep──▶ ABORTED
```

- The backend clock starts at gate authorization; the phone only displays it.
- One active session per user **and** per vehicle (enforced under a row lock).
- Payment freezes the fee; only `PAYMENT_PENDING` with a successful payment can
  leave. An `EXITED` session may re-open once within 8 s of the exit gate.
