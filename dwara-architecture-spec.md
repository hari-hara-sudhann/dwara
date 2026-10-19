# Dwara — Architecture Specification (Interview Consensus)

> **Status:** Spec of record — decisions below are binding for the docs/ architecture documentation.
> **Date:** 2026-10-06 · **Branch:** `feature/gate-service` · **Backend:** Spring Boot 4.1 / Java 21 / PostgreSQL (existing)
> **Scope of this file:** The master spec. Follow-up work (already agreed) writes one markdown file per feature/process into `docs/`, indexed by `docs/README.md`.

---

## 1. Product Vision

Dwara is a parking management system that moves **authentication onto the user's mobile phone** and deliberately uses **no cameras and no vehicle sensors**. The entire on-ground hardware is:

- **1 × ESP32** (WiFi + BLE)
- **1 × MG90S servo motor** acting as the gate

A user with an account registers their vehicles in the mobile app, drives up to the gate, starts a parking session from the phone, and the gate opens. Fee accrues **per second**. When the user pays, the gate opens for exit. The phone replaces the camera, the RFID tag, and the ticket machine.

**Key philosophical decisions (from interview):**

| # | Decision | Choice |
|---|----------|--------|
| D1 | Facility discovery | **BLE beacon** — the ESP32 advertises the `facilityCode` in its BLE name/advertisement; the app scans, lists nearby facilities, no GPS/QR. |
| D2 | Gate topology | **One ESP32, one servo serving both entry and exit.** Entry/exit distinction lives in software (session state), not hardware. Firmware mechanics are **not** a backend concern — do not touch the Spring Boot app for this. |
| D3 | Billing clock start | **Backend confirms open** — `entryTimestamp` is set by the backend when it authorizes the session/open. |
| D4 | Session scope | **Entry → exit.** One session covers entry → parked → payment → exit; fee accrues until payment succeeds. |
| D5 | ESP32 connectivity | **BLE + WiFi.** BLE for user-facing proximity interactions; WiFi for backend validation calls, heartbeats, and remote commands. |
| D6 | BLE command security (v1) | **Plaintext `sessionId`** over BLE (deliberately accepted; see §9 Threat model). Backend does all real validation. |
| D7 | Device provisioning | **Flash-time config** — `facilityCode` and device identity baked into firmware at build/flash time. |
| D8 | Capacity | **No tracking** — any authorized user may enter anytime; occupancy is the operator's concern. |
| D9 | Anti-abuse | **One active session per user AND per vehicle.** Starting a session fails if either already has one active. |
| D10 | Payments (v1) | **Mock payment service.** `POST /sessions/{id}/pay` marks the Payment `SUCCESS` behind a `PaymentService` interface; a real gateway (e.g. Razorpay) is a later swap. |
| D11 | WiFi channel role | **Remote commands only** (backend → ESP32 `open` / `keep_closed`). No heartbeats, no OTA, no remote config in scope. |
| D12 | Mobile stack | **React Native**, must support iPhone. Framework-agnostic where possible, but RN is the assumed target. |
| D13 | Roles | **`USER` only.** No ADMIN role. Facility/device administration via REST endpoints guarded by a **static admin key header**. |
| D14 | Fee rules | **Flat per-second**, rounded up to the paisa (2 decimals). No minimum fee, no grace period, no daily cap. Currency: **INR (₹)**. |
| D15 | Gate mechanics | **Abstract angle spec** — firmware-level "servo rotates to OPEN/CLOSED angle"; physical mounting is out of scope for backend docs. |
| D16 | Failure policy (v1) | **Fail open.** If the exit flow breaks after payment, the gate opens on request with a log entry; abuse handling deferred. |
| D17 | Gate close | **Fixed timer** — servo opens, holds a configurable time (default ~5–10 s), auto-closes. No sensors. |
| D18 | Firmware stack | **Arduino framework** (ESP32 Arduino core; BLE + WiFi libraries). |
| D19 | Clock truth | **Backend clock only.** `entryTimestamp` = authorization time; `exitTimestamp` = payment success time. ESP32/app timings are never used for billing. |
| D20 | Stale sessions | **Scheduled abort** — a backend job ABORTs `CREATED` sessions after N minutes (default 15). |
| D21 | Repo layout | **Monorepo** — `mobile/` (React Native) and `firmware/` (Arduino) live beside the Spring Boot backend in this repo. |
| D22 | Docs shape | **Index + per-feature files** in `docs/`, Mermaid diagrams inline, `.mmd` sources allowed in `docs/diagrams/`. |

---

## 2. System Components

```
┌───────────────┐   HTTPS    ┌─────────────────────┐   JDBC    ┌────────────┐
│ Mobile app    │ ─────────► │ Spring Boot backend │ ────────► │ PostgreSQL │
│ (React Native)│ ◄───────── │ (this repo, Java 21)│           └────────────┘
└──────┬────────┘            └─────────▲───────────┘
       │ BLE (GATT)                    │ WiFi (HTTP)
       │ sessionId+intent / ack        │ validate, open/keep_closed
       ▼                               │
┌──────────────────────────────────────┴─────┐
│ ESP32 (Arduino): 1 × MG90S servo gate      │
│ BLE peripheral + WiFi station              │
└────────────────────────────────────────────┘
```

- **Mobile app (React Native)** — auth, vehicle management, facility discovery via BLE scan, session start/stop, live fee display, mock payment trigger, BLE courier to the ESP32.
- **Backend (Spring Boot, existing)** — identity (JWT), users, vehicles, facilities + `PricingPolicy` (jsonb), sessions + lifecycle, fee calculation, mock payments, gate validation API for the ESP32, device registry, scheduled stale-session abort.
- **ESP32 firmware (Arduino)** — BLE peripheral advertising the facility; GATT characteristic accepting `{sessionId, intent}`; WiFi client that calls the backend to validate; servo actuation on `open`; fixed-timer close; a `keep_closed` default state.
- **PostgreSQL** — existing schema (see §4).

---

## 3. Core Flows (canonical, to be diagrammed per-file in docs/)

### 3.1 Facility discovery (BLE beacon)

1. ESP32 (flashed with `facilityCode`) boots, joins facility WiFi, starts BLE advertising. Its advertised local name encodes the facility, e.g. `DWARA-<facilityCode>` (exact format fixed in `docs/ble-protocol.md`).
2. App scans for peripherals whose name matches the `DWARA-` prefix and lists them (name → facilityCode).
3. User picks the facility; app shows its details (fetch by `facilityCode` from backend).

No GPS, no QR, no manual entry in v1.

### 3.2 Session creation (app → backend)

1. Authenticated user (JWT) selects facility + one of their vehicles; app calls **`POST /sessions`**.
2. Backend validations (existing `ParkingSessionService.createSession` semantics): user exists, facility exists and `ACTIVE`, vehicle exists and belongs to user, **no other active session for the same user AND none for the same vehicle** (D9).
3. Session persisted with `sessionStatus = CREATED`, no timestamps yet.
4. App shows "Session started — validate at the gate." The **stale-session job** will ABORT it if not validated within N minutes (D20).

> Note: the user taps "Start session" **before** arriving at the gate; the session id is what gets carried to the ESP32. Direction intent (`ENTRY`) is declared by the app (the app UI simply does not offer a wrong intent — D-answer to "entry vs exit").

### 3.3 Entry (gate validation)

1. User, near the gate, taps **"Open entry gate"**. App connects to the ESP32 over BLE (service UUID fixed in docs) and writes **`{ sessionId, intent: ENTRY }`** to the gate characteristic.
2. ESP32 calls the backend over WiFi: **`POST /gate/validate`** with `{ deviceId, sessionId, intent }`.
3. Backend validates:
   - device belongs to the session's facility (via `GateController` ↔ facility),
   - session exists, belongs to the authenticated context it can verify (session status machine below),
   - intent consistent with session state: **`CREATED` → ENTRY is valid**; `PARKED`/`PAYMENT_PENDING` → ENTRY rejected.
4. On valid ENTRY: backend sets `entryTimestamp = Instant.now()` (backend clock, D19), status `CREATED → PARKED` **(D3: "backend confirms open")**, and responds **`{ decision: "open" }`**.
5. ESP32 rotates the servo to the OPEN angle (D15), holds for the fixed timer (D17), returns to CLOSED angle.
6. ESP32 acknowledges back over BLE (`{ sessionId, result: OPENED }`); app updates UI. The ack is informational only — billing never depends on it.

Invalid cases return `{ decision: "keep_closed", reason }` (stale/aborted session, wrong intent, wrong facility, session already PARKED).

### 3.4 Parking (accrual)

- Status `PARKED`. App polls (or fetches on demand) **`GET /sessions/{id}/fee`** which computes `rate × elapsedSeconds` from the facility `PricingPolicy` (existing code path). Rates are per-second, per vehicle type (MC / LMV / HMV).
- Live display is cosmetic; the **authoritative** fee is computed at payment time from backend clock only (D19).

### 3.5 Exit (payment → gate open)

1. User taps **"Pay & Exit"**. App calls **`POST /sessions/{id}/pay`**.
2. Backend (mock `PaymentService`, D10):
   - computes the **final fee** = `rate × (now − entryTimestamp)`, rounded up to 2 decimals (fee stops accruing **at payment success**, D-answer "fee freeze"),
   - sets `exitTimestamp = now` (backend clock), `sessionStatus = PAYMENT_PENDING`,
   - creates a `Payment` row (`amount`, `status = SUCCESS` immediately, `transactionReference = "MOCK-<uuid>"`),
   - responds with the payment receipt.
3. User taps **"Open exit gate"**. App writes **`{ sessionId, intent: EXIT }`** over BLE.
4. ESP32 → backend **`POST /gate/validate`**: valid when status is `PAYMENT_PENDING` **with a `SUCCESS` payment** (or `EXITED` grace re-entry within the fixed close window — see edge cases). Responds **`{ decision: "open" }`**, sets status `PARKED→` (already) `PAYMENT_PENDING → EXITED`.
5. Servo opens, fixed timer, closes. BLE ack to app.

**Fail-open rule (D16, v1):** if payment succeeded but validation keeps failing (device offline, BLE flaky), a retry path exists; the spec deliberately accepts that a determined user may social-engineer an open in v1 — logged, not blocked. Real mitigation (auto-open via backend after X minutes, or attendant override) is a documented future option.

### 3.6 Remote commands (backend → ESP32, D11)

- ESP32 polls **`GET /devices/{deviceId}/commands`** (short poll, interval fixed in firmware docs) with its device secret as `Authorization: Bearer <deviceSecret>`.
- Response contains pending commands: `OPEN` (attendant/override/fail-open recovery) or `KEEP_CLOSED`; ESP32 executes and the next poll implicitly acks.
- This is the **only** backend-initiated channel in scope. No heartbeats, no OTA, no remote pricing config (D11).

---

## 4. Data Model (existing entities; deltas listed)

Existing entities stay as-is (`User`, `Vehicle`, `ParkingFacility` + `PricingPolicy` jsonb, `ParkingSession`, `Payment`, `GateController`). Deltas required by this spec:

| Change | Why |
|---|---|
| `GateController` + add `deviceSecretHash` (write-only) | D6/D11: the ESP32 authenticates to the backend with a per-device secret (hash stored, secret itself flashed at build time, D7). Needed for `/gate/validate` and `/devices/{id}/commands` auth. |
| `GateController` + `deviceName` or keep `firmwareVersion` | For provisioning traceability (D7: flash-time config). |
| `SessionStatus` enum: no change | `CREATED → PARKED → PAYMENT_PENDING → EXITED`, `ABORTED` for the scheduled job (D20). |
| `Payment` `transactionReference` | Mock provider writes `MOCK-<uuid>` (D10). |
| No capacity fields | D8. |

`PricingPolicy` (jsonb) unchanged: `mcRate`, `lmvRate`, `hmRate` — **per-second INR** rates, e.g. `mcRate: 0.005` (₹18/hr), `lmvRate: 0.01` (₹36/hr), `hmRate: 0.02` (₹72/hr). Fee rounding: `ceil` to 2 decimal places.

---

## 5. API Surface (design-first — no controllers exist yet in the codebase)

All under JWT auth unless marked **[admin-key]** (static `X-Admin-Key` header from env, D13) or **[device]** (ESP32 bearer device secret).

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | public | Register (exists in service layer) |
| POST | `/auth/login` | public | Login → JWT (exists) |
| POST | `/vehicles` | JWT | Add vehicle (exists) |
| GET | `/vehicles` | JWT | List my vehicles (exists) |
| PATCH/DELETE | `/vehicles/{id}` | JWT | Update/delete own vehicle (exists) |
| POST | `/facilities` | **[admin-key]** | Create facility + pricing (exists as open service) |
| GET | `/facilities/{facilityCode}` | JWT | Facility lookup by BLE-discovered code |
| PUT | `/facilities/{facilityCode}/pricing` | **[admin-key]** | Update pricing policy |
| PUT | `/facilities/{facilityCode}/status` | **[admin-key]** | ACTIVE/INACTIVE/MAINTENANCE |
| POST | `/devices` | **[admin-key]** | Register a GateController (returns deviceId; secret flashed separately, D7) |
| POST | `/gate/validate` | **[device]** | ESP32 asks open/keep_closed (§3.3/§3.5) |
| GET | `/devices/{deviceId}/commands` | **[device]** | Poll remote commands (D11) |
| POST | `/sessions` | JWT | Create session (exists; add D9 uniqueness + D20 interplay) |
| GET | `/sessions/active` | JWT | My active session (exists) |
| GET | `/sessions/{id}/fee` | JWT | Live fee (exists) |
| POST | `/sessions/{id}/pay` | JWT | **Mock** pay → freezes fee, SUCCESS (D10) |
| POST | `/sessions/{id}/cancel` | JWT | Cancel a CREATED session (pairs with D20) |
| POST | `/sessions/{id}/request-entry` / `request-exit` | JWT | *Optional sugar:* app-side validation mirror of §3.3/§3.5 before BLE write, so the app can pre-check without the ESP32 round-trip. |
| (job) | — | internal | Scheduled ABORT of stale CREATED sessions (D20) |

Response envelope: existing `ResponseObject<T> { success, message, data }`.

---

## 6. BLE Protocol (summary; full GATT table → `docs/ble-protocol.md`)

- **One custom GATT service** (UUID TBD in docs) with:
  - **Gate Command characteristic (write):** `{ sessionId, intent }` — intent ∈ `ENTRY | EXIT` (D-answer: app declares intent, UI prevents wrong intent),
  - **Gate Status characteristic (read/notify):** `{ sessionId, result: OPENED | DENIED, reason }`,
  - Advertisement local name: `DWARA-<facilityCode>` (discovery, D1).
- Payload encoding (JSON vs compact TLV), MTU handling, and reconnect/backoff rules → `docs/ble-protocol.md`.
- Security posture (v1, D6): payload is plaintext; **the backend is the only trust anchor** — the ESP32 never acts on BLE content alone, it always re-validates via `/gate/validate`. Any phone can write a sessionId; the worst outcome is a `keep_closed` unless the session genuinely qualifies.

---

## 7. ESP32 Firmware (Arduino) — responsibilities

- **Radio roles:** BLE peripheral (always on) + WiFi station (always on, facility credentials + `facilityCode` + `deviceId` + `deviceSecret` baked at flash time, D7).
- **Servo control (MG90S):** abstract angles `OPEN_ANGLE` / `CLOSED_ANGLE` (D15); non-blocking sweep; power considerations documented (MG90S stalls are a known risk — external 5 V supply recommended, decoupling capacitor; wiring in `docs/hardware.md`).
- **Close policy:** fixed timer after open (default 8 s, `#define`-configurable, D17).
- **Validation client:** HTTPS `POST /gate/validate` on BLE write; executes decision; BLE-notifies result.
- **Command poller:** periodic `GET /devices/{id}/commands` (D11).
- **Error behavior:** unreachable backend → BLE status `DENIED/BACKEND_UNREACHABLE` (fail-closed at the gate, D16 applies to *post-payment* recovery via remote commands, not to unauthenticated opens).
- Framework: **Arduino** (D18). Sketch structure, pin map, and OTA-out-of-scope note → `docs/firmware.md`.
- **Temporary actuator (D23):** the MG90S has a power-supply issue; until fixed, the servo is stood in for by an **LED on D18** behind the abstract `GateActuator` interface — `openGate` = 3 blinks, `closeGate` = 2 blinks. Implemented in `firmware/dwara-gate/` (Arduino sketch); rationale + revert path in `docs/firmware.md` §"Temporary LED workaround". All other firmware behavior (flows, timers, remote commands) is unchanged.

---

## 8. Mobile App (React Native) — responsibilities

- JWT auth flows (register/login/logout), token storage.
- Vehicle CRUD.
- **BLE scanner** filtering `DWARA-` prefixed names (D1) → facility list; facility detail fetch by code.
- Session start (facility + vehicle picker), live fee screen, **mock Pay & Exit**, gate open buttons writing `{sessionId, intent}` over BLE (D-answer: UI makes wrong intent impossible — entry button only offered with CREATED session, exit button only after payment success).
- iPhone support is a hard requirement (D12): use a BLE library with iOS background caveats documented (app must be foreground during gate interaction in v1).
- Screens/state machine → `docs/mobile-app.md`.

---

## 9. Security Model & Threats (v1 posture)

| Threat | Mitigation in v1 | Accepted residual risk |
|---|---|---|
| Rogue BLE client writes someone's sessionId | Backend validates session + state + facility; intent cross-check | None material — worst case `keep_closed` |
| Session replay at exit gate | Status machine: EXIT requires PAYMENT_PENDING + SUCCESS payment; EXITED is terminal | Re-open within close window treated as grace (docs define) |
| Stolen JWT | Standard bearer-token risk; short expiry from `jwt.expiration` | Out of scope v1 |
| Device secret extraction from flashed unit | Secret stored hashed server-side; flashing controlled (D7) | Physical access = game over (documented) |
| BLE spoofing / MITM | D6 accepts plaintext BLE | Backend is trust anchor; documented |
| Fee manipulation | All fee math server-side from backend clock (D19) | None |
| Unauthenticated facility/device admin | **Static admin key** (D13) | Key sharing; rotate via env; harden later |
| Payment fraud | Mock payment (D10) — no real money | Entire payment flow is a stub in v1 |
| Tailgating at the gate (car follows car) | Fixed-timer close (D17), no sensors | Accepted; operator's concern (D8) |
| Fail-open abuse at exit | Logged manual/remote recovery (D16) | Accepted v1; documented future hardening |

---

## 10. Edge Cases & Rules (agreed)

1. **Stale CREATED sessions** → scheduled job ABORTs after 15 min (D20). App shows "session expired, start again."
2. **Double-start** → D9: 409-style failure if user OR vehicle has an active (CREATED/PARKED/PAYMENT_PENDING) session.
3. **ENTRY with PARKED session** → `keep_closed` (already inside).
4. **EXIT without payment** → `keep_closed` with reason `PAYMENT_REQUIRED`.
5. **EXIT after EXITED** (gate re-approach within seconds) → grace re-open allowed once within the close window; afterwards `keep_closed` (docs define exact window; default = servo close timer).
6. **App crash between payment and BLE write** → payment stands; user re-opens app, taps exit gate; session is PAYMENT_PENDING+SUCCESS → validates (fail-safe by design).
7. **ESP32 offline / backend unreachable during validation** → `keep_closed`; for post-payment recovery use remote OPEN command (D11 + D16).
8. **Vehicle deleted mid-session** → delete is blocked while a session referencing it is active (docs specify constraint).
9. **Facility set INACTIVE/MAINTENANCE mid-session** → existing sessions run to completion; new sessions refused.
10. **Clock drift** — irrelevant by construction: only backend `Instant.now()` is used for billing (D19).
11. **Multiple vehicles, one user** → allowed (sessions are per-vehicle, D9 strictness is per user AND per vehicle simultaneously — a user may not have two concurrent sessions even with two vehicles).
12. **MG90S stall/jam** → firmware watchdog: if target angle not reached within timeout, cut PWM, mark local fault, report `DENIED/ACTUATOR_FAULT`; manual push-override is a hardware reality documented in `docs/hardware.md`.

---

## 11. Docs Deliverables (next task — one file per feature/process, D22)

All under `docs/`, indexed by `docs/README.md`, Mermaid inline, diagram sources optional in `docs/diagrams/`:

| File | Content |
|---|---|
| `docs/README.md` | Index, system overview diagram, glossary, decision log (copy of §12) |
| `docs/system-architecture.md` | Component diagram, monorepo layout (D21), request lifecycles |
| `docs/data-model.md` | Entity-relationship diagram, entities, enums, `PricingPolicy` jsonb, deltas from §4, migration notes |
| `docs/api-reference.md` | Every endpoint (§5) with request/response JSON examples, error codes, auth schemes |
| `docs/auth-and-security.md` | JWT flows, admin key, device secret scheme, threat model (§9) |
| `docs/ble-protocol.md` | GATT table (UUIDs), advertisement format, payload schemas/byte layout, MTU, reconnect rules |
| `docs/entry-flow.md` | Discovery + session creation + entry validation (§3.1–3.3), sequence diagram |
| `docs/exit-and-payment.md` | Fee accrual, mock payment, exit validation, receipt (§3.4–3.5), sequence diagram |
| `docs/session-lifecycle.md` | State machine diagram, all edge cases (§10), stale-session job, cancellation |
| `docs/firmware.md` | **Implemented** — Arduino sketch architecture (`firmware/dwara-gate/`), state machine, WiFi client, command poller, abstract actuator, **temporary LED workaround (servo power issue)**, config constants |
| `docs/hardware.md` | BOM, wiring (MG90S → GPIO + external 5 V, capacitor, level/power notes), enclosure notes, push-override |
| `docs/mobile-app.md` | RN app structure, screens, BLE integration (iOS caveats), state management |
| `docs/provisioning-and-ops.md` | Flash-time config procedure (D7), device registration flow, remote commands, deployment (docker-compose exists), ops runbook |

---

## 12. Decision Log (interview record)

Rounds of `ask_user` questions and the binding answers:

- **R1:** Discovery = BLE beacon · Topology = one ESP32, entry+exit combined in firmware, "do not touch the Spring Boot app for gate mechanics" · Clock start = backend confirms open · Session scope = entry to exit.
- **R2:** Radios = BLE + WiFi · BLE security = plaintext sessionId (v1) · Provisioning = flash-time config · Capacity = none · Anti-abuse = strict (user AND vehicle).
- **R3:** Payments = mock behind `PaymentService` · WiFi role = remote commands only · Mobile = React Native (iPhone required) · Roles = USER only · Fees = flat per-second only.
- **R4:** Mechanics = abstract angle spec · Fail policy = fail open v1 · Close = fixed timer · Firmware = Arduino · Docs = Mermaid + API reference + hardware/BOM + BLE tables.
- **R5:** Admin access = static admin key · Clock = backend only · WiFi channel = ESP32 calls backend itself for validation ("user taps start → BLE carries sessionId → ESP32 → backend → open/keep_closed") · Repo = monorepo · Docs = index + per-feature files.
- **R6:** Validation carrier = ESP32 over WiFi (phone is courier) · BLE payload = sessionId + intent · Intent = app-declared, UI-enforced · Fee freeze = at payment success · Stale sessions = scheduled abort · Currency = INR.
- **R7 (implementation round):** ESP32 sketch created at `firmware/dwara-gate/` (20-pin board, GPIO18/D18) · Actuator kept abstract (`GateActuator`) per D15 · **D23 — Temporary LED actuator:** servo has a power-supply problem; LED build blinks 3× on `openGate`, 2× on `closeGate`; documented in `docs/firmware.md`; revert path = implement `ServoGateActuator` + swap one line. All other firmware behavior per D1–D22 unchanged.

---

## 13. Out of Scope (v1)

- Real payment gateway integration (Razorpay/Stripe), refunds, wallets.
- Heartbeats, OTA firmware updates, remote pricing/config push.
- ADMIN user role, facility-operator web console.
- Capacity tracking, occupancy display.
- BLE encryption/pairing, session tokens over BLE.
- GPS/QR facility discovery, manual code entry.
- Grace periods, minimum fees, daily caps in pricing.
- Multi-gate facilities (>1 servo), boom-barrier mechanical design.
- iOS background BLE operation.
