#!/usr/bin/env bash
# smoke-test-api.sh — P1 核心链路 API 冒烟测试
#
# 用法:
#   TEST_USER=xxx TEST_PASS=xxx bash smoke-test-api.sh
#   TOKEN=Bearer-xxx bash smoke-test-api.sh  # 使用指定 token
#
# 前置: 五域服务已启动 (8081/8082/8084/8086)

set -euo pipefail

MASTERDATA="http://localhost:8081"
ACCESS="http://localhost:8082"
BILLING="http://localhost:8084"
GOVERNANCE="http://localhost:8085"
IDENTITY="http://localhost:8086"

PASS=0
FAIL=0

check() {
    local desc="$1"
    local http_code="$2"
    local expected="${3:-200}"
    if [ "$http_code" = "$expected" ]; then
        echo "  ✅ $desc (HTTP $http_code)"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $desc (HTTP $http_code, expected $expected)"
        FAIL=$((FAIL + 1))
    fi
}

api() {
    local method="$1" url="$2" data="${3:-}"
    if [ -n "$data" ]; then
        curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -H "Authorization: $TOKEN" \
            -d "$data"
    else
        curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" \
            -H "Authorization: $TOKEN"
    fi
}

api_body() {
    local method="$1" url="$2" data="${3:-}"
    if [ -n "$data" ]; then
        curl -s -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -H "Authorization: $TOKEN" \
            -d "$data"
    else
        curl -s -X "$method" "$url" \
            -H "Authorization: $TOKEN"
    fi
}

echo "=========================================="
echo "  P1 核心链路 API 冒烟测试"
echo "=========================================="
echo ""

# ============================================================
# 0. 获取认证 Token
# ============================================================
echo "--- 0. 获取认证 Token ---"

if [ -z "${TOKEN:-}" ]; then
    if [ -z "${TEST_USER:-}" ] || [ -z "${TEST_PASS:-}" ]; then
        echo "  ❌ 未提供认证信息，请设置 TOKEN，或同时设置 TEST_USER 和 TEST_PASS"
        exit 1
    fi

    LOGIN_RESP=$(curl -s -X POST "$IDENTITY/identity/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${TEST_USER}\",\"password\":\"${TEST_PASS}\"}" 2>/dev/null || echo '{}')

    TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null || echo "")

    if [ -n "$TOKEN" ]; then
        TOKEN="Bearer $TOKEN"
        echo "  ✅ Token 获取成功"
    else
        echo "  ❌ Token 获取失败，请检查凭据和 /identity/auth/login 端点"
        exit 1
    fi
else
    echo "  ✅ 使用指定 Token"
fi
echo ""

# ============================================================
# 1. 主数据域 — 厂商 CRUD
# ============================================================
echo "--- 1. 厂商 CRUD ---"

TIMESTAMP=$(date +%s)

VENDOR_CREATE=$(api_body POST "$MASTERDATA/vendor" "{
    \"vendorCode\": \"TEST_V_${TIMESTAMP}\",
    \"vendorName\": \"测试厂商-冒烟\",
    \"vendorType\": \"http\",
    \"status\": \"active\"
}")
VENDOR_ID=$(echo "$VENDOR_CREATE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

http_code=$(api POST "$MASTERDATA/vendor" "{\"vendorCode\":\"TEST_V2_${TIMESTAMP}\",\"vendorName\":\"测试厂商2\",\"vendorType\":\"http\",\"status\":\"active\"}")
check "创建厂商" "$http_code"

http_code=$(api GET "$MASTERDATA/vendor/list?pageNum=1&pageSize=10")
check "厂商列表" "$http_code"

if [ -n "$VENDOR_ID" ] && [ "$VENDOR_ID" != "" ]; then
    http_code=$(api GET "$MASTERDATA/vendor/$VENDOR_ID")
    check "厂商详情" "$http_code"

    http_code=$(api PUT "$MASTERDATA/vendor/$VENDOR_ID" '{"vendorName":"测试厂商-已更新","status":"active"}')
    check "更新厂商" "$http_code"
fi
echo ""

# ============================================================
# 2. 主数据域 — 数据类型 CRUD
# ============================================================
echo "--- 2. 数据类型 CRUD ---"

http_code=$(api POST "$MASTERDATA/datatype" "{\"dataTypeCode\":\"TEST_DT_${TIMESTAMP}\",\"dataTypeName\":\"测试数据类型\",\"dataTypeDesc\":\"测试数据类型\",\"status\":\"active\"}")
check "创建数据类型" "$http_code"

http_code=$(api GET "$MASTERDATA/datatype/list?pageNum=1&pageSize=10")
check "数据类型列表" "$http_code"

http_code=$(api GET "$MASTERDATA/datatype/all")
check "全部数据类型" "$http_code"
echo ""

# ============================================================
# 3. 主数据域 — 接口定义
# ============================================================
echo "--- 3. 接口定义 ---"

http_code=$(api GET "$MASTERDATA/interface/list?pageNum=1&pageSize=10")
check "接口列表" "$http_code"
echo ""

# ============================================================
# 4. 访问域 — 调用方 CRUD
# ============================================================
echo "--- 4. 调用方 CRUD ---"

CALLER_CREATE=$(api_body POST "$ACCESS/caller" "{
    \"callerName\": \"测试调用方-冒烟\",
    \"callerCode\": \"TEST_C_${TIMESTAMP}\",
    \"status\": \"active\"
}")
CALLER_ID=$(echo "$CALLER_CREATE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

http_code=$(api POST "$ACCESS/caller" "{\"callerName\":\"测试调用方2\",\"callerCode\":\"TEST_C2_${TIMESTAMP}\",\"status\":\"active\"}")
check "创建调用方" "$http_code"

http_code=$(api GET "$ACCESS/caller/list?pageNum=1&pageSize=10")
check "调用方列表" "$http_code"
echo ""

# ============================================================
# 5. 访问域 — API Key 管理
# ============================================================
echo "--- 5. API Key 管理 ---"

if [ -n "$CALLER_ID" ] && [ "$CALLER_ID" != "" ]; then
    http_code=$(api POST "$ACCESS/caller/$CALLER_ID/api-key" '{"name":"冒烟测试Key"}')
    check "生成 API Key" "$http_code"
else
    echo "  ⚠️ 跳过 (调用方 ID 为空)"
fi

http_code=$(api GET "$ACCESS/caller/apikey/list?pageNum=1&pageSize=10")
check "API Key 列表" "$http_code"
echo ""

# ============================================================
# 6. 计费域 — 计费规则与账单
# ============================================================
echo "--- 6. 计费规则与账单 ---"

http_code=$(api GET "$BILLING/billing/rule/list?pageNum=1&pageSize=10")
check "计费规则列表" "$http_code"

http_code=$(api POST "$BILLING/billing/rule" "{
    \"ruleName\": \"冒烟测试计费规则\",
    \"vendorCode\": \"TEST_V_${TIMESTAMP}\",
    \"dataTypeCode\": \"TEST_DT_${TIMESTAMP}\",
    \"billingType\": \"standard\",
    \"unitPrice\": 0.5,
    \"status\": \"active\"
}")
check "创建计费规则" "$http_code"

http_code=$(api GET "$BILLING/billing/list?pageNum=1&pageSize=10")
check "账单列表" "$http_code"

http_code=$(api GET "$BILLING/billing/stats")
check "计费统计" "$http_code"
echo ""

# ============================================================
# 7. 身份租户域 — P2 链路
# ============================================================
echo "--- 7. 身份租户域 ---"

http_code=$(api GET "$IDENTITY/tenant/list?page=1&pageSize=10")
check "租户列表" "$http_code"

TENANT_CREATE=$(api_body POST "$IDENTITY/tenant" "{
    \"tenantCode\": \"TEST_T_${TIMESTAMP}\",
    \"tenantName\": \"测试租户-冒烟\",
    \"tenantType\": \"enterprise\",
    \"contactPerson\": \"测试联系人\",
    \"contactPhone\": \"13800138000\"
}")
TENANT_ID=$(echo "$TENANT_CREATE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

http_code=$(api POST "$IDENTITY/tenant" "{\"tenantCode\":\"TEST_T2_${TIMESTAMP}\",\"tenantName\":\"测试租户2\",\"tenantType\":\"enterprise\"}")
check "创建租户" "$http_code"

if [ -n "$TENANT_ID" ] && [ "$TENANT_ID" != "" ]; then
    http_code=$(api PATCH "$IDENTITY/tenant/$TENANT_ID/status" '{"status":"inactive"}')
    check "租户状态切换" "$http_code"
fi
echo ""

# ============================================================
# 8. 治理观测域 — P2 链路
# ============================================================
echo "--- 8. 治理观测域 ---"

http_code=$(api GET "$GOVERNANCE/alert/rule/list?page=1&pageSize=10")
check "告警规则列表" "$http_code"

http_code=$(api POST "$GOVERNANCE/alert/rule" "{
    \"ruleName\": \"冒烟测试告警规则_${TIMESTAMP}\",
    \"ruleType\": \"THRESHOLD\",
    \"targetType\": \"billing\",
    \"conditionType\": \"gt\",
    \"thresholdValue\": 80,
    \"severity\": \"warning\"
}")
check "创建告警规则" "$http_code"

http_code=$(api GET "$GOVERNANCE/log/list?page=1&pageSize=10")
check "操作日志列表" "$http_code"

http_code=$(api GET "$GOVERNANCE/quality/rules")
check "质量规则列表" "$http_code"

http_code=$(api GET "$GOVERNANCE/trace/lineage/upstream?type=api&id=1")
check "血缘上游查询" "$http_code"

# 验证 X-Trace-Id 头传播
TRACE_RESP=$(curl -s -D- -o /dev/null "$MASTERDATA/datatype/list?page=1&pageSize=5" \
    -H "Authorization: $TOKEN" \
    -H "X-Trace-Id: smoke-test-trace-$(date +%s)")
if echo "$TRACE_RESP" | grep -qi "x-trace-id"; then
    echo "  ✅ X-Trace-Id 响应头存在"
    PASS=$((PASS + 1))
else
    echo "  ⚠️ X-Trace-Id 响应头缺失 (非阻塞)"
fi
echo ""

# ============================================================
# 汇总
# ============================================================
echo "=========================================="
echo "  测试完成: ✅ $PASS 通过, ❌ $FAIL 失败"
echo "=========================================="

exit "$FAIL"
