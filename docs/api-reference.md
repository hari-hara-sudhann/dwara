# Dwara API Reference

Base URL: `http://<host>:8080` (compose publishes `APP_PORT`). All app-facing
responses use the envelope:

```json
{ "success": true, "message": "...", "data": null }
```

Two endpoints deliberately return **bare objects** because the ESP32 firmware
parses them at the top level: `POST /gate/validate` and
`GET /devices/{id}/commands`.

Business refusals (duplicate session, wrong state, ownership) are HTTP `200`
with `"success": false` and a human-readable `message`; the mobile app shows
that message. Transport-level failures are real status codes: `400` malformed
body/UUID, `401` missing/invalid credentials, `403` forbidden.

## Authentication

| Scheme | Header | Used by |
|---|---|---|
| JWT | `Authorization: Bearer <jwt>` | App endpoints (`/auth/me`, `/vehicles`, `/facilities/{code}`, `/sessions`) |
| Admin key | `X-Admin-Key: <DWARA_ADMIN_KEY>` | `POST /facilities`, `PUT /facilities/...`, `POST /devices`, `POST /devices/{id}/commands` |
| Device secret | `Authorization: Bearer <deviceSecret>` | `POST /gate/validate`, `GET /devices/{id}/commands` |

If `DWARA_ADMIN_KEY` is unset the admin surface is closed (all admin calls
return `401`). Device secrets are returned once at registration; only a BCrypt
hash is stored.

## Auth

### `POST /auth/register`

```json
{ "name": "Hari", "email": "hari@example.com", "rawPassword": "hunter2" }
```

`200` → `data: { "userId": "...", "name": "Hari", "email": "hari@example.com" }`

### `POST /auth/login`

```json
{ "email": "hari@example.com", "password": "hunter2" }
```

`200` → `data: { "jwt": "<token>" }`. The token only carries the user id, so the
app calls `/auth/me` next.

### `GET /auth/me` *(JWT)*

`200` → `data: { "userId": "...", "name": "...", "email": "..." }`. Used to
restore a persisted token after an app relaunch.

## Vehicles *(JWT, owner from the token)*

| Method | Path | Body | `data` |
|---|---|---|---|
| `POST` | `/vehicles` | `{ "numberPlate": "KA05MJ4821", "type": "MC" \| "LMV" \| "HMV" }` | `{ "uuid": "..." }` |
| `GET` | `/vehicles` | — | `{ "userId": "...", "vehicles": [{ "vehicleId", "numberPlate", "type" }] }` |
| `PATCH` | `/vehicles/{vehicleId}` | `{ "numberPlate", "vehicleType" }` | `null` |
| `DELETE` | `/vehicles/{vehicleId}` | — | `null` (refused while an active session references it) |

## Facilities

### `GET /facilities/{facilityCode}` *(JWT)*

```json
{
  "facilityId": "042e...",
  "facilityCode": "FAC-0001",
  "name": "Dwara Demo Parking",
  "address": "MG Road, Bengaluru",
  "status": "ACTIVE",
  "pricingPolicy": { "mcRate": 0.005, "lmvRate": 0.010, "hmvRate": 0.020 }
}
```

Rates are **INR per second** (spec D14). The app shows `rate * 3600` as the
hourly figure.

### Administration *(admin key)*

| Method | Path | Body |
|---|---|---|
| `POST` | `/facilities` | `{ "name", "address", "facilityCode", "policy": { "mcRate", "lmvRate", "hmvRate" } }` |
| `PUT` | `/facilities/{facilityCode}/pricing` | `{ "mcRate", "lmvRate", "hmvRate" }` |
| `PUT` | `/facilities/{facilityCode}/status` | `{ "status": "ACTIVE" \| "INACTIVE" \| "MAINTENANCE" }` |

A new facility starts `INACTIVE`; activate it before starting sessions.

The backend also seeds one `ACTIVE` demo facility (`FAC-0001`) on startup unless
`DWARA_SEED_DEMO_FACILITY=false`.

## Sessions *(JWT)*

### `POST /sessions`

```json
{ "facilityCode": "FAC-0001", "vehicleId": "<uuid>" }
```

`200` → `data: "<sessionId>"` (a UUID **string**, not an object). Refused if the
user or vehicle already holds an active session (D9). The user row is locked for
the transaction, so two concurrent requests create only one session.

### `GET /sessions/active`

`data` is the active session or `null`:

```json
{
  "sessionId": "2128...",
  "sessionStatus": "PARKED",
  "facilityCode": "FAC-0001",
  "facilityName": "Dwara Demo Parking",
  "vehicleId": "0571...",
  "numberPlate": "KA05E2E1234",
  "createdAt": "2026-10-21T13:30:34Z",
  "entryAt": "2026-10-21T13:30:35Z",
  "exitAt": null,
  "estimatedFee": 0.010,
  "totalFee": null
}
```

### `GET /sessions?status=&page=0&size=20`

Own sessions only, newest first. `status` is optional
(`CREATED|PARKED|PAYMENT_PENDING|EXITED|ABORTED`); `size` is clamped to 100.

```json
{
  "sessions": [ { "...": "SessionDto" } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

### `GET /sessions/{id}/fee`

`data` is a **bare number** (INR). While parked it is the live per-second
accrual; after payment it is the frozen `totalFee`.

### `POST /sessions/{id}/pay`

`200` →

```json
{
  "paymentId": "...",
  "sessionId": "...",
  "amount": 12.34,
  "transactionReference": "MOCK-...",
  "paidAt": "2026-10-21T13:30:35Z"
}
```

The session moves `PARKED → PAYMENT_PENDING`. Payment is mocked (D10). The
session row is locked, so a double-tap or retry is refused rather than charged
twice.

### `POST /sessions/{id}/cancel`

Only a `CREATED` session may be cancelled; it becomes `ABORTED`.

## Gate hardware

### `POST /gate/validate` *(device secret, bare response)*

```json
{ "deviceId": "<uuid>", "sessionId": "<uuid>", "intent": "ENTRY" }
```

```json
{ "decision": "open", "reason": "NONE" }
{ "decision": "keep_closed", "reason": "PAYMENT_REQUIRED" }
```

Decision table:

| Intent | Session state | Decision / reason |
|---|---|---|
| ENTRY | `CREATED` at the same facility | `open`; backend stamps `entryAt` and parks the session |
| ENTRY | `PARKED`/`PAYMENT_PENDING`/`EXITED` | `keep_closed` / `WRONG_STATE_FOR_ENTRY` |
| ENTRY | `ABORTED` | `keep_closed` / `STALE_SESSION` |
| EXIT | `PAYMENT_PENDING` + successful payment | `open`; session becomes `EXITED` |
| EXIT | `PAYMENT_PENDING` + no successful payment | `keep_closed` / `PAYMENT_REQUIRED` |
| EXIT | `CREATED` or `PARKED` | `keep_closed` / `PAYMENT_REQUIRED` |
| EXIT | `EXITED` within 8 s of the exit gate opening | `open` (grace re-open) |
| EXIT | `EXITED` outside the grace window | `keep_closed` / `STALE_SESSION` |
| any | unknown session / other facility | `UNKNOWN_SESSION` / `WRONG_FACILITY` |

### `POST /devices` *(admin key)*

```json
{ "facilityCode": "FAC-0001", "gateType": "ENTRY", "firmwareVersion": "1.0.0" }
```

```json
{ "controllerId": "<uuid>", "deviceSecret": "<plaintext, shown once>" }
```

Flash `controllerId` and `deviceSecret` into `firmware/dwara-gate/config.h`.

### `GET /devices/{deviceId}/commands` *(device secret, bare response)*

`{ "command": "NONE" | "OPEN" | "KEEP_CLOSED" }`. The poll is the
acknowledgement: a delivered command is not served twice.

### `POST /devices/{deviceId}/commands` *(admin key)*

```json
{ "command": "OPEN" }
```

Queues an attendant override; the gate executes it on its next poll.

## Operations

`GET /actuator/health` → `{"status":"UP"}` (public, used by the compose
healthcheck; no other actuator endpoint is exposed).
