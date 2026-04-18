#!/bin/bash
# run_tests.sh — Eagle Point Console test orchestrator.
#
# Responsibilities (ONLY orchestration — no assertions):
#   1. Wait for the running `task-133-app` container's /api/health to succeed.
#   2. Build the `tests` stage of `app/Dockerfile`.
#   3. Run the full Maven test suite (JUnit 5 unit tests + REST-Assured
#      integration tests + LiveAppSmokeTest against the live container)
#      inside the test container.
#   4. Exit 0 when Maven reports all tests passed, non-zero on any failure.
#
# All API assertions live in Java test files under `repo/tests/java/...` —
# this script does not duplicate them.
#
# Required host tools: docker, docker compose, bash, curl.

set -euo pipefail

BACKEND_URL="http://localhost:8080"
MAX_WAIT=120
INTERVAL=5

echo "=== Eagle Point Console — Test Runner ==="
echo ""

# ─── 1. Start app if not running, then wait for healthy ─────────────────────
if ! curl -sf "${BACKEND_URL}/api/health" > /dev/null 2>&1; then
    echo "App not running — starting with docker compose up -d..."
    docker compose up -d
    echo ""
fi

echo "Waiting for app to be healthy (max ${MAX_WAIT}s)..."
elapsed=0
until curl -sf "${BACKEND_URL}/api/health" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
        echo "ERROR: App not healthy after ${MAX_WAIT}s"
        docker compose logs app | tail -80
        exit 1
    fi
    sleep "$INTERVAL"
    elapsed=$((elapsed + INTERVAL))
    echo "  Waiting... ($elapsed/${MAX_WAIT}s)"
done
echo "App is healthy."
echo ""

# ─── 2. Build the tests image ────────────────────────────────────────────────
echo "Building test image (target=tests)..."
docker build --target tests -t task-133-app-tests -f app/Dockerfile . >/dev/null

# ─── 3. Run the Maven test suite inside Docker ───────────────────────────────
# LiveAppSmokeTest reads LIVE_APP_URL and hits the compose container via
# host-gateway networking.  The flag is Linux-required and no-op on Desktop.
echo "Running Maven tests (unit + embedded integration + live smoke)..."
if ! docker run --rm \
        --add-host=host.docker.internal:host-gateway \
        -e LIVE_APP_URL=http://host.docker.internal:8080 \
        task-133-app-tests; then
    echo ""
    echo "FAIL: Maven test suite reported failures."
    exit 1
fi

echo ""
echo "=== All tests passed ==="
exit 0
