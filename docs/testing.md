# Local Testing

Everything runs in Docker. There is no CI pipeline and no host Maven/JDK
requirement.

## One command

```bash
./scripts/docker-test.sh            # full run
./scripts/docker-test.sh --no-build # reuse images while iterating
./scripts/docker-test.sh --keep     # leave containers for inspection
./scripts/docker-test.sh --force    # run while the dev stack is up
```

Prerequisites: Docker with Compose v2 (`docker compose version` works), `bash`,
and a `.env` in the repository root (the same one the dev stack uses).

## What it does

1. Preflight: Docker reachable, `.env` present, compose config valid, dev stack
   not running (unless `--force`), test ports `18080`/`15432` free.
   It never stops or removes the development containers or the `postgres_data`
   volume.
2. Builds the images (`Dockerfile` targets `runtime` and `test`).
3. Starts Postgres, waits for it to be healthy, starts the real backend image
   and waits for `/actuator/health`. The backend is only a readiness gate —
   proof that the deployment configuration boots.
4. Runs `mvn -B -ntp verify` inside the `test` container: JUnit 5 + Mockito,
   then the JaCoCo check (100 % line coverage of `me.hari.dwara.services`).
5. Copies reports to the host **even when tests fail** (bind mounts,
   `./reports/junit` and `./reports/jacoco`).
6. Stops and removes only the containers/network of the `dwara-test` compose
   project, then prints a summary.

## Output

```
reports/
├── junit/            TEST-<class>.xml + .txt per test class
├── jacoco/           index.html, jacoco.csv, jacoco.xml, jacoco.exec
└── test-output.log   full container log
```

`reports/` is gitignored and wiped at the start of every run. The summary prints
the pass/fail status, test counts, failing test names, service line coverage and
the report locations.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Tests passed, coverage gate met, reports collected |
| 1 (or Maven's own code) | Test or coverage failure, passed through unchanged |
| 2 | Image build / compose configuration error |
| 3 | Preflight or health failure (Docker down, dev stack running, port taken, unhealthy service) |
| 4 | Reports missing after a nominally successful run |

## Without Docker

With a Postgres reachable on `localhost:5433` holding `dwara_test`:

```bash
docker run -d --name dwara-test-db \
  -e POSTGRES_USER=dwara_user -e POSTGRES_PASSWORD=dwara_password \
  -e POSTGRES_DB=dwara_test -p 5433:5432 postgres:16-alpine
./mvnw verify
```

The `test` profile (`src/test/resources/application-test.properties`) reads
`TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`, `TEST_JWT_SECRET`,
`TEST_JWT_EXPIRATION` and `TEST_ADMIN_KEY`, and defaults them to those local
values. The committed JWT value is test-scope only; production reads
`JWT_SECRET` from the environment with no default.

Mobile type check:

```bash
npm --prefix frontend run typecheck
```

Firmware compile (needs the ESP32 core, `ArduinoJson` and `ESP32Servo`):

```bash
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app firmware/dwara-gate
```
