# Dwara — Project Completion & Docker Test Workflow Spec

> **Status:** Design of record for the remaining work. **No code has been changed.**
> **Date:** 2026-10-21 · **Branch:** `finish-backend` · **Owner:** hari-hara-sudhann
> **Inputs:** `dwara-architecture-spec.md` (spec of record, D1–D23), `REMAINING_WORK.md` (2026-10-19 snapshot), `docker-compose.yml`, `Dockerfile`, `pom.xml`, `docs/firmware.md`, `docs/serial-bridge.md`, `frontend/`, `firmware/dwara-gate/`, and six interview rounds recorded in §5.
> **Supersedes:** `REMAINING_WORK.md` — that local note is deleted in Phase 3, item B0.
> **Purpose:** (a) state exactly what remains for this project to be complete, as a step-by-step plan; (b) specify the Docker Compose test workflow that builds the images, starts dependencies, runs `mvn verify` (JUnit + Mockito + JaCoCo), and collects reports on the host.

---

## 1. Request, as interpreted

Two deliverables, in one document:

1. **Completion report + step-by-step plan.** The project is mid-flight: the backend REST surface exists, tests exist, the mobile app is mock-only, the firmware works but its remote-command poller is parked, and 12 of the 13 deliverables named in `dwara-architecture-spec.md` §11 do not exist (`docs/firmware.md` is the only one written). The plan must say what is left, in what order, and how each item is proven done.
2. **Docker Compose test workflow.** One command that builds the images, starts the app and its dependencies, waits for health, runs the JUnit/Mockito/JaCoCo test suite, captures full output and the original exit code, writes JUnit XML and JaCoCo reports into `reports/junit/` and `reports/jacoco/` on the host, always cleans up containers/networks, and prints a pass/fail summary. Must work locally and in CI.

**Definition of done for the project (interview R1/Q2):** backend + mobile + firmware, not backend-only. The driver is an **academic submission** (R1/Q3), so demonstrability, documentation, and reproducible test evidence all count.

**Frontend and firmware are in scope** to the level of: mobile wired to the real REST API *and* real BLE, firmware poller wired, servo actuator current, all 13 docs written.

---

## 2. Current state — verified inventory

Facts below were checked against the working tree on 2026-10-21, not copied from notes.

| Area | State | Evidence |
|---|---|---|
| Backend REST surface | Implemented — auth, vehicles, facilities, devices, gate validate, sessions, commands | `src/main/java/me/hari/dwara/controllers/*` (7 controllers) |
| Auth | JWT bearer, `X-Admin-Key`, per-device bearer secret | `configs/SecurityConfig.java`, `configs/JwtAuthenticationFilter.java`, `configs/AdminKeyFilter.java`, `services/DeviceAuthenticationService.java` |
| Domain | 7 entities, 11 enums, 7 repositories, 4 mappers | `entities/`, `repositories/`, `mappers/` |
| Service layer | Complete, incl. scheduled stale-session abort and mock payment | `services/`, `services/ScheduledSessionAborter.java`, `services/MockPaymentService.java` |
| Unit tests | 15 test classes, all `services/*` tests on Mockito | `src/test/java/me/hari/dwara/**` |
| Test suite status | **113 tests**, 0 failures, **1 error** — `DwaraApplicationTests.contextLoads` (`Unable to determine Dialect without JDBC metadata`: no DB, no JWT env) | `target/surefire-reports/*.xml`, summed 2026-10-21 |
| Controller/security tests | **None** — no MockMvc, no `@WebMvcTest`, no full-context wire tests | `src/test` tree |
| JaCoCo | Configured: `prepare-agent`, `report` in `test` (services only), 100 % line gate in `verify` | `pom.xml` |
| Test DB wiring | **None** — `application.yaml` requires `DB_*` and `JWT_*`; no `src/test/resources` | `src/main/resources/application.yaml` |
| Docker | `Dockerfile` (maven build stage + JRE runtime, `target/app.jar`); `docker-compose.yml` with `postgres` (healthcheck) + `backend` (`depends_on: service_healthy`) | root |
| App health endpoint | **None** — no `spring-boot-starter-actuator` | `pom.xml` |
| Test compose / runner / CI | **None** — no `test` service, no `scripts/`, no `.github/`, no `reports/` | repo root |
| Deployment env | `.env` exists but has **no `DWARA_ADMIN_KEY`** → admin surface closed in compose | `.env`, `application.yaml` (`dwara.admin-key: ${DWARA_ADMIN_KEY:}`) |
| Mobile | Expo/React Native app, 11 screens, `mockApi.ts` + `mockBle.ts`; only `GateTestScreen` imports `realBle` | `frontend/src/**` |
| Firmware | WiFi + serial transports, remote auth, servo actuator current, LED actuator legacy; **command poller not wired into the loop** | `firmware/dwara-gate/dwara-gate.ino`, `docs/firmware.md` |
| Docs | 1 of the 13 §11 deliverables exists (`docs/firmware.md`); `docs/serial-bridge.md` and `docs/diagrams/` are extras not listed in §11 | `docs/` |
| Diagram/model sources | `docs/diagrams/Dwara-Complete.mdj`, ER + DFD PNGs, `docs/Dwara.pod(.xml)` | `docs/` |

**Contradiction resolved (interview R4/Q2):** the MG90S **servo build is current**; `LedGateActuator` is legacy. `docs/firmware.md` already says this — the stale claim lives only in `REMAINING_WORK.md`, which this plan deletes.

---

## 3. Ambiguities this spec resolves

| # | Ambiguity | Resolution |
|---|---|---|
| A1 | "Project complete" undefined | Backend + mobile (real API **and** real BLE) + firmware (poller wired) + all 13 docs (R1/Q2, R4/Q1) |
| A2 | Whether the failing `contextLoads` blocks completion | Yes. `mvn verify` must exit 0 with no exclusions (R2/Q2) |
| A3 | Where the test DB comes from | The compose `postgres` service plus a Spring `test` profile — no H2, no Testcontainers (R2/Q1) |
| A4 | Test stack layout | A `test`-profile service set inside the **existing** `docker-compose.yml`, executed under a distinct compose project (R2/Q3) |
| A5 | How reports reach the host | Bind mounts only, no `docker cp` (R2/Q4) |
| A6 | How `docker compose` should be driven | A bash script is the interface; a Makefile alias is **not** required (R3/Q1) |
| A7 | Why start the app at all | App container is a **readiness gate**: prove the real image boots with the deployment config, then tests run against the DB (R3/Q2) |
| A8 | Health check mechanism | Add `spring-boot-starter-actuator`, health-check `/actuator/health` (R3/Q3) |
| A9 | CI | Include `.github/workflows/tests.yml` that calls the same script and uploads reports (R3/Q4) |
| A10 | Exit-code semantics | 0 pass; Maven's own code passed through on test failure; 2/3/4 for wrapper-detected build/health/report failures (R5/Q1) |
| A11 | Report retention | `reports/` wiped and regenerated each run; gitignored (R5/Q2) |
| A12 | Dev stack already running | Fail fast with a clear message; `--force` is the escape hatch (R5/Q3) |
| A13 | Coverage gate in the workflow | Run `mvn verify` so the 100 % service-line gate is enforced (R5/Q4) |
| A14 | Test configuration values | Generated per run by the script and injected as env vars; test profile carries non-secret defaults so a bare `./mvnw test` still works (R6/Q1, see §7.4) |
| A15 | Maven dependency cache | A named, externally-owned volume so `down -v` does not nuke it (R6/Q2) |
| A16 | Summary contents | Pass/fail + counts, coverage %, report paths, failing test names — stdout only, no `reports/summary.txt` (R6/Q3) |
| A17 | Report document shape | Phased checklist: ordered phases, checkboxes, files touched, acceptance criterion per item (R6/Q4) |
| A18 | Backlog priority | Everything from `REMAINING_WORK.md` is **required**, not tiered (R4/Q3) |
| A19 | Docs scope | Every §11 deliverable accounted for: 12 written now + `firmware.md` updated, indexed by `docs/README.md` (R4/Q4) |

---

## 4. Deliverable 1 — Completion plan (phased checklist)

Conventions: each item has an ID, the files it touches, and a verifiable acceptance criterion. Items are ordered by dependency; a phase is done only when every box in it is checked. `⟦…⟧` marks a decision already fixed in §5/§7 and not to be re-litigated during implementation.

### Phase 0 — Baseline and prerequisites

- [ ] **P0.1 Reproduce the baseline.** Run the suite exactly as it is and record the result.
  Files: none. Acceptance: `./mvnw test -Dtest='!DwaraApplicationTests'` passes and `./mvnw test` reproduces the single `contextLoads` error; the measured test count, failure/error/skip counts, and the regenerated `jacoco.csv` service-line ratio are pasted into the new plan document as the "before" state. The 100 % service-line figure in this spec is the **2026-10-19 recorded snapshot** (`REMAINING_WORK.md` §"Verification snapshot"); the report tree is not retained in `target/` today, so re-measure it here instead of trusting the number.
  Commands:
  ```bash
  ./mvnw -B -ntp test -Dtest='!DwaraApplicationTests'
  ./mvnw -B -ntp test || echo "expected failure reproduced"
  ```
- [ ] **P0.2 Pin the toolchain.** Confirm Docker ≥ 24 with Compose v2 (already `Docker 29.8.0` / `Compose v5.5.1`), JDK 21, and `bash`. Document minimum versions in `README`/`docs/provisioning-and-ops.md`.
- [ ] **P0.3 Establish the report artifact.** Create `COMPLETION_PLAN.md` at the repo root containing the phase/checkbox content of §4–§7 of this spec plus the P0.1 baseline numbers: problem statement, current-state table, phase order, and per-item acceptance criteria. Add `reports/` to `.gitignore`.
  Acceptance: `COMPLETION_PLAN.md` exists, is self-sufficient (a reader needs no other doc to start), and every item has an acceptance line.

### Phase 1 — Green test suite

- [ ] **P1.1 Add the Spring `test` profile.** New `src/test/resources/application-test.properties`:
  ```properties
  spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/dwara_test}
  spring.datasource.username=${TEST_DB_USER:dwara}
  spring.datasource.password=${TEST_DB_PASSWORD:dwara}
  spring.jpa.hibernate.ddl-auto=create-drop
  spring.jpa.show-sql=false
  # Test-only values. Overridden per run by scripts/docker-test.sh with random ones.
  jwt.secret=${TEST_JWT_SECRET:test-only-dwara-jwt-secret-not-for-deployment-000000000000}
  jwt.expiration=${TEST_JWT_EXPIRATION:PT1H}
  dwara.admin-key=${TEST_ADMIN_KEY:test-admin-key}
  ```
  Acceptance: `@ActiveProfiles("test")` on `DwaraApplicationTests` makes `contextLoads` pass against a reachable Postgres; no production default is introduced in `application.yaml`.
- [ ] **P1.2 Add the test database bootstrap.** New `docker/postgres/init-test-db.sql` mounting into the postgres init dir, creating `dwara_test` alongside the app database. Keep it rerun-safe (`SELECT ... WHERE NOT EXISTS ... \gexec`).
  Acceptance: after a fresh `postgres` container, `psql -l` lists both the app DB and `dwara_test`.
- [ ] **P1.3 Make the full suite green.** Ensure `./mvnw verify` exits 0 with **no exclusions and no `-DskipTests`**. Do not weaken assertions or delete `DwaraApplicationTests`. If the context test reveals a real startup defect, fix the defect.
  Acceptance: `./mvnw -B -ntp verify` → exit 0; `target/surefire-reports` shows 0 failures, 0 errors; JaCoCo check passes.
- [ ] **P1.4 Add controller/security tests** (item 10 of `REMAINING_WORK.md`, still required). Cover, with `@WebMvcTest`/`MockMvc` plus Mockito service doubles: `POST /auth/register`, `POST /auth/login`, `POST /vehicles` (+ ownership rejection), `GET /sessions/active`, `POST /sessions/{id}/pay` (fee freeze + double-pay rejection), `POST /gate/validate` device auth + `open`/`keep_closed`, `GET /devices/{id}/commands`, admin-key 401/403 paths on `/facilities` and `/devices`, and the `ResponseObject` envelope shape with `data`.
  Acceptance: at least one test per HTTP surface in `dwara-architecture-spec.md` §5; unauthenticated and wrong-role paths asserted, not assumed; suite still 100 % on `services/**` lines.
- [ ] **P1.5 Document how to run tests without Docker.** Add a "Testing" section to `docs/provisioning-and-ops.md`: start `postgres` only, export `TEST_*`, run `./mvnw verify`; plus the one-liner for the full Docker path.
  Acceptance: following the section on a clean machine produces a green `verify` without reading source.

### Phase 2 — Docker Compose test workflow

Full design in §7. This phase is the implementation of that design.

- [ ] **P2.1 Actuator dependency + health endpoint.** `pom.xml` (+`spring-boot-starter-actuator`), `application.yaml` (`management.endpoints.web.exposure.include: health`, `management.endpoint.health.show-details: never`), `SecurityConfig` (permit `/actuator/health`).
  Acceptance: `curl -fsS localhost:8080/actuator/health` returns `{"status":"UP"}` on an unauthenticated request; all other actuator endpoints stay closed.
- [ ] **P2.2 Extend `Dockerfile` with a `test` stage.** Maven 3.9.6 + Temurin 21 alpine, `WORKDIR /workspace`, copy `pom.xml` then `src` **and `scripts/`**, `chmod +x scripts/*.sh`, no test execution at build time (`mvn -q -DskipTests dependency:go-offline` is allowed for cache warming).
  Acceptance: `docker build --target test .` succeeds; the resulting image contains `/workspace/scripts/run-tests-in-container.sh`, runs `mvn -v`, and can resolve the project offline after one warm run.
- [ ] **P2.3 Add the `test` profile services to `docker-compose.yml`.** `test` runner (build target `test`, bind mounts for `reports/junit` and `reports/jacoco`, `depends_on` postgres+backend healthy, cache volume, generated env), plus the `restart` policy override for the app under test and the `postgres` init-script mount (§7.3).
  Acceptance: `docker compose --profile test config` is valid; `docker compose config --services` without the profile still lists only `postgres` and `backend`.
- [ ] **P2.4 Container-side runner** `scripts/run-tests-in-container.sh`: run `mvn -B -ntp verify`, capture its status, copy `target/surefire-reports/*` → `/reports/junit/` and `target/site/jacoco/*` (+ `target/jacoco.exec`) → `/reports/jacoco/`, always, then `exit` with the captured status.
  Acceptance: with an intentionally failing test, both report trees are still populated and the container exits non-zero.
- [ ] **P2.5 Host orchestrator** `scripts/docker-test.sh` per the contract in §7.6: preflight, generated secrets, alternate ports, wipe/regenerate reports, stream logs through `tee`, capture `PIPESTATUS[0]`, cleanup in a trap, summary, faithful exit code.
  Acceptance: every row of the failure matrix (§7.10) behaves as specified.
- [ ] **P2.6 Ignore generated artifacts.** `.gitignore`: `reports/`, and confirm `target/` stays ignored.
  Acceptance: `git status --short` is clean after a workflow run.
- [ ] **P2.7 CI workflow** `.github/workflows/tests.yml` (§7.9): checkout → run `./scripts/docker-test.sh` → upload `reports/junit` and `reports/jacoco` as artifacts (`if: always()`) → publish the JUnit XML as a check result.
  Acceptance: a deliberately broken test on a throwaway branch produces a red check **with** downloadable reports; a green run publishes both artifacts.

### Phase 3 — Backend correctness (all 12 items, all required)

- [ ] **B0 Remove the superseded note.** Delete `REMAINING_WORK.md`; its content lives in `COMPLETION_PLAN.md` and this spec.
  Acceptance: `git grep REMAINING_WORK` returns no dangling references.
- [ ] **B1 Optional pre-check endpoints.** Implement `POST /sessions/{id}/request-entry` and `request-exit` as **validation-only** calls through the same gate state machine (no status mutation, no timestamps), JWT-guarded, ownership-checked.
  Acceptance: `request-entry` on a `CREATED` session returns the same decision the gate would, without changing `sessionStatus`/`entryTimestamp`; on `PARKED` returns `keep_closed` + `ENTRY_ALREADY_USED`; covered by tests.
- [ ] **B2 Grace re-open on the real gate-open time.** Add `gateOpenedAt` (or an equivalent append-only event/audit row) set when exit validation returns `open`; `exit-grace-window` is measured from it, not from `exitTimestamp`.
  Acceptance: paying, waiting longer than the grace window, then validating EXIT still opens exactly once; a second validation after that returns `keep_closed`; both cases tested with an injectable `Clock`.
- [ ] **B3 Facility administration surface.** Expose `PATCH /facilities/{facilityCode}` and `DELETE /facilities/{facilityCode}` (admin key) for the existing service methods; include `PricingPolicy` in the facility read model (`GET /facilities/{facilityCode}`).
  Acceptance: an operator can read current rates and update pricing/status without touching the DB; delete refuses while an active session references the facility; tests cover 200/401/409.
- [ ] **B4 Session history + pagination.** Add `GET /sessions?status=&page=&size=` (own sessions only, newest first), returning the same `SessionDto` shape plus paging metadata.
  Acceptance: the mobile Activity screen's data needs (list + status filter + paging) are met without client-side filtering of a full dump; unauthorized access returns empty/403 per decision in B7.
- [ ] **B5 Device lifecycle.** Add `GET /devices`, `DELETE /devices/{id}`, `POST /devices/{id}/rotate-secret` (admin key; returns the new plaintext secret exactly once, stores only the hash), and a scheduled cleanup of `DELIVERED` commands older than a configurable retention window.
  Acceptance: rotation invalidates the old secret immediately (device auth 401); cleanup is idempotent and tested with an injectable clock; no plaintext secret is ever readable via any GET.
- [ ] **B6 Dead/legacy code.** Remove `client/GateControllerClient.java` (duplicate of `services/interfaces/GateControllerClient`) and one of the two `GateControllerClient` shapes; delete or `@Deprecated` `ParkingSessionService.checkIn` with a removal note.
  Acceptance: no unused duplicate compiles; `mvn verify` green; `docs/api-reference.md` reflects reality.
- [ ] **B7 Validation + HTTP semantics.** Add Jakarta Bean Validation on request DTOs (`@NotNull`, `@NotBlank`, plate format, non-negative rates, valid enum transitions), map violations to `400` in `ApiExceptionHandler`, and decide/mapping for business refusals: double-start → `409`, payment-required → `409`, ownership → `403`, unknown → `404`.
  Acceptance: a malformed body returns `400` with field detail; double-start returns `409` (not `200` with `success=false`); the app's error handling in Phase 4 matches these codes; contract tests assert each.
- [ ] **B8 Concurrency + idempotency.** Enforce D9 with a partial unique index on active sessions (or a transactional lock), and make `POST /sessions/{id}/pay` idempotent via idempotency key (header) plus a unique constraint on (session, request key); a retried pay returns the original receipt.
  Acceptance: a concurrency test (two threads / two connections) creates one session, never two; replaying pay with the same key charges once.
- [ ] **B9 Deployment configuration.** Add `DWARA_ADMIN_KEY` to the deployment env (and `docker compose` test env), document generation and rotation in `docs/provisioning-and-ops.md`; enable CORS only if an Expo web build is used (default: documented decision, configurable property).
  Acceptance: admin endpoints work against the composed stack; `docs/provisioning-and-ops.md` states where the key lives and how to rotate it.
- [ ] **B10 Keep coverage honest.** Every new service line added by B1–B8 gets a Mockito test in the same change, keeping the `verify` gate at 100 %.
  Acceptance: `mvn verify` green without lowering `<minimum>1.00</minimum>`.

### Phase 4 — Mobile app on the real backend

- [ ] **M1 REST client + contract alignment.** Replace `mockApi` with a typed client. Resolve the shape mismatches recorded in `REMAINING_WORK.md` §5:
  - login returns `{ jwt }` (mock expects `{ token, user }`);
  - registration: backend has no `phone` field — drop it from the form or store it later;
  - vehicles: `numberPlate` + `VehicleType` instead of make/model/color;
  - `POST /sessions` returns a session id/UUID, not an object — fetch after create;
  - fee endpoint returns a bare number;
  - `confirmEntry`/`confirmExit` are replaced by gate validation (B1 pre-check endpoints are the app-side mirror).
  Acceptance: every screen that talks to the backend fails loudly with the server's message, no mock fallback in release builds.
- [ ] **M2 JWT persistence + lifecycle.** Store the token in AsyncStorage, attach `Authorization: Bearer`, handle 401 by clearing the token and returning to login.
  Acceptance: killing and relaunching the app keeps the user signed in; an expired token lands on the login screen with a message.
- [ ] **M3 Real BLE discovery.** Implement the `DWARA-<facilityCode>` scanner with `react-native-ble-plx` (foreground only, per v1 scope), facility list → detail via `GET /facilities/{facilityCode}`.
  Acceptance: on a physical iPhone, the gate appears in the list and the detail screen shows live facility data.
- [ ] **M4 Real gate writes.** Send `{ sessionId, intent }` on the gate characteristic and read/notify the status characteristic; replace `mockBle` in all production paths; surface `DENIED` reasons (incl. `PAYMENT_REQUIRED`, `BACKEND_UNREACHABLE`).
  Acceptance: entry and exit are driven from the phone end-to-end against the composed backend and a flashed ESP32.
- [ ] **M5 Session/fee/pay/history screens on real data.** Dashboard active session, live fee polling, Pay & Exit, Activity screen backed by B4.
  Acceptance: fee displayed matches `GET /sessions/{id}/fee`; after payment the exit button becomes available; history lists past sessions with paging.
- [ ] **M6 Typecheck + docs.** `npm --prefix frontend run typecheck` clean; `docs/mobile-app.md` documents screens, state, BLE caveats.
  Acceptance: typecheck exit 0 in CI or as a documented manual step.

### Phase 5 — Firmware completion

- [ ] **F1 Wire the remote-command poller (D11).** Call `BackendClient::pollCommands()` on `COMMAND_POLL_INTERVAL_MS` from the main loop, execute `OPEN`/`KEEP_CLOSED`, never block the BLE/actuator path, and back off on transport failure.
  Acceptance: with the backend up, a queued `OPEN` opens the gate within one poll interval; deleting WiFi mid-poll does not hang the gate; `[gate]` log lines show the poll cadence.
- [ ] **F2 Keep the servo build current.** `ServoGateActuator` stays the active actuator (interview: servo is current); `LedGateActuator` remains legacy-only.
  Acceptance: `config.h`/`dwara-gate.ino` instantiates the servo actuator; `docs/firmware.md` states the same, with no contradictory note anywhere in the repo.
- [ ] **F3 Actuator fault path.** Ensure the stall watchdog reports `DENIED/ACTUATOR_FAULT` and never leaves PWM energised.
  Acceptance: simulated stall (blocked servo) returns the deny reason and releases the signal.
- [ ] **F4 Bench/demo script.** Document the exact flash-and-verify procedure (partition scheme, config values, serial-bridge jumper, poller expectation) in `docs/firmware.md` and `docs/hardware.md`.
  Acceptance: a second person can flash a board and see a gate open from a phone using only the docs.

### Phase 6 — Documentation (all 12 missing §11 files; `firmware.md` updated in Phase 5)

- [ ] **D1–D13** Write, each with Mermaid where a flow exists, and index every one from `docs/README.md`:

  | # | File | Must contain |
  |---|---|---|
  | D1 | `docs/README.md` | Index, system overview diagram, glossary, decision log (D1–D23) |
  | D2 | `docs/system-architecture.md` | Component diagram, monorepo layout (D21), request lifecycles |
  | D3 | `docs/data-model.md` | ER diagram, entities, enums, `PricingPolicy` jsonb, deltas from §4, migration notes (incl. B2/B8 columns) |
  | D4 | `docs/api-reference.md` | Every §5 endpoint with JSON examples, error codes, auth schemes, **bare firmware responses** vs the `data` envelope, and B1/B3/B4/B5 additions |
  | D5 | `docs/auth-and-security.md` | JWT, admin key, device secret scheme, test-only secret policy, threat model (§9) |
  | D6 | `docs/ble-protocol.md` | GATT table + UUIDs, advertisement format, payload schemas, MTU, reconnect rules |
  | D7 | `docs/entry-flow.md` | Discovery → session creation → entry validation, sequence diagram |
  | D8 | `docs/exit-and-payment.md` | Accrual, mock payment, exit validation, receipt, fail-open rule, B2 grace window |
  | D9 | `docs/session-lifecycle.md` | State machine, all §10 edge cases, stale job, cancellation |
  | D10 | `docs/hardware.md` | BOM, MG90S wiring + external 5 V + capacitor, enclosure, push-override |
  | D11 | `docs/mobile-app.md` | Screens, state, BLE integration + iOS caveats (incl. Phase 4 outcome) |
  | D12 | `docs/provisioning-and-ops.md` | Flash-time config (D7), device registration/rotation, remote commands, deployment, **testing** (P1.5), runbook |
  | D13 | `docs/diagrams/` | Any new `.mmd` sources for the diagrams embedded above |

  `docs/firmware.md` already exists and is **updated**, not rewritten, for F1–F4 (poller wired, servo current, fault path, bench procedure); `docs/serial-bridge.md` already exists and needs no work beyond links.

  Acceptance: every link in `docs/README.md` resolves; no doc contradicts the code (specifically: servo is current, poller is wired, envelope vs bare responses, admin key present); each of the 13 §11 rows has a file and each is reviewed against its §11 description.

### Phase 7 — Release and demo readiness

- [ ] **R1 One-command stack.** `docker compose up --build` serves the app on `APP_PORT` with admin endpoints enabled; documented in `docs/provisioning-and-ops.md`.
- [ ] **R2 One-command tests.** `./scripts/docker-test.sh` prints PASSED with counts, coverage %, failing names (none), and report paths.
- [ ] **R3 Demo script.** A numbered end-to-end walkthrough (register → add vehicle → discover facility via BLE → start session → enter → fee → pay → exit → history) with expected screen/response at each step.
  Acceptance: the walkthrough is executed once, start to finish, without a workaround; any deviation becomes a new checklist item, not a footnote.
- [ ] **R4 Final evidence.** Green `verify`, `reports/jacoco/index.html`, the CI run URL, and the demo walkthrough recorded in `COMPLETION_PLAN.md`.

---

## 5. Interview decisions (record)

| Round | Question | Answer | Consequence |
|---|---|---|---|
| R1/Q1 | Relationship of the two halves | One spec, both halves | This file |
| R1/Q2 | Definition of done | Backend + mobile + firmware | Phases 3–5 all in scope |
| R1/Q3 | Driver | Academic submission | Docs and demoability are first-class, not optional |
| R1/Q4 | Fate of `REMAINING_WORK.md` | Superseded | Deleted in B0 |
| R2/Q1 | Test DB | Compose `postgres` + Spring `test` profile | No H2, no Testcontainers |
| R2/Q2 | Green suite required | Yes, fully green | P1.3, no exclusions |
| R2/Q3 | Compose shape | Profile in the existing `docker-compose.yml` | §7.3 |
| R2/Q4 | Report transport | Bind mounts only | No `docker cp` path |
| R3/Q1 | Entrypoint | Shell script | `scripts/docker-test.sh`, no Makefile |
| R3/Q2 | App container role | Readiness gate only | No HTTP smoke test added |
| R3/Q3 | Healthcheck | Spring Actuator | P2.1 |
| R3/Q4 | CI | GitHub Actions | P2.7, §7.9 |
| R4/Q1 | Mobile depth | Everything incl. real BLE | M3, M4 |
| R4/Q2 | Actuator reality | Servo is current | F2; stale note deleted with B0 |
| R4/Q3 | Backlog priority | All required | B1–B10 |
| R4/Q4 | Docs scope | Every §11 deliverable | D1–D13 |
| R5/Q1 | Exit codes | Distinct wrapper codes | §7.6 table |
| R5/Q2 | Reports retention | Wipe + regenerate, gitignored | P2.6 |
| R5/Q3 | Dev stack running | Fail fast, `--force` escape hatch | Preflight step 0 |
| R5/Q4 | Coverage gate | Run `mvn verify` | §7.5 |
| R6/Q1 | Test env values | Generated at runtime | §7.4 |
| R6/Q2 | Maven cache | Named external volume | §7.3 |
| R6/Q3 | Summary content | Counts, coverage, paths, failing names (no file) | §7.6 |
| R6/Q4 | Plan shape | Phased checklist | §4 |

---

## 6. Deliverable 2 — Relationship to Deliverable 1

The workflow in §7 is Phase 2 of the plan; Phase 1 is its prerequisite (you cannot have a meaningful PASS summary while a test errors out). Phase 2's acceptance criteria are written so they can be demonstrated independently of Phases 3–5: the workflow reports the *truth* about the suite, and Phases 3–5 keep that truth green.

---

## 7. Docker Compose test workflow — design

### 7.1 Topology

```
host                                    compose project: dwara-test
┌──────────────────────────────┐       ┌────────────────────────────────────────┐
│ ./scripts/docker-test.sh     │       │  postgres  (healthcheck: pg_isready)   │
│  ├─ preflight                │       │      ▲                                 │
│  ├─ generate env             │──────►│      │ service_healthy                 │
│  ├─ docker compose --profile │       │  backend   (healthcheck: /actuator/     │
│  │     test up --build ...   │       │      │       health)                    │
│  ├─ tee reports/test-output  │       │      ▲ service_healthy                 │
│  ├─ trap → down -v           │       │  test  (maven:3.9.6-temurin-21-alpine) │
│  └─ print summary, exit rc   │       │      │                                 │
└──────────────────────────────┘       │      ├─ bind: ./reports/junit           │
        │                              │      ├─ bind: ./reports/jacoco          │
        └──────────────────────────────┴──────┴─ volume: dwara-maven-cache      │
                                       └────────────────────────────────────────┘
```

- `test` runs **inside** the network: DB host is `postgres`, never `localhost`.
- The `backend` container exists only to prove the real image boots and reports healthy before tests start (R3/Q2).
- Nothing in the test path requires the host's JDK or Maven.

### 7.2 File inventory

| Path | New/Changed | Purpose |
|---|---|---|
| `docker-compose.yml` | changed | `test` profile services, backend healthcheck + restart override, postgres init mount |
| `Dockerfile` | changed | `test` build stage (source baked into the runner image) |
| `scripts/docker-test.sh` | new | host orchestrator: preflight, run, collect, clean, summarise, exit code |
| `scripts/run-tests-in-container.sh` | new | in-container `mvn verify` + unconditional report copy |
| `docker/postgres/init-test-db.sql` | new | creates `dwara_test` |
| `src/test/resources/application-test.properties` | new | test profile DB/JWT/admin values |
| `pom.xml` | changed | actuator; (JaCoCo/surefire config only if §7.7 needs it) |
| `src/main/resources/application.yaml` | changed | actuator exposure |
| `src/main/java/me/hari/dwara/configs/SecurityConfig.java` | changed | permit `/actuator/health` |
| `.github/workflows/tests.yml` | new | CI entrypoint + artifact upload |
| `.gitignore` | changed | `reports/` |
| `COMPLETION_PLAN.md` | new | Phase 0 deliverable |

### 7.3 `docker-compose.yml` changes (sketch)

```yaml
services:
  postgres:
    # ... unchanged ...
    volumes:
      - postgres_data:/var/lib/postgresql/data
      # Also applies to the dev project: harmless, it only adds a second, empty database.
      - ./docker/postgres/init-test-db.sql:/docker-entrypoint-initdb.d/10-test-db.sql:ro

  backend:
    # ... unchanged ...
    restart: ${BACKEND_RESTART_POLICY:-unless-stopped}   # test run sets "no"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 5s
      retries: 30
      start_period: 30s

  test:
    profiles: ["test"]
    build:
      context: .
      target: test
    working_dir: /workspace
    command: ["/workspace/scripts/run-tests-in-container.sh"]
    environment:
      TEST_DB_URL: jdbc:postgresql://postgres:5432/dwara_test
      TEST_DB_USER: ${DB_USER}
      TEST_DB_PASSWORD: ${DB_PASSWORD}
      TEST_JWT_SECRET: ${TEST_JWT_SECRET}
      TEST_JWT_EXPIRATION: ${TEST_JWT_EXPIRATION}
      TEST_ADMIN_KEY: ${TEST_ADMIN_KEY}
      SPRING_PROFILES_ACTIVE: test
    volumes:
      - ./reports/junit:/reports/junit
      - ./reports/jacoco:/reports/jacoco
      - dwara-maven-cache:/root/.m2
    depends_on:
      postgres:
        condition: service_healthy
      backend:
        condition: service_healthy

volumes:
  postgres_data:
  dwara-maven-cache:
    external: true
    name: dwara-maven-cache
```

Design notes:

- **Source is baked into the `test` image**, not bind-mounted. Rationale: a bind-mounted working tree lets the container write `target/` as root on Linux hosts (ownership churn), and it also risks the container reading a stale/half-edited tree in CI. Baking gives reproducibility; only `reports/` is bind-mounted, which is exactly the requirement.
- **`dwara-maven-cache` is `external: true`** so `down -v` (which the cleanup always runs) cannot delete the warm `.m2` cache. The script creates it with `docker volume create` when absent.
- **Profiles:** without `--profile test`, `docker compose config --services` still yields exactly `postgres` and `backend`, so the developer workflow is unchanged.
- **`restart: no` for the test run** is set by exporting `BACKEND_RESTART_POLICY=no`, so a crash-loop surfaces as a health failure instead of hiding.
- **Password/DB values** for the test project come from the same `.env` as dev, but the project name (`dwara-test`) and its own `postgres_data` volume keep data separate.

### 7.4 Test profile and generated values

- `src/test/resources/application-test.properties` (see P1.1) points at `TEST_DB_URL` and uses `create-drop`, so each run starts from an empty schema.
- The host script generates, per run: `TEST_JWT_SECRET` (`openssl rand -hex 32`, with a `docker run`/`od` fallback when `openssl` is absent), `TEST_JWT_EXPIRATION=PT1H`, `TEST_ADMIN_KEY` (`openssl rand -hex 16`).
- These are exported into the compose environment only; nothing is written to disk and nothing is logged.
- The committed defaults in `application-test.properties` exist **only** so `./mvnw test` on a developer machine works after starting `postgres`; `docs/auth-and-security.md` must state that this constant is test-scope and that production reads `JWT_SECRET` from the environment with no default.
- Compose interpolation requires all referenced variables to be defined; the script exports every one, and `docker compose config` is run in preflight to catch a missing variable with a clear message before any container starts.

### 7.5 In-container runner (`scripts/run-tests-in-container.sh`)

```
#!/usr/bin/env sh   # alpine maven image has sh, not bash
set -u
mkdir -p /reports/junit /reports/jacoco
mvn -B -ntp verify            # test phase + JaCoCo check (R5/Q4)
rc=$?
# Always collect, regardless of rc:
cp -a target/surefire-reports/. /reports/junit/   2>/dev/null || true
cp -a target/site/jacoco/.     /reports/jacoco/   2>/dev/null || true
cp -a target/jacoco.exec       /reports/jacoco/   2>/dev/null || true
exit "$rc"
```

Requirements it satisfies:

- `mvn verify` runs `test` first (JUnit 5 + Mockito) and then the JaCoCo `check`, so the 100 % service-line gate is enforced while all JUnit XML is still produced.
- Report collection happens **after** the exit status is captured and **before** exit, so it is independent of test success.
- `set -u` only: `set -e` would abort before the copies on a failing build.
- `-B -ntp` keeps logs parseable (no download progress bar noise).
- No `mvn clean` — the image is built fresh per run, so `target/` is empty at start; adding `clean` would only slow the build.

### 7.6 Host orchestrator (`scripts/docker-test.sh`) — contract

**Shebang/robustness:** `#!/usr/bin/env bash`, `set -uo pipefail` (not `-e`: failures must be classified, not short-circuited).

**Flags**

| Flag | Effect |
|---|---|
| `--keep` | Skip cleanup; print the `docker compose -p dwara-test down -v` command for manual cleanup |
| `--force` | Skip the "dev stack is running" preflight refusal |
| `--no-build` | Skip `--build` (reuse images; faster iteration) |
| `--verbose` | Do not filter Maven log lines |
| `-h/--help` | Usage + exit-code table |

**Steps**

0. **Preflight (all failures → exit 3)**
   - `docker info` succeeds; `docker compose version` present.
   - Validate compose config: `docker compose -p dwara-test --profile test config --quiet` (catches undefined variables).
   - Dev stack check: if `docker compose ps -q --status running` (default project) returns anything, **or** containers named `dwara-postgres`/`dwara-backend` are running (`docker ps --format '{{.Names}}'`), print
     `Dev stack is running (dwara-postgres/dwara-backend). Run 'docker compose down' first, or pass --force.` and exit 3.
     The name check matters because the dev services pin `container_name`, so a differently-named project would still collide.
   - Ports: `APP_PORT=18080`, `DB_PORT=15432` are exported for the test project, so a stray listener cannot collide; if either is occupied, exit 3 with the offending port.
   - `docker volume create dwara-maven-cache >/dev/null 2>&1 || true`.
   - `mkdir -p reports/junit reports/jacoco`, then wipe only those two subtrees (and `reports/test-output.log`) — never `rm -rf reports`.
1. **Generate secrets** (§7.4) and export all compose variables (`DB_*`, `APP_PORT`, `DB_PORT`, `BACKEND_RESTART_POLICY=no`, `TEST_*`). Missing `.env` → exit 3 with "copy .env.example / set DB_USER, DB_PASSWORD, DB_NAME".
2. **Cleanup trap** registered **before** step 3: `trap cleanup EXIT INT TERM`, where `cleanup` (idempotent, `set +e` inside):
   - `docker compose -p dwara-test --profile test down -v --remove-orphans --timeout 30`
   - unless `--keep`: same as above is the only teardown; networks are removed by `down`.
   - preserves `$?` by saving it first and returning it afterwards (a trap must not mask the real status).
   - prints `Cleanup: containers and networks removed.` (or the manual command with `--keep`).
3. **Run** (`set -o pipefail` so the pipeline status is Maven's):

   ```bash
   docker compose -p dwara-test --profile test \
     up --build --abort-on-container-exit --exit-code-from test \
     2>&1 | tee reports/test-output.log
   rc=${PIPESTATUS[0]}
   ```

   - `--abort-on-container-exit` + `--exit-code-from test` makes `up` return the **test container's** status, i.e. Maven's code — this is the "preserve the original test exit code" mechanism.
   - No `-d`: logs stream (the "complete test output") and are simultaneously written to `reports/test-output.log`.
   - `--no-build` drops `--build`.
4. **Verify collection** — for each of `reports/junit/TEST-*.xml` and `reports/jacoco/jacoco.csv`: if absent and `rc -eq 0`, treat as a collection failure → `rc=4`, explain that the bind mount or report path is broken. If absent and `rc != 0`, keep `rc` and print a warning (a compile error legitimately produces no XML).
5. **Summary** (stdout; not written to a file, per R6/Q3), parsed with `awk` from `reports/junit/TEST-*.xml` (tests/failures/errors/skipped, plus names of failing/erroring `testcase`s) and from `reports/jacoco/jacoco.csv` (sum `LINE_COVERED`/`LINE_MISSED` over `me.hari.dwara.services` rows → percentage and `321/321`-style fraction):

   ```
   ==========================================================
    Dwara Docker test run
   ----------------------------------------------------------
    Result    : PASSED
    Tests     : 118 run, 0 failures, 0 errors, 0 skipped
    Coverage  : services line 100.00% (338/338) — gate PASSED
    Failing   : none
    Duration  : 94s
    JUnit     : reports/junit/            (reports/junit/TEST-*.xml)
    JaCoCo    : reports/jacoco/           (reports/jacoco/index.html)
    Log       : reports/test-output.log
    Exit code : 0
   ==========================================================
   ```

   On failure the same block shows `FAILED`, the failing test names (up to 20, then `… and N more`), and the exit code.
6. **Exit** with `rc`, untouched by the trap.

**Exit-code table (documented in `--help` and `docs/provisioning-and-ops.md`)**

| Code | Meaning | Source |
|---|---|---|
| 0 | Tests passed; coverage gate met; reports collected | Maven |
| 1 (or Maven's own code) | Test/verify failure — passed through verbatim | Maven |
| 2 | Build / orchestration error (image build failed, compose config invalid) | wrapper |
| 3 | Preflight or health failure (Docker down, dev stack running, port taken, service never healthy, missing env) | wrapper |
| 4 | Report collection failure (expected artifacts missing after a nominally successful run) | wrapper |

Maven's code is never remapped: `1` stays `1`, and an unusual Maven code (e.g. `137` from an OOM kill) also passes through. Wrapper codes are disjoint from Maven's normal `1` so CI can tell infrastructure apart from a red suite.

### 7.7 Reports layout

```
reports/
├── junit/                      # bind-mounted to the test container
│   ├── TEST-me.hari.dwara.services.UserServiceTest.xml
│   ├── me.hari.dwara.services.UserServiceTest.txt
│   └── ...                     # one XML + one TXT per test class
├── jacoco/                     # bind-mounted to the test container
│   ├── index.html
│   ├── jacoco.csv
│   ├── jacoco.xml
│   ├── jacoco.exec
│   └── me.hari.dwara.services/...
└── test-output.log             # host-side tee of the container stream
```

- JUnit XML is the machine-readable artifact (CI JUnit publishers consume `TEST-*.xml`); the `.txt` summaries are kept because they are what a person reads first.
- JaCoCo's `report-services` execution is scoped to `me/hari/dwara/services/**`, so `jacoco.csv` contains only service rows — the summary's coverage percentage is therefore the *gated* number, not a diluted project-wide one.
- `reports/` is gitignored (R5/Q2) and recreated by every run; nothing else may write there.
- If a future CI provider needs a single archive, `tar -C reports -czf reports.tar.gz .` is a one-line addition — out of scope now.

### 7.8 `.gitignore` additions

```gitignore
# Test workflow artifacts (regenerated by scripts/docker-test.sh)
reports/
```

`target/` is already ignored; ensure the containers never write into it (they don't — baked source, in-container `target/`).

### 7.9 CI — `.github/workflows/tests.yml`

```yaml
name: tests
on:
  push:
    branches: [main, finish-backend]
  pull_request:

jobs:
  docker-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - name: Run Docker Compose test workflow
        run: ./scripts/docker-test.sh
      - name: Upload JUnit results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: junit-results
          path: reports/junit/
      - name: Upload JaCoCo coverage
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-coverage
          path: reports/jacoco/
      - name: Publish test report
        if: always()
        uses: mikepenz/action-junit-report@v4
        with:
          report_paths: reports/junit/TEST-*.xml
          check_name: JUnit
```

Notes:

- The script is the only entrypoint, so local and CI behavior cannot drift.
- `if: always()` on every artifact step is the CI analogue of the cleanup/report requirement: reports survive a red suite.
- The job fails because the last script step's exit code is the job's exit code — no `continue-on-error`.
- The named Maven cache volume is empty on a fresh runner, so CI pays a cold dependency download once per job. This is accepted (R6/Q2); a follow-up may bind-mount `~/.m2` when `CI=true`, and the spec deliberately does not do that now.
- No repo secrets are needed: all test values are generated in-run.

### 7.10 Failure matrix (acceptance tests for the workflow)

Each row is a concrete check to perform during P2.5 sign-off.

| # | Injected condition | Expected observable behavior |
|---|---|---|
| 1 | Normal run | Exit 0; summary `PASSED`; XML + HTML present; `docker compose -p dwara-test ps -a` empty afterwards; `docker network ls` has no `dwara-test` network |
| 2 | One assertion intentionally broken | Exit 1 (Maven's code); summary `FAILED` listing the test name; both report trees still populated; containers still removed |
| 3 | Compile error in `src/main` | Exit 1; no `reports/junit` XML; warning instead of exit 4; teardown still runs |
| 4 | `DB_PASSWORD` unset in `.env` | Exit 3 with a message naming the variable; no container starts |
| 5 | `backend` healthcheck forced to fail (bad `SPRING_DATASOURCE_URL`) | Exit 3 after the health timeout; app + postgres removed; message names the unhealthy service |
| 6 | Dev stack already up | Exit 3, immediate, message instructs `docker compose down` or `--force`; nothing is touched |
| 7 | `Ctrl-C` mid-run | Trap fires: containers/networks removed, exit code 130, no orphan containers |
| 8 | `reports/junit` deleted/not writable | Exit 4 on an otherwise green run with an explicit bind-mount message |
| 9 | Coverage regression (delete a service test) | Exit 1 from the JaCoCo `check`; reports still collected; summary shows the real coverage number |
| 10 | `--keep` | Containers remain; printed teardown command removes them |
| 11 | Second consecutive run | Second run reuses the warm `dwara-maven-cache` and the previous `reports/` is fully replaced, not merged |
| 12 | CI run on a PR | Green check + two downloadable artifacts; a red PR still produces both artifacts |

### 7.11 Security and hygiene notes

- No secrets in git: test values are generated per run; the only committed credential-like string is the explicitly-labelled test-scope JWT default in `application-test.properties` (P1.4 and `docs/auth-and-security.md`).
- `reports/test-output.log` may contain request/response bodies from failing integration tests; it is gitignored, and CI artifacts should be treated as internal.
- The test project publishes `APP_PORT=18080`/`DB_PORT=15432` on the host. If that is undesirable later, `ports` can be made conditional via `PROFILES`-scoped overrides; the current choice keeps the file simple and requires no host-network tricks.
- `--force` bypasses only the dev-stack check; it never bypasses health waiting or cleanup.

---

## 8. Non-goals / explicitly deferred

- Real payment gateway (D10 stays mock).
- Testcontainers, H2, or any second database strategy (R2/Q1).
- `docker cp`-based report transport, tarball artifacts, per-run report history (R2/Q4, R5/Q2).
- Makefile aliases, npm-script wrappers, or a Windows `.bat`/PowerShell runner (R3/Q1; Git Bash covers the supported shell).
- Publishing images to a registry, deployment automation, or CD.
- iOS background BLE, OTA firmware updates, heartbeats (architecture spec §13).
- ADMIN user role or operator web console.
- Multi-gate facilities, capacity tracking.

---

## 9. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| B2 (grace timestamp) requires a schema change | Entity/repository churn, coverage gate pressure | Add the column with `ddl-auto=update`, keep service logic behind an injectable `Clock`, test both boundaries |
| B8 concurrency constraints differ between Postgres and test profile | Green locally, red in CI (or vice versa) | Both run Postgres 16 (compose and CI use the same image); no H2 anywhere |
| Baked-source test image slows iteration | Developer friction | `--no-build` flag; warm `dwara-maven-cache` volume; rebuild is a layer-cached copy + compile |
| Docker volume permission issues on Linux hosts | Reports unreadable/root-owned | Only `reports/` is written from the container; documented `sudo chown -R "$USER" reports` note; CI unaffected |
| Actuator endpoint widens the attack surface | Info leak | Only `health` exposed, `show-details: never`, permitted unauthenticated, everything else authenticated |
| Mobile work needs a physical iPhone + BLE | Phase 4 may stall | Phase order lets backend/tests/docs finish first; `docs/mobile-app.md` records the simulator limitation |
| Firmware poller changes can regress the gate | Demo failure | Poller is non-blocking and additive; F1 acceptance includes a transport-failure case |
| 100 % coverage gate blocks fast iteration during B1–B8 | Red builds if tests lag | B10 makes the test part of each item, not a cleanup pass |

---

## 10. End-to-end verification plan

Run, in order, after implementation:

1. `docker compose --profile test config --quiet` → valid, and `docker compose config --services` without the profile lists only `postgres`/`backend`.
2. `./scripts/docker-test.sh` → exit 0, `PASSED`, counts match `target`-equivalent output, coverage `100.00%`, both report paths exist, `reports/jacoco/index.html` opens.
3. `grep -c '<testcase' reports/junit/TEST-*.xml | ...` matches the summary count (proves the summary is parsed, not hard-coded).
4. Failure matrix rows 2, 4, 5, 6, 7, 9 (§7.10) — each produces the specified code and behavior.
5. `docker compose -p dwara-test ps -a` → empty; `docker network ls | grep dwara-test` → empty.
6. `git status --short` → clean after a run.
7. `./mvnw -B -ntp verify` on the host with `postgres` up → identical green result, proving the workflow is not the only way to be green.
8. Demo walkthrough (R3) end-to-end with phone + flashed ESP32.

---

## 11. Open items requiring human confirmation

1. **Hardware access.** F1/F3/F4 need a flashed ESP32 and a powered MG90S. If the servo is still unreliable in practice, the plan's F2 statement ("servo is current") must be revisited *before* the docs are finalized — the decision recorded here comes from the interview, not from a bench test.
2. **iPhone availability for M3/M4.** Without a physical device, Phase 4 can only be verified on a simulator with mocked BLE; `docs/mobile-app.md` must then say so.
3. **GitHub repository settings** for the CI phase (Actions enabled, artifact retention). No repo secrets are required.
4. **`mvn verify` vs the literal request.** The workflow runs `mvn verify` (tests **and** the coverage gate) per R5/Q4; the request said `mvn test`. `verify` runs `test`, so both readings are satisfied, and the docs will say so explicitly rather than silently diverging.

---

## 12. Appendix — requirement traceability (from the original request)

| Requirement | Where addressed |
|---|---|
| Build required Docker images | §7.3 `test` build target, P2.2; dev image unchanged |
| Start the application and its dependencies | §7.3 `depends_on: service_healthy` for `postgres` + `backend` |
| Wait for services to become healthy | §7.3 healthchecks (actuator + `pg_isready`), preflight exit 3 |
| Run `mvn test` with JUnit + Mockito + JaCoCo | §7.5 `mvn verify`, §7.4 test profile, existing JaCoCo config |
| Capture complete test output and exit code | §7.6 step 3 (`tee` + `PIPESTATUS[0]`), `reports/test-output.log` |
| Generate JUnit result + JaCoCo coverage reports | §7.5 copy step, §7.7 layout |
| Copy all reports back to the repo | §7.3 bind mounts `./reports/junit`, `./reports/jacoco` (R2/Q4) |
| Reports survive container exit | Bind mounts (host-owned files, container-independent) |
| Stop and remove containers and networks on failure | §7.6 step 2 trap → `down -v --remove-orphans`, matrix rows 2, 7 |
| Preserve original test exit code | §7.6 `--exit-code-from test` + `PIPESTATUS`, exit table |
| Print final summary of pass/fail and report locations | §7.6 step 5, contents per R6/Q3 |
| Collection and cleanup regardless of outcome | §7.5 unconditional copies; §7.6 EXIT/INT/TERM trap |
| Suitable for local dev and CI/CD | §7.6 flags (`--keep`, `--no-build`, `--force`), §7.9 GitHub Actions, matrix row 12 |
