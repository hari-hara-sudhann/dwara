# Dwara — phone-controlled parking gate

Dwara is a BLE + REST parking system: the phone finds a gate, starts a session,
and the ESP32 gate asks the backend whether the car may enter or leave. Billing
is per second and stops at payment. One repository holds the backend, the mobile
app and the gate firmware.

```
frontend/ (Expo)  ──REST/JWT──▶  Spring Boot backend  ◀──REST/device secret──  firmware/dwara-gate (ESP32)
                                     │  PostgreSQL
                                     └── mock payment (no real money)
```

## Run it

Prerequisites: Docker + Compose v2, JDK not required for the Docker workflow.

```bash
cp .env.example .env       # set DB_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up -d --build
curl -s localhost:8080/actuator/health
```

The backend seeds an `ACTIVE` demo facility `FAC-0001` (₹0.005/0.010/0.020 per
second for MC/LMV/HMV) unless `DWARA_SEED_DEMO_FACILITY=false`. Set
`DWARA_ADMIN_KEY` in `.env` to use the admin endpoints.

Register a gate controller and flash the returned id/secret into
`firmware/dwara-gate/config.h`:

```bash
curl -s -X POST localhost:8080/devices \
  -H "X-Admin-Key: $DWARA_ADMIN_KEY" -H 'Content-Type: application/json' \
  -d '{"facilityCode":"FAC-0001","gateType":"ENTRY","firmwareVersion":"1.0.0"}'
```

## Test it

```bash
./scripts/docker-test.sh
```

Builds the images, starts Postgres + the backend, runs `mvn verify` (JUnit +
JaCoCo 100 % service-line gate), writes `reports/junit`, `reports/jacoco` and
`reports/test-output.log`, removes only its own containers, and prints a
summary. It never stops the development stack. See
[docs/testing.md](docs/testing.md).

Mobile and firmware checks:

```bash
npm --prefix frontend run typecheck
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app firmware/dwara-gate
```

## Demo flow

1. Register in the app → add a vehicle (plate + MC/LMV/HMV).
2. **Find** → scan → the ESP32 advertising `DWARA-FAC-0001` appears; select it.
3. Create a session; the backend returns `CREATED`.
4. **Open Entry Gate** → BLE → backend `POST /gate/validate` → `open`; the fleet
   timestamp is stamped and the session becomes `PARKED`.
5. Wait; the fee on screen is the backend's per-second accrual.
6. **Exit & Pay** → mock payment freezes the fee and moves the session to
   `PAYMENT_PENDING`.
7. **Open Exit Gate** → backend verifies the payment → `open`, session `EXITED`.
8. **Activity** lists the finished session from `GET /sessions`.

## Documentation

[docs/README.md](docs/README.md) indexes the API reference, mobile setup, BLE
protocol, hardware wiring, firmware and testing guides.

## Verified status

- Backend: full workflow verified end to end against a compose stack (register →
  login → vehicle → session → entry validation → fee → pay → exit validation →
  history → remote command poll).
- Automated: `./scripts/docker-test.sh` → 118 tests, 0 failures, JaCoCo 100 %
  service-line coverage, exit code 0.
- Mobile: real API client and real BLE client; TypeScript typecheck passes.
  BLE and the iOS build require a physical iPhone and have **not** been run in
  this environment.
- Firmware: compiles for ESP32 (`arduino-cli`, Huge APP partition). Servo, BLE
  and backend round-trips on real hardware have **not** been physically verified.
