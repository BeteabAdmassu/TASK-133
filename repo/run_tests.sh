#!/bin/bash
# run_tests.sh — Eagle Point Console test orchestrator
# Runs on the HOST; executes tests INSIDE Docker containers.
# Exits 0 on all tests passing, non-zero on any failure.

set -euo pipefail

BACKEND_URL="http://localhost:8080"
MAX_WAIT=120
INTERVAL=5

echo "=== Eagle Point Console — Test Runner ==="
echo ""

# ---- Wait for app health ----
echo "Waiting for app to be healthy (max ${MAX_WAIT}s)..."
elapsed=0
until curl -sf "${BACKEND_URL}/api/health" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
        echo "ERROR: App not healthy after ${MAX_WAIT}s"
        docker compose logs app | tail -50
        exit 1
    fi
    sleep "$INTERVAL"
    elapsed=$((elapsed + INTERVAL))
    echo "  Waiting... ($elapsed/${MAX_WAIT}s)"
done

echo "App is healthy!"
echo ""

# ---- Verify health endpoint returns expected fields ----
HEALTH=$(curl -sf "${BACKEND_URL}/api/health")
echo "Health check response: $HEALTH"
if ! echo "$HEALTH" | grep -q '"status"'; then
    echo "FAIL: Health response missing 'status' field"
    exit 1
fi
echo "PASS: Health endpoint"
echo ""

# ---- Run unit + integration tests inside a test-profile container ----
echo "Building test image (target=tests)..."
docker build --target tests -t task-133-app-tests ./app >/dev/null
echo "Running Maven tests inside test container..."
if ! docker run --rm task-133-app-tests; then
    echo "FAIL: Maven tests failed"
    exit 1
fi
echo "PASS: Maven unit/integration tests"
echo ""

echo "=== API Smoke Tests ==="

# ---- Login ----
echo "Test: POST /api/auth/login"
LOGIN_RESP=$(curl -sf -X POST "${BACKEND_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"Admin1234!"}')

TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
    echo "FAIL: Login did not return a token. Response: $LOGIN_RESP"
    exit 1
fi
echo "PASS: Login — token obtained"

# ---- Get current user ----
echo "Test: GET /api/auth/me"
ME_RESP=$(curl -sf "${BACKEND_URL}/api/auth/me" -H "Authorization: Bearer $TOKEN")
if ! echo "$ME_RESP" | grep -q '"username":"admin"'; then
    echo "FAIL: /api/auth/me response unexpected: $ME_RESP"
    exit 1
fi
echo "PASS: GET /api/auth/me"

# ---- List communities ----
echo "Test: GET /api/communities"
COMM_RESP=$(curl -sf "${BACKEND_URL}/api/communities" -H "Authorization: Bearer $TOKEN")
if ! echo "$COMM_RESP" | grep -q '"data"'; then
    echo "FAIL: /api/communities response missing 'data' field: $COMM_RESP"
    exit 1
fi
echo "PASS: GET /api/communities"

# ---- Create community ----
echo "Test: POST /api/communities"
CREATE_RESP=$(curl -sf -X POST "${BACKEND_URL}/api/communities" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"Smoke Test Community","description":"Created by run_tests.sh"}')
if ! echo "$CREATE_RESP" | grep -q '"Smoke Test Community"'; then
    echo "FAIL: Create community response unexpected: $CREATE_RESP"
    exit 1
fi
COMM_ID=$(echo "$CREATE_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
echo "PASS: POST /api/communities — created id=$COMM_ID"

# ---- Get community ----
echo "Test: GET /api/communities/$COMM_ID"
GET_RESP=$(curl -sf "${BACKEND_URL}/api/communities/$COMM_ID" -H "Authorization: Bearer $TOKEN")
if ! echo "$GET_RESP" | grep -q '"Smoke Test Community"'; then
    echo "FAIL: GET community response unexpected: $GET_RESP"
    exit 1
fi
echo "PASS: GET /api/communities/$COMM_ID"

# ---- List service areas ----
echo "Test: GET /api/service-areas"
SA_RESP=$(curl -sf "${BACKEND_URL}/api/service-areas" -H "Authorization: Bearer $TOKEN")
if ! echo "$SA_RESP" | grep -q '"data"'; then
    echo "FAIL: /api/service-areas missing 'data': $SA_RESP"
    exit 1
fi
echo "PASS: GET /api/service-areas"

# ---- List beds ----
echo "Test: GET /api/beds"
BED_RESP=$(curl -sf "${BACKEND_URL}/api/beds" -H "Authorization: Bearer $TOKEN")
if ! echo "$BED_RESP" | grep -q '"data"'; then
    echo "FAIL: /api/beds missing 'data': $BED_RESP"
    exit 1
fi
echo "PASS: GET /api/beds"

# ---- Unauthorized access ----
echo "Test: GET /api/communities without token returns 401"
UNAUTH=$(curl -s -o /dev/null -w "%{http_code}" "${BACKEND_URL}/api/communities")
if [ "$UNAUTH" != "401" ]; then
    echo "FAIL: Expected 401, got $UNAUTH"
    exit 1
fi
echo "PASS: Unauthorized returns 401"

# ---- Logout ----
echo "Test: POST /api/auth/logout"
LOGOUT=$(curl -sf -X POST "${BACKEND_URL}/api/auth/logout" -H "Authorization: Bearer $TOKEN")
echo "PASS: Logout succeeded"

echo ""
echo "=== All tests passed ==="
exit 0
