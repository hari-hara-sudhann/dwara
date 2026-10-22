#!/usr/bin/env bash
#
# One-command local test workflow for Dwara.
#
#   ./scripts/docker-test.sh
#
# Builds the images, starts Postgres + the backend (readiness gate) + the test
# runner, runs `mvn verify` (JUnit + JaCoCo gate) inside the runner, and writes
# reports into ./reports. Containers and networks created for the run are
# removed afterwards; the normal development stack and its volumes are never
# touched (a separate compose project, names and ports are used).
#
# Exit codes:
#   0           tests passed and reports were collected
#   1 (or n)    Maven's own exit code, passed through untouched
#   2           build/orchestration error
#   3           preflight or service-health failure
#   4           reports missing after a nominally successful run
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROJECT="dwara-test"
REPORTS="$ROOT/reports"
JUNIT_DIR="$REPORTS/junit"
JACOCO_DIR="$REPORTS/jacoco"
LOG="$REPORTS/test-output.log"

KEEP=0
FORCE=0
BUILD=1

usage() {
  cat <<'EOF'
Usage: ./scripts/docker-test.sh [options]

Options:
  --keep       Do not remove the test containers (prints the cleanup command)
  --force      Run even if the development stack is up
  --no-build   Reuse existing images instead of rebuilding
  -h, --help   Show this help

Exit codes:
  0  passed, coverage gate met, reports collected
  1  test or coverage failure (Maven's code, passed through)
  2  image build / compose configuration error
  3  preflight failure (Docker down, dev stack running, port taken, unhealthy)
  4  report collection failure
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --keep) KEEP=1 ;;
    --force) FORCE=1 ;;
    --no-build) BUILD=0 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 3 ;;
  esac
  shift
done

log() { printf '%s\n' "$*"; }
fail3() { echo "ERROR: $*" >&2; exit 3; }

# ---------------------------------------------------------------- preflight --

command -v docker >/dev/null 2>&1 || fail3 "docker is not installed or not on PATH."
docker info >/dev/null 2>&1 || fail3 "Docker daemon is not reachable. Start Docker Desktop and retry."
docker compose version >/dev/null 2>&1 || fail3 "docker compose (v2) is required."

[ -f .env ] || fail3 ".env is missing. Copy the documented values (DB_USER, DB_PASSWORD, DB_NAME, JWT_*) into .env first."

# Generated per run; nothing is written to disk.
TEST_JWT_SECRET="$(openssl rand -base64 48 2>/dev/null | tr -d '\n' || true)"
[ -n "$TEST_JWT_SECRET" ] || TEST_JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
TEST_JWT_EXPIRATION=3600000
TEST_ADMIN_KEY="$(openssl rand -hex 16 2>/dev/null || head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"

# Alternate ports and container names: the test project can coexist with the
# development stack, and a stray listener cannot collide.
export APP_PORT=18080
export DB_PORT=15432
export POSTGRES_CONTAINER_NAME=dwara-test-postgres
export BACKEND_CONTAINER_NAME=dwara-test-backend
export TEST_CONTAINER_NAME=dwara-test-runner
export BACKEND_RESTART_POLICY=no
export TEST_JWT_SECRET TEST_JWT_EXPIRATION TEST_ADMIN_KEY

if [ "$FORCE" -eq 0 ] && docker ps --format '{{.Names}}' | grep -qxE 'dwara-postgres|dwara-backend'; then
  fail3 "The development stack is running (dwara-postgres/dwara-backend). Run 'docker compose down' first, or pass --force."
fi

for port in "$APP_PORT" "$DB_PORT"; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
    exec 3>&- 2>/dev/null || true
    fail3 "Port $port is already in use; stop the process or free the port."
  fi
done

if ! docker compose -p "$PROJECT" --profile test config --quiet; then
  exit 3
fi

docker volume create dwara-maven-cache >/dev/null 2>&1 || true

mkdir -p "$JUNIT_DIR" "$JACOCO_DIR"
rm -rf "${JUNIT_DIR:?}"/* "${JACOCO_DIR:?}"/*
rm -f "$LOG"

# ------------------------------------------------------------------ cleanup --

cleanup() {
  local status=$?
  set +e
  if [ "$KEEP" -eq 1 ]; then
    log ""
    log "Cleanup: containers kept. Remove them with:"
    log "  docker compose -p $PROJECT --profile test down -v"
  else
    docker compose -p "$PROJECT" --profile test down -v --remove-orphans --timeout 30 >/dev/null 2>&1
    log "Cleanup: test containers and networks removed."
  fi
  return "$status"
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------------- run --

log "==> Building and starting the test stack (this can take a few minutes on the first run)"
start=$SECONDS

COMPOSE_CMD=(docker compose -p "$PROJECT" --profile test up --abort-on-container-exit --exit-code-from test)
if [ "$BUILD" -eq 1 ]; then
  COMPOSE_CMD+=(--build)
fi

"${COMPOSE_CMD[@]}" 2>&1 | tee "$LOG"
rc=${PIPESTATUS[0]}

duration=$(( SECONDS - start ))

# ------------------------------------------------------------ classification --

if [ "$rc" -ne 0 ]; then
  if grep -qE 'dependency failed to start|is unhealthy|container .* is unhealthy' "$LOG"; then
    log "ERROR: the backend or Postgres never became healthy; see $LOG" >&2
    rc=3
  elif grep -qE 'failed to solve|failed to build|failed to compute cache key' "$LOG"; then
    log "ERROR: image build failed; see $LOG" >&2
    rc=2
  fi
fi

junit_xml=("$JUNIT_DIR"/TEST-*.xml)
if [ ! -e "${junit_xml[0]}" ] && [ ! -f "$JACOCO_DIR/jacoco.csv" ]; then
  if [ "$rc" -eq 0 ]; then
    log "ERROR: tests reported success but no reports were collected. Check the reports bind mount." >&2
    rc=4
  else
    log "WARNING: no JUnit XML was produced (compile/test discovery failure?)." >&2
  fi
fi

# -------------------------------------------------------------------- summary --

attr_sum() {
  grep -h '<testsuite ' "$JUNIT_DIR"/TEST-*.xml 2>/dev/null \
    | grep -o "$1=\"[0-9]*\"" \
    | sed 's/.*="//;s/"//' \
    | awk '{s+=$1} END{printf "%d", s+0}'
}

tests=$(attr_sum tests)
failures=$(attr_sum failures)
errors=$(attr_sum errors)
skipped=$(attr_sum skipped)

failing=$(awk '
  /<testcase / {
    name=""; cls=""
    if (match($0, /name="[^"]*"/))      name=substr($0, RSTART+6,  RLENGTH-7)
    if (match($0, /classname="[^"]*"/)) cls=substr($0, RSTART+11, RLENGTH-12)
    cur = (cls == "" ? name : cls "." name)
  }
  /<(failure|error)[ >]/ { if (cur != "") { print cur; cur="" } }
' "$JUNIT_DIR"/TEST-*.xml 2>/dev/null)

coverage_line="n/a"
if [ -f "$JACOCO_DIR/jacoco.csv" ]; then
  read -r covered missed pct <<<"$(awk -F, '
    NR > 1 && $2 ~ /^me\.hari\.dwara\.services/ {
      lm += $8; lc += $9
    }
    END {
      total = lm + lc
      p = (total > 0) ? (100 * lc / total) : 0
      printf "%d %d %.2f", lc, lm, p
    }' "$JACOCO_DIR/jacoco.csv")"
  total=$(( covered + missed ))
  coverage_line="services line ${pct}% (${covered}/${total})"
fi

status="PASSED"
if [ "$rc" -ne 0 ]; then
  status="FAILED"
fi

log ""
log "=========================================================="
log " Dwara Docker test run"
log "----------------------------------------------------------"
log " Result    : $status"
log " Tests     : ${tests} run, ${failures} failures, ${errors} errors, ${skipped} skipped"
log " Coverage  : ${coverage_line}"
if [ -n "$failing" ]; then
  count=$(printf '%s\n' "$failing" | wc -l | tr -d ' ')
  log " Failing   : $(printf '%s\n' "$failing" | head -20 | paste -sd ', ' -)"
  if [ "$count" -gt 20 ]; then
    log "             ... and $((count - 20)) more"
  fi
else
  log " Failing   : none"
fi
log " Duration  : ${duration}s"
log " JUnit     : reports/junit/"
log " JaCoCo    : reports/jacoco/"
log " Log       : reports/test-output.log"
log " Exit code : $rc"
log "=========================================================="
log ""

exit "$rc"
