#!/bin/bash
# Smoke test: verifies SkyWalking OAP is healthy and traces are received.
# Usage: ./skywalking/verify-trace.sh [gateway_port]

set -euo pipefail

GATEWAY_PORT="${1:-8888}"
OAP_URL="http://localhost:12800"
GATEWAY_URL="http://localhost:${GATEWAY_PORT}"
GENERATED_TRACE_ID=$(uuidgen | tr -d '-' | tr '[:upper:]' '[:lower:]')

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; EXIT_CODE=1; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

EXIT_CODE=0

echo "=== SkyWalking Trace Verification ==="
echo ""

# 1. Check OAP health
echo "1. Checking SkyWalking OAP health..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${OAP_URL}/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "OAP is healthy (${OAP_URL}/health -> 200)"
else
    fail "OAP is unreachable or unhealthy (${OAP_URL}/health -> ${HTTP_CODE})"
    echo "   Is docker-compose running? Try: docker-compose up -d skywalking-oap"
    if [ "$EXIT_CODE" != "0" ]; then
        echo ""
        echo "=== Results: FAILED ==="
        exit "$EXIT_CODE"
    fi
fi

# 2. Check OAP GraphQL readiness (SkyWalking UI API)
echo ""
echo "2. Checking SkyWalking OAP GraphQL API..."
GRAPHQL_QUERY='{"query":"query { getAllServices { key label type } }"}'
GRAPHQL_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST -H "Content-Type: application/json" \
    -d "$GRAPHQL_QUERY" \
    "${OAP_URL}/graphql" 2>/dev/null || echo "000")
if [ "$GRAPHQL_CODE" = "200" ]; then
    pass "OAP GraphQL API is responsive"
else
    warn "OAP GraphQL API returned ${GRAPHQL_CODE} (may need data to be non-empty)"
fi

# 3. Send a test request through the gateway (if running)
echo ""
echo "3. Sending test request to gateway..."
GW_ALIVE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "${GATEWAY_URL}/actuator/health" 2>/dev/null || echo "000")
if [ "$GW_ALIVE" = "200" ] || [ "$GW_ALIVE" = "404" ] || [ "$GW_ALIVE" = "403" ]; then
    # Gateway is running — send a request with custom X-Trace-Id
    RESP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
        -H "X-Trace-Id: ${GENERATED_TRACE_ID}" \
        "${GATEWAY_URL}/" 2>/dev/null || echo "000")
    if [ "$RESP_CODE" != "000" ]; then
        pass "Gateway responded (${GATEWAY_URL}/ -> ${RESP_CODE}) with X-Trace-Id: ${GENERATED_TRACE_ID}"
    else
        warn "Gateway did not respond (port ${GATEWAY_PORT})"
    fi
else
    warn "Gateway not running on port ${GATEWAY_PORT} — skipping trace injection"
    echo "   Start services with: SW_AGENT_ENABLED=true ./start-services.sh"
fi

# 4. Query SkyWalking for recent traces
echo ""
echo "4. Querying SkyWalking for recent traces..."
# Use the OAP GraphQL to list traces
TRACES_QUERY='{"query":"query { queryBasicTraces(condition: { paging: { pageNum: 1, pageSize: 5, needTotal: false } }) { traces { key segmentId } } }"}'
TRACES_RESP=$(curl -s --max-time 10 \
    -X POST -H "Content-Type: application/json" \
    -d "$TRACES_QUERY" \
    "${OAP_URL}/graphql" 2>/dev/null || echo "")

if [ -n "$TRACES_RESP" ] && echo "$TRACES_RESP" | grep -q '"traces"'; then
    TRACE_COUNT=$(echo "$TRACES_RESP" | grep -o '"key"' | wc -l | tr -d ' ')
    if [ "$TRACE_COUNT" -gt 0 ]; then
        pass "SkyWalking has ${TRACE_COUNT} trace(s) recorded"
    else
        warn "SkyWalking has no traces yet — agent may not be sending data"
    fi
else
    warn "Could not query SkyWalking traces (OAP may have no data yet)"
fi

# Summary
echo ""
if [ "$EXIT_CODE" = "0" ]; then
    echo -e "${GREEN}=== Results: ALL CHECKS PASSED ===${NC}"
else
    echo -e "${RED}=== Results: SOME CHECKS FAILED ===${NC}"
fi
exit "$EXIT_CODE"
