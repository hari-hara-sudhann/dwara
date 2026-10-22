#!/usr/bin/env sh
# Runs inside the `test` container (Dockerfile target `test`).
#
# Always copies the reports out before exiting, even when the build fails, so
# the host still gets JUnit XML and JaCoCo data for a red suite. No `set -e`:
# the build failure must be captured, not short-circuited.
set -u

mkdir -p /reports/junit /reports/jacoco

echo "[dwara-test] mvn -B -ntp verify"
mvn -B -ntp verify
rc=$?

# A test failure aborts the lifecycle before the `test`-phase JaCoCo report
# goal, so generate the coverage report from the collected exec data anyway.
if [ ! -f target/site/jacoco/jacoco.csv ] && [ -f target/jacoco.exec ]; then
  mvn -B -ntp -q jacoco:report@report-services >/dev/null 2>&1 || true
fi

cp -a target/surefire-reports/. /reports/junit/ 2>/dev/null || true
cp -a target/site/jacoco/. /reports/jacoco/ 2>/dev/null || true
cp -a target/jacoco.exec /reports/jacoco/ 2>/dev/null || true

echo "[dwara-test] maven exit code: ${rc}"
exit "$rc"
