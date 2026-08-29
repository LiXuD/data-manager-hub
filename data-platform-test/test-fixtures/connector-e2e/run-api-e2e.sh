#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
STATE_FILE="${1:-}"
GATEWAY_URL="${E2E_GATEWAY_URL:-http://127.0.0.1:8888}"

[[ -f "$STATE_FILE" ]] || {
  echo "用法: $0 <fixture.env>" >&2
  exit 2
}

# shellcheck disable=SC1090
source "$STATE_FILE"
[[ "${E2E_PROJECT_ROOT:-}" == "$PROJECT_ROOT" ]] || {
  echo "fixture状态文件不属于当前项目" >&2
  exit 1
}
[[ "${E2E_DB_NAME:-}" =~ ^dataplatform_connector_e2e_[0-9]{14}_[0-9]+_regression$ ]] || {
  echo "拒绝使用非隔离E2E数据库名: ${E2E_DB_NAME:-}" >&2
  exit 1
}

for command_name in curl jq psql; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少命令: $command_name" >&2
    exit 1
  }
done

export PGPASSWORD="$E2E_DB_PASSWORD"
PSQL=(psql -X -v ON_ERROR_STOP=1 -h "$E2E_DB_HOST" -p "$E2E_DB_PORT" -U "$E2E_DB_USERNAME")
sql() {
  "${PSQL[@]}" -d "$E2E_DB_NAME" -Atq -c "$1"
}

WORK_DIR="$(mktemp -d)"
cleanup() { rm -rf -- "$WORK_DIR"; }
trap cleanup EXIT

ADMIN_TOKEN=""
RESPONSE_FILE=""
HTTP_CODE="000"
API_CALL_NUMBER=0
PUBLIC_CALL_NUMBER=0
EXPECTED_RECORDS=0
TRACE_PREFIX="connector-e2e-api-$(date -u +%Y%m%d%H%M%S)-$$"

api_call() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local response_path="$WORK_DIR/api-${API_CALL_NUMBER}.json"
  local error_path="$WORK_DIR/api-${API_CALL_NUMBER}.err"
  API_CALL_NUMBER=$((API_CALL_NUMBER + 1))
  RESPONSE_FILE="$response_path"
  if [[ -n "$body" ]]; then
    HTTP_CODE="$(curl -skS --connect-timeout 5 --max-time 30 \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H 'Content-Type: application/json' -X "$method" \
      "$GATEWAY_URL$path" --data-binary "$body" \
      -o "$response_path" -w '%{http_code}' 2>"$error_path" || true)"
  else
    HTTP_CODE="$(curl -skS --connect-timeout 5 --max-time 30 \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -X "$method" "$GATEWAY_URL$path" \
      -o "$response_path" -w '%{http_code}' 2>"$error_path" || true)"
  fi
}

result_code() { jq -r '.code // 0' "$RESPONSE_FILE" 2>/dev/null || printf '0'; }

expect_result() {
  local label="$1"
  local code
  code="$(result_code)"
  if [[ "$HTTP_CODE" != "200" || "$code" != "200" ]]; then
    echo "${label}失败: HTTP=$HTTP_CODE code=$code" >&2
    exit 1
  fi
}

login() {
  RESPONSE_FILE="$WORK_DIR/login.json"
  HTTP_CODE="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H 'Content-Type: application/json' \
    -X POST "$GATEWAY_URL/api/v1/auth/login" \
    --data-binary "$(jq -nc --arg username "$FIXTURE_ADMIN_USERNAME" \
      --arg password "$FIXTURE_ADMIN_PASSWORD" \
      '{username:$username,password:$password}')" \
    -o "$RESPONSE_FILE" -w '%{http_code}' 2>"$WORK_DIR/login.err" || true)"
  ADMIN_TOKEN="$(jq -r '.data.token // .data.accessToken // empty' "$RESPONSE_FILE" 2>/dev/null || true)"
  [[ "$HTTP_CODE" == "200" && -n "$ADMIN_TOKEN" ]] || {
    echo "管理端登录失败: HTTP=$HTTP_CODE" >&2
    exit 1
  }
}

wait_plugin_ready() {
  local attempt
  for attempt in $(seq 1 30); do
    api_call GET "/api/v1/connector-plugin/e2e-signed-connector/versions/1.1.0/activation"
    if [[ "$HTTP_CODE" == "200" ]] && jq -e '.code == 200 and .data.ready == true' "$RESPONSE_FILE" >/dev/null 2>&1; then
      return
    fi
    sleep 0.5
  done
  echo "签名插件未达到 READY" >&2
  exit 1
}

stage_plugin() {
  api_call GET "/api/v1/connector-plugin/e2e-signed-connector/versions/1.1.0/activation"
  if [[ "$HTTP_CODE" == "200" ]] && jq -e '.code == 200 and .data.ready == true' "$RESPONSE_FILE" >/dev/null 2>&1; then
    return
  fi

  if [[ "$HTTP_CODE" != "200" ]]; then
    api_call POST /api/v1/connector-plugin/versions/import "$(jq -nc \
      --arg artifactUri "$FIXTURE_ARTIFACT_URI" \
      --arg expectedSha256 "$FIXTURE_ARTIFACT_SHA256" \
      --arg detachedSignature "$FIXTURE_DETACHED_SIGNATURE" \
      --arg signingKeyId "$FIXTURE_SIGNING_KEY_ID" \
      '{artifactUri:$artifactUri,expectedSha256:$expectedSha256,detachedSignature:$detachedSignature,signingKeyId:$signingKeyId}')"
    if [[ "$HTTP_CODE" != "200" && "$(result_code)" != "409" ]]; then
      expect_result "导入签名插件"
    fi
  fi

  api_call POST "/api/v1/connector-plugin/e2e-signed-connector/versions/1.1.0/stage"
  expect_result "预加载签名插件"
  wait_plugin_ready
}

activate_plugin() {
  api_call GET "/api/v1/connector-plugin/e2e-signed-connector/versions"
  expect_result "读取签名插件版本"
  api_call POST "/api/v1/connector-plugin/e2e-signed-connector/versions/1.1.0/activate"
  if [[ "$HTTP_CODE" == "200" && "$(result_code)" == "200" ]]; then
    return
  fi
  if jq -e '((.msg // .message // "") | test("已激活|already active"; "i"))' "$RESPONSE_FILE" >/dev/null 2>&1; then
    return
  fi
  if jq -e '((.msg // .message // "") | contains("只有STAGING插件版本可以激活"))' "$RESPONSE_FILE" >/dev/null 2>&1; then
    api_call GET "/api/v1/connector-plugin/e2e-signed-connector/versions/1.1.0/activation"
    if [[ "$HTTP_CODE" == "200" ]] && jq -e '.code == 200 and .data.ready == true' "$RESPONSE_FILE" >/dev/null 2>&1; then
      return
    fi
  fi
  expect_result "激活签名插件"
}

wait_plugin_runtime_ready() {
  local attempt
  for attempt in $(seq 1 40); do
    api_call GET "/api/v1/connector-plugin/e2e-signed-connector/versions/1.1.0/activation"
    if [[ "$HTTP_CODE" == "200" ]] && jq -e '
      .code == 200
      and .data.ready == true
      and ((.data.instances // []) | length >= 2)
      and all(.data.instances[]; .state == "READY")
    ' "$RESPONSE_FILE" >/dev/null 2>&1; then
      return
    fi
    sleep 0.5
  done
  echo "签名插件未在全部Access实例达到READY" >&2
  exit 1
}

current_draft() {
  local config_id="$1"
  api_call GET "/api/v1/vendor/config/$config_id/connector-spec/draft"
  expect_result "读取配置${config_id}草稿"
}

ensure_primary_simple() {
  current_draft "$FIXTURE_VENDOR_CONFIG_ID"
  local authoring_mode draft_version
  authoring_mode="$(jq -r '.data.authoringMode // empty' "$RESPONSE_FILE")"
  draft_version="$(jq -r '.data.draftVersion // 0' "$RESPONSE_FILE")"
  if [[ "$authoring_mode" == "ADVANCED_LEGACY" ]]; then
    api_call POST "/api/v1/vendor/config/$FIXTURE_VENDOR_CONFIG_ID/connector-spec/convert" \
      "$(jq -nc --argjson expectedDraftVersion "$draft_version" \
        '{expectedDraftVersion:$expectedDraftVersion}')"
    expect_result "转换Legacy草稿"
  fi
}

ensure_secret_ref() {
  local existing
  existing="$(sql "SELECT id FROM vendor_config_extended WHERE vendor_id = ${FIXTURE_VENDOR_ID} AND config_key = 'connector.e2e.client-secret' AND status = 'active' LIMIT 1")"
  if [[ -n "$existing" ]]; then
    return
  fi
  api_call POST /api/v1/vendor/extended-config "$(jq -nc \
    --argjson vendorId "$FIXTURE_VENDOR_ID" \
    --arg configKey "$FIXTURE_CONNECTOR_SECRET_REF" \
    --arg configValue "$FIXTURE_CONNECTOR_SECRET_VALUE" \
    '{vendorId:$vendorId,configKey:$configKey,configValue:$configValue,configType:"secret",description:"Connector E2E token",isEncrypted:true,isActive:true}')"
  expect_result "创建连接器SecretRef"
}

spec_for_single() {
  local endpoint="$1"
  jq -nc --arg endpoint "$endpoint" '{
    specVersion:"1",
    plugin:{pluginId:"e2e-signed-connector",pluginVersion:"1.1.0"},
    config:{flow:"single-http",endpoint:$endpoint,connectTimeoutMs:2000,readTimeoutMs:5000,totalTimeoutMs:8000,maxResponseBytes:1048576},
    responseMapping:null
  }'
}

spec_for_token() {
  local endpoint_base="$1"
  jq -nc --arg tokenEndpoint "$endpoint_base/vendor/token" \
    --arg businessEndpoint "$endpoint_base/vendor/business" \
    --arg clientSecret "$FIXTURE_CONNECTOR_SECRET_REF" '{
      specVersion:"1",
      plugin:{pluginId:"e2e-signed-connector",pluginVersion:"1.1.0"},
      config:{flow:"token-business",tokenEndpoint:$tokenEndpoint,businessEndpoint:$businessEndpoint,clientId:"e2e-client",clientSecret:$clientSecret,connectTimeoutMs:2000,readTimeoutMs:5000,totalTimeoutMs:8000,maxResponseBytes:1048576},
      responseMapping:null
    }'
}

spec_for_polling() {
  local endpoint_base="$1"
  jq -nc --arg submitEndpoint "$endpoint_base/vendor/async/submit" \
    --arg pollEndpointBase "$endpoint_base" '{
      specVersion:"1",
      plugin:{pluginId:"e2e-signed-connector",pluginVersion:"1.1.0"},
      config:{flow:"async-polling",submitEndpoint:$submitEndpoint,pollEndpointBase:$pollEndpointBase,maxPolls:2,connectTimeoutMs:2000,readTimeoutMs:5000,totalTimeoutMs:8000,maxResponseBytes:1048576},
      responseMapping:null
    }'
}

save_spec() {
  local config_id="$1"
  local expected_version="$2"
  local spec_json="$3"
  api_call PUT "/api/v1/vendor/config/$config_id/connector-spec/draft" \
    "$(jq -nc --argjson expectedDraftVersion "$expected_version" \
      --argjson connectorSpec "$spec_json" \
      '{expectedDraftVersion:$expectedDraftVersion,connectorSpec:$connectorSpec}')"
  expect_result "保存配置${config_id}产品草稿"
  DRAFT_VERSION="$(jq -r '.data.draftVersion' "$RESPONSE_FILE")"
}

save_spec_at_current_version() {
  local config_id="$1"
  local spec_json="$2"
  current_draft "$config_id"
  local expected_version
  expected_version="$(jq -r '.data.draftVersion // 0' "$RESPONSE_FILE")"
  save_spec "$config_id" "$expected_version" "$spec_json"
}

controlled_test() {
  local config_id="$1"
  local label="$2"
  local params_json="$3"
  api_call POST "/api/v1/vendor/config/$config_id/connector-spec/test" \
    "$(jq -nc --argjson params "$params_json" '{params:$params}')"
  expect_result "受控测试${label}"
  jq -e '.data.success == true' "$RESPONSE_FILE" >/dev/null || {
    echo "受控测试${label}未成功" >&2
    exit 1
  }
}

publish_spec() {
  local config_id="$1"
  local expected_version="$2"
  local attempt
  for attempt in $(seq 1 5); do
    api_call POST "/api/v1/vendor/config/$config_id/connector-spec/publish" \
      "$(jq -nc --argjson expectedDraftVersion "$expected_version" \
        '{expectedDraftVersion:$expectedDraftVersion}')"
    if [[ "$HTTP_CODE" == "200" && "$(result_code)" == "200" ]]; then
      return
    fi
    if [[ "$HTTP_CODE" == "409" ]] && jq -e '(.msg // .message // "") == "CONNECTOR_VERSION_ALREADY_ACTIVE"' "$RESPONSE_FILE" >/dev/null 2>&1; then
      return
    fi
    if [[ "$attempt" -lt 5 ]] && jq -e '((.msg // .message // "") | test("PLUGIN_STATUS_INVALID|CONNECTOR_PLUGIN_NOT_READY"))' "$RESPONSE_FILE" >/dev/null 2>&1; then
      sleep 1
      continue
    fi
    expect_result "发布配置${config_id}"
  done
  expect_result "发布配置${config_id}"
}

activate_config() {
  local config_id="$1"
  api_call PATCH "/api/v1/vendor/config/$config_id/status" '{"status":"active"}'
  expect_result "启用配置${config_id}"
}

route_configs() {
  local primary_id="$1"
  local fallback_id="$2"
  api_call PUT "/api/v1/interface/$FIXTURE_INTERFACE_ID/vendor-routing" \
    "$(jq -nc --argjson primaryVendorConfigId "$primary_id" \
      --argjson fallbackVendorConfigId "$fallback_id" \
      '{primaryVendorConfigId:$primaryVendorConfigId,fallbackVendorConfigId:$fallbackVendorConfigId}')"
  expect_result "保存主备路由"
  jq -e '.data.routingReadiness == "READY"' "$RESPONSE_FILE" >/dev/null || {
    echo "主备路由未达到READY" >&2
    exit 1
  }
}

public_call() {
  local label="$1"
  local use_cache="$2"
  local params_json="$3"
  local request_id="${TRACE_PREFIX}-${label}-${PUBLIC_CALL_NUMBER}"
  local response_path="$WORK_DIR/public-${PUBLIC_CALL_NUMBER}.json"
  local error_path="$WORK_DIR/public-${PUBLIC_CALL_NUMBER}.err"
  PUBLIC_CALL_NUMBER=$((PUBLIC_CALL_NUMBER + 1))
  EXPECTED_RECORDS=$((EXPECTED_RECORDS + 1))
  PUBLIC_RESPONSE_FILE="$response_path"
  PUBLIC_HTTP_CODE="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H 'Content-Type: application/json' \
    -H "X-Api-Key: $FIXTURE_API_KEY_VALUE" \
    -H "X-Trace-Id: $request_id" \
    -X POST "$GATEWAY_URL/openapi/v1/query" \
    --data-binary "$(jq -nc --arg requestId "$request_id" \
      --arg apiCode "$INTERFACE_CODE" --arg productCode "$PRODUCT_CODE" \
      --arg sceneCode "$SCENE_CODE" --argjson useCache "$use_cache" \
      --argjson params "$params_json" \
      '{requestId:$requestId,apiCode:$apiCode,apiVersion:"v1",productCode:$productCode,sceneCode:$sceneCode,useCache:$useCache,cacheDays:1,params:$params}')" \
    -o "$response_path" -w '%{http_code}' 2>"$error_path" || true)"
}

fixture_state() {
  local state
  state="$(curl -skS --connect-timeout 5 --max-time 10 \
    "$FIXTURE_ENDPOINT_BASE/state")" || {
    echo "无法读取厂商fixture计数器" >&2
    exit 1
  }
  jq -e 'type == "object" and (.vendorRequests | type == "number")
    and (.vendorEchoRequests | type == "number")
    and (.vendorFallbackRequests | type == "number")' \
    <<< "$state" >/dev/null || {
    echo "厂商fixture计数器响应无效" >&2
    exit 1
  }
  printf '%s' "$state"
}

fixture_counter() {
  local state="$1"
  local counter="$2"
  jq -er --arg counter "$counter" '.[$counter] | numbers' <<< "$state"
}

assert_fixture_counter_delta() {
  local before="$1"
  local after="$2"
  local counter="$3"
  local expected="$4"
  local label="$5"
  local before_value after_value
  before_value="$(fixture_counter "$before" "$counter")"
  after_value="$(fixture_counter "$after" "$counter")"
  if ! [[ "$before_value" =~ ^[0-9]+$ && "$after_value" =~ ^[0-9]+$ ]] \
      || (( after_value - before_value != expected )); then
    echo "${label}fixture计数不匹配: ${counter} ${before_value}->${after_value}, 预期增量${expected}" >&2
    exit 1
  fi
}

assert_fixture_state_unchanged() {
  local before="$1"
  local after="$2"
  local label="$3"
  if [[ "$(jq -S -c . <<< "$before")" != "$(jq -S -c . <<< "$after")" ]]; then
    echo "${label}改变了厂商fixture计数，疑似绕过缓存" >&2
    exit 1
  fi
}

expect_public_success() {
  local label="$1"
  local expected_cached="$2"
  if [[ "$PUBLIC_HTTP_CODE" != "200" ]] || ! jq -e --argjson cached "$expected_cached" \
    '.code == 200 and .data.success == true and .data.cached == $cached' "$PUBLIC_RESPONSE_FILE" >/dev/null 2>&1; then
    echo "${label}公开调用未成功: HTTP=$PUBLIC_HTTP_CODE" >&2
    exit 1
  fi
}

expect_public_error() {
  local label="$1"
  local expected_error="$2"
  if [[ "$PUBLIC_HTTP_CODE" != "200" ]] || ! jq -e --arg errorCode "$expected_error" \
    '.code == 200 and .data.success == false and .data.errorCode == $errorCode' "$PUBLIC_RESPONSE_FILE" >/dev/null 2>&1; then
    echo "${label}公开错误码不匹配: HTTP=$PUBLIC_HTTP_CODE" >&2
    exit 1
  fi
}

prepare_legacy_migration_source() {
  local active_mode
  active_mode="$(sql "SELECT v.authoring_mode FROM vendor_config c JOIN vendor_connector_version v ON v.id = c.active_connector_version_id WHERE c.id = ${FIXTURE_VENDOR_CONFIG_ID} AND v.status = 'ACTIVE'")"
  if [[ "$active_mode" == "ADVANCED_LEGACY" ]]; then
    return
  fi
  if [[ -n "$active_mode" ]]; then
    echo "隔离迁移夹具已有非Legacy活动连接器，拒绝跳过迁移控制面验收" >&2
    exit 1
  fi

  current_draft "$FIXTURE_VENDOR_CONFIG_ID"
  local draft_version
  draft_version="$(jq -r '.data.draftVersion // 0' "$RESPONSE_FILE")"
  [[ "$draft_version" =~ ^[1-9][0-9]*$ ]] || {
    echo "隔离迁移夹具Legacy草稿版本无效" >&2
    exit 1
  }
  api_call POST "/api/v1/vendor/config/$FIXTURE_VENDOR_CONFIG_ID/connector/test" \
    '{"params":{"probe":"legacy-migration-controlled","requestId":"controlled-legacy-migration"}}'
  expect_result "Legacy迁移前受控测试"
  jq -e '.data.success == true' "$RESPONSE_FILE" >/dev/null || {
    echo "Legacy迁移前受控测试未成功" >&2
    exit 1
  }
  api_call POST "/api/v1/vendor/config/$FIXTURE_VENDOR_CONFIG_ID/connector/publish" \
    "$(jq -nc --argjson expectedDraftVersion "$draft_version" \
      '{expectedDraftVersion:$expectedDraftVersion}')"
  expect_result "发布Legacy迁移源版本"
  active_mode="$(sql "SELECT v.authoring_mode FROM vendor_config c JOIN vendor_connector_version v ON v.id = c.active_connector_version_id WHERE c.id = ${FIXTURE_VENDOR_CONFIG_ID} AND v.status = 'ACTIVE'")"
  [[ "$active_mode" == "ADVANCED_LEGACY" ]] || {
    echo "Legacy迁移源版本未成为活动版本" >&2
    exit 1
  }
}

verify_legacy_inventory() {
  api_call GET "/api/v1/vendor/config/connector-spec/inventory?page=1&pageSize=100"
  expect_result "读取Legacy迁移清点"
  jq -e --argjson configId "$FIXTURE_VENDOR_CONFIG_ID" '
    .data.total >= 1
    and (.data.items | any(.[];
      .vendorConfigId == $configId
      and ((.active // .draft).authoringMode == "ADVANCED_LEGACY")
      and ((.active // .draft).classification
        | IN("LOSSLESS_CONVERTIBLE", "REQUIRES_DEDICATED_PLUGIN", "MUST_REMAIN_LEGACY"))
    ))
  ' "$RESPONSE_FILE" >/dev/null || {
    echo "Legacy迁移清点未返回隔离厂商分类" >&2
    exit 1
  }
  INVENTORY_TOTAL="$(jq -r '.data.total' "$RESPONSE_FILE")"
  INVENTORY_CLASSIFICATIONS="$(jq -r --argjson configId "$FIXTURE_VENDOR_CONFIG_ID" '
    [.data.items[]
      | select(.vendorConfigId == $configId)
      | [(.active // .draft).classification]
      | .[]] | unique | join(",")
  ' "$RESPONSE_FILE")"
}

prepare_migration_record() {
  api_call POST "/api/v1/vendor/connector-migration/$FIXTURE_VENDOR_CONFIG_ID/prepare"
  expect_result "准备厂商迁移控制记录"
  jq -e '.data.state == "PREPARED" and .data.recordVersion == 0' "$RESPONSE_FILE" >/dev/null || {
    echo "厂商迁移控制记录未进入PREPARED" >&2
    exit 1
  }
  MIGRATION_RECORD_VERSION="$(jq -r '.data.recordVersion' "$RESPONSE_FILE")"
}

start_migration_observation() {
  api_call POST "/api/v1/vendor/connector-migration/$FIXTURE_VENDOR_CONFIG_ID/start-observation" \
    "$(jq -nc --argjson expectedRecordVersion "$MIGRATION_RECORD_VERSION" \
      '{expectedRecordVersion:$expectedRecordVersion,minimumObservationMinutes:0,minimumCalls:1,
        maximumErrorRate:0.05,maximumP95DurationMs:5000,minimumBillingCoverageRate:1}')"
  expect_result "开始厂商迁移观察"
  jq -e '.data.state == "OBSERVING" and .data.observationGatePassed == false' "$RESPONSE_FILE" >/dev/null || {
    echo "厂商迁移控制记录未进入OBSERVING" >&2
    exit 1
  }
  MIGRATION_RECORD_VERSION="$(jq -r '.data.recordVersion' "$RESPONSE_FILE")"
}

wait_for_trace_billing() {
  local trace_id="$1"
  local billing_events=0
  local attempt
  for attempt in $(seq 1 40); do
    billing_events="$(sql "SELECT count(*) FROM billing_event WHERE request_id IN (SELECT request_id FROM call_record WHERE trace_id = '$trace_id')")"
    [[ "$billing_events" -ge 1 ]] && return
    sleep 0.25
  done
  echo "迁移观察调用未产生BillingEvent: $trace_id" >&2
  exit 1
}

observe_and_complete_migration() {
  api_call POST "/api/v1/vendor/connector-migration/$FIXTURE_VENDOR_CONFIG_ID/observe" \
    "$(jq -nc --argjson expectedRecordVersion "$MIGRATION_RECORD_VERSION" \
      '{expectedRecordVersion:$expectedRecordVersion}')"
  expect_result "刷新厂商迁移观察"
  jq -e '.data.state == "READY" and .data.observationGatePassed == true
    and .data.observedCalls >= 1 and .data.observedBillingCoverageRate >= 1' "$RESPONSE_FILE" >/dev/null || {
    echo "厂商迁移观察门禁未通过" >&2
    exit 1
  }
  MIGRATION_RECORD_VERSION="$(jq -r '.data.recordVersion' "$RESPONSE_FILE")"
  api_call POST "/api/v1/vendor/connector-migration/$FIXTURE_VENDOR_CONFIG_ID/complete" \
    "$(jq -nc --argjson expectedRecordVersion "$MIGRATION_RECORD_VERSION" \
      '{expectedRecordVersion:$expectedRecordVersion}')"
  expect_result "完成厂商迁移观察"
  jq -e '.data.state == "STABLE" and .data.observationGatePassed == true' "$RESPONSE_FILE" >/dev/null || {
    echo "厂商迁移控制记录未进入STABLE" >&2
    exit 1
  }
}

ensure_backup_config() {
  BACKUP_CONFIG_ID="${FIXTURE_BACKUP_VENDOR_CONFIG_ID:-}"
  if [[ -z "$BACKUP_CONFIG_ID" ]]; then
    BACKUP_CONFIG_ID="$(sql "SELECT vc.id FROM vendor_config vc JOIN vendor_info vi ON vi.id = vc.vendor_id WHERE vc.interface_id = ${FIXTURE_INTERFACE_ID} AND vc.id <> ${FIXTURE_VENDOR_CONFIG_ID} AND vi.vendor_code LIKE 'e2e-backup-vendor-%' AND vc.deleted = false ORDER BY vc.id LIMIT 1")"
  fi
  [[ -n "$BACKUP_CONFIG_ID" ]] || {
    echo "未找到隔离备用厂商配置" >&2
    exit 1
  }
  BACKUP_VENDOR_ID="$(sql "SELECT vendor_id FROM vendor_config WHERE id = ${BACKUP_CONFIG_ID}")"
  [[ -n "$BACKUP_VENDOR_ID" ]] || {
    echo "备用厂商配置不存在" >&2
    exit 1
  }

  local plan_count
  plan_count="$(sql "SELECT count(*) FROM billing_plan WHERE vendor_id = ${BACKUP_VENDOR_ID} AND interface_id = ${FIXTURE_INTERFACE_ID} AND status = 'ACTIVE'")"
  if [[ "$plan_count" == "0" ]]; then
    BACKUP_VENDOR_CODE="$(sql "SELECT vendor_code FROM vendor_info WHERE id = ${BACKUP_VENDOR_ID}")"
    BACKUP_INTERFACE_CODE="$INTERFACE_CODE"
    sql "SELECT 1" >/dev/null
    psql -X -v ON_ERROR_STOP=1 -h "$E2E_DB_HOST" -p "$E2E_DB_PORT" -U "$E2E_DB_USERNAME" \
      -d "$E2E_DB_NAME" -v vendor_id="$BACKUP_VENDOR_ID" \
      -v interface_id="$FIXTURE_INTERFACE_ID" \
      -v plan_code="CONNECTOR-E2E-BACKUP-$(date -u +%Y%m%d%H%M%S)-$$" \
      -f "$SCRIPT_DIR/seed-billing-plan.sql" >/dev/null
  fi
}

wait_persisted_records() {
  local persisted=0
  local attempt
  for attempt in $(seq 1 40); do
    persisted="$(sql "SELECT count(*) FROM call_record WHERE trace_id LIKE '${TRACE_PREFIX}-%'")"
    [[ "$persisted" -ge "$EXPECTED_RECORDS" ]] && break
    sleep 0.25
  done
  [[ "$persisted" -ge "$EXPECTED_RECORDS" ]] || {
    echo "CallRecord未完整落库: $persisted/$EXPECTED_RECORDS" >&2
    exit 1
  }
}

wait_for_trace_records() {
  local minimum="$1"
  local persisted=0
  local attempt
  for attempt in $(seq 1 40); do
    persisted="$(sql "SELECT count(*) FROM call_record WHERE trace_id LIKE '${TRACE_PREFIX}-%'")"
    [[ "$persisted" -ge "$minimum" ]] && return
    sleep 0.25
  done
  echo "CallRecord未达到预期数量: $persisted/$minimum" >&2
  exit 1
}

login
stage_plugin
ensure_secret_ref
prepare_legacy_migration_source
verify_legacy_inventory
prepare_migration_record
ensure_primary_simple

INTERFACE_CODE="${FIXTURE_INTERFACE_CODE:-$(sql "SELECT interface_code FROM api_interface WHERE id = ${FIXTURE_INTERFACE_ID} AND status = 'active' AND deleted = false")}"
SCENE_CODE="${FIXTURE_SCENE_CODE:-$(sql "SELECT scene_code FROM call_scene WHERE status = 'active' AND deleted = false ORDER BY id LIMIT 1")}"
API_KEY_ROW="$(sql "SELECT k.api_key, cp.product_code FROM api_key k JOIN api_key_interface aki ON aki.api_key_id = k.id JOIN api_key_product akp ON akp.api_key_id = k.id JOIN caller_product cp ON cp.id = akp.product_id WHERE aki.interface_id = ${FIXTURE_INTERFACE_ID} AND aki.status = 'ACTIVE' AND k.status = 'active' AND k.deleted = false AND cp.status = 'active' AND cp.deleted = false ORDER BY k.id LIMIT 1")"
IFS='|' read -r FIXTURE_API_KEY_VALUE PRODUCT_CODE <<< "$API_KEY_ROW"
[[ -n "$INTERFACE_CODE" && -n "$SCENE_CODE" && -n "$FIXTURE_API_KEY_VALUE" && -n "$PRODUCT_CODE" ]] || {
  echo "没有找到隔离 API Key、产品、场景或接口" >&2
  exit 1
}

FIXTURE_ENDPOINT_BASE="${FIXTURE_VENDOR_ENDPOINT%/vendor/echo}"
ensure_backup_config

SINGLE_SPEC="$(spec_for_single "$FIXTURE_VENDOR_ENDPOINT")"
save_spec_at_current_version "$FIXTURE_VENDOR_CONFIG_ID" "$SINGLE_SPEC"
controlled_test "$FIXTURE_VENDOR_CONFIG_ID" single-http '{"probe":"single-controlled","requestId":"controlled-single"}'
activate_plugin
publish_spec "$FIXTURE_VENDOR_CONFIG_ID" "$DRAFT_VERSION"
activate_config "$FIXTURE_VENDOR_CONFIG_ID"
wait_plugin_runtime_ready
route_configs "$FIXTURE_VENDOR_CONFIG_ID" null

start_migration_observation
public_call migration-observation false '{"probe":"migration-observation"}'
expect_public_success migration-observation false
MIGRATION_TRACE_ID="${TRACE_PREFIX}-migration-observation-$((PUBLIC_CALL_NUMBER - 1))"
wait_for_trace_records 1
wait_for_trace_billing "$MIGRATION_TRACE_ID"
observe_and_complete_migration

BACKUP_SPEC="$(spec_for_single "$FIXTURE_ENDPOINT_BASE/vendor/fallback")"
save_spec_at_current_version "$BACKUP_CONFIG_ID" "$BACKUP_SPEC"
controlled_test "$BACKUP_CONFIG_ID" backup '{"probe":"backup-controlled","requestId":"controlled-backup"}'
publish_spec "$BACKUP_CONFIG_ID" "$DRAFT_VERSION"
activate_config "$BACKUP_CONFIG_ID"
wait_plugin_runtime_ready
route_configs "$FIXTURE_VENDOR_CONFIG_ID" "$BACKUP_CONFIG_ID"

CACHE_PARAMS="$(jq -nc --arg cacheKey "$TRACE_PREFIX" '{probe:"cache-same",cacheKey:$cacheKey}')"
CACHE_FIXTURE_BEFORE="$(fixture_state)"
public_call cache-miss true "$CACHE_PARAMS"
expect_public_success cache-miss false
CACHE_MISS_COST="$(jq -r '.data.cost // 0' "$PUBLIC_RESPONSE_FILE")"
wait_for_trace_records 1
CACHE_FIXTURE_AFTER_MISS="$(fixture_state)"
assert_fixture_counter_delta "$CACHE_FIXTURE_BEFORE" "$CACHE_FIXTURE_AFTER_MISS" \
  vendorEchoRequests 1 "缓存未命中"
public_call cache-hit true "$CACHE_PARAMS"
expect_public_success cache-hit true
CACHE_HIT_COST="$(jq -r '.data.cost // 0' "$PUBLIC_RESPONSE_FILE")"
jq -e '.data.cost == 0' "$PUBLIC_RESPONSE_FILE" >/dev/null || {
  echo "缓存命中未免除计费" >&2
  exit 1
}
assert_fixture_state_unchanged "$CACHE_FIXTURE_AFTER_MISS" "$(fixture_state)" "缓存命中"

declare -a ERROR_CASES=(
  'reject|BUSINESS_REJECTED'
  'malformed|RESPONSE_PARSE_ERROR'
  'http-error|TRANSPORT_HTTP_ERROR'
  'connection-error|TRANSPORT_CONNECTION_ERROR'
)
route_configs "$FIXTURE_VENDOR_CONFIG_ID" null
HTTP_ERROR_TRACE_ID=""
ERROR_FIXTURE_BEFORE="$(fixture_state)"
for error_case in "${ERROR_CASES[@]}"; do
  IFS='|' read -r probe expected_error <<< "$error_case"
  public_call "$probe" false "$(jq -nc --arg probe "$probe" '{probe:$probe}')"
  expect_public_error "$probe" "$expected_error"
  if [[ "$probe" == "http-error" ]]; then
    HTTP_ERROR_TRACE_ID="${TRACE_PREFIX}-http-error-$((PUBLIC_CALL_NUMBER - 1))"
  fi
done
primary_http_error_observed=0
for _ in $(seq 1 40); do
  primary_http_error_observed="$(sql "SELECT count(*) FROM call_record WHERE trace_id = '${HTTP_ERROR_TRACE_ID}' AND vendor_id = ${FIXTURE_VENDOR_ID}")"
  [[ "$primary_http_error_observed" == "1" ]] && break
  sleep 0.25
done
[[ "$primary_http_error_observed" == "1" ]] || {
  echo "SENT HTTP错误未保留主厂商实际事实" >&2
  exit 1
}
ERROR_FIXTURE_AFTER="$(fixture_state)"
assert_fixture_counter_delta "$ERROR_FIXTURE_BEFORE" "$ERROR_FIXTURE_AFTER" \
  vendorEchoRequests 4 "错误矩阵"
assert_fixture_counter_delta "$ERROR_FIXTURE_BEFORE" "$ERROR_FIXTURE_AFTER" \
  vendorFallbackRequests 0 "SENT错误不回退"
route_configs "$FIXTURE_VENDOR_CONFIG_ID" null

TOKEN_SPEC="$(spec_for_token "$FIXTURE_ENDPOINT_BASE")"
save_spec_at_current_version "$FIXTURE_VENDOR_CONFIG_ID" "$TOKEN_SPEC"
controlled_test "$FIXTURE_VENDOR_CONFIG_ID" token-business '{"probe":"token-controlled","requestId":"controlled-token"}'
publish_spec "$FIXTURE_VENDOR_CONFIG_ID" "$DRAFT_VERSION"
activate_config "$FIXTURE_VENDOR_CONFIG_ID"
route_configs "$FIXTURE_VENDOR_CONFIG_ID" null
public_call token-business false '{"probe":"token-public"}'
expect_public_success token-business false

POLLING_SPEC="$(spec_for_polling "$FIXTURE_ENDPOINT_BASE")"
save_spec_at_current_version "$FIXTURE_VENDOR_CONFIG_ID" "$POLLING_SPEC"
controlled_test "$FIXTURE_VENDOR_CONFIG_ID" async-polling '{"probe":"polling-controlled","requestId":"controlled-polling"}'
publish_spec "$FIXTURE_VENDOR_CONFIG_ID" "$DRAFT_VERSION"
activate_config "$FIXTURE_VENDOR_CONFIG_ID"
route_configs "$FIXTURE_VENDOR_CONFIG_ID" null
public_call async-polling false '{"probe":"polling-public"}'
expect_public_success async-polling false

FAILOVER_SPEC="$(spec_for_single "$FIXTURE_VENDOR_ENDPOINT")"
save_spec_at_current_version "$FIXTURE_VENDOR_CONFIG_ID" "$FAILOVER_SPEC"
controlled_test "$FIXTURE_VENDOR_CONFIG_ID" failover '{"probe":"failover-controlled","requestId":"controlled-failover"}'
publish_spec "$FIXTURE_VENDOR_CONFIG_ID" "$DRAFT_VERSION"
activate_config "$BACKUP_CONFIG_ID"
activate_config "$FIXTURE_VENDOR_CONFIG_ID"
wait_plugin_runtime_ready
route_configs "$FIXTURE_VENDOR_CONFIG_ID" "$BACKUP_CONFIG_ID"

fallback_success=0
fallback_trace_id=""
FALLBACK_FIXTURE_BEFORE="$(fixture_state)"
for attempt in $(seq 1 32); do
  public_call "circuit-http-error-$attempt" false '{"probe":"http-error"}'
  if [[ "$PUBLIC_HTTP_CODE" == "200" ]] && jq -e '.code == 200 and .data.success == true' "$PUBLIC_RESPONSE_FILE" >/dev/null 2>&1; then
    fallback_success=1
    fallback_trace_id="${TRACE_PREFIX}-circuit-http-error-$attempt-$((PUBLIC_CALL_NUMBER - 1))"
    break
  fi
  expect_public_error "circuit-http-error-$attempt" TRANSPORT_HTTP_ERROR
done
[[ "$fallback_success" == "1" ]] || {
  echo "熔断打开后的备用路由未成功" >&2
  exit 1
}
FALLBACK_FIXTURE_AFTER="$(fixture_state)"
fallback_primary_before="$(fixture_counter "$FALLBACK_FIXTURE_BEFORE" vendorEchoRequests)"
fallback_primary_after="$(fixture_counter "$FALLBACK_FIXTURE_AFTER" vendorEchoRequests)"
fallback_primary_delta=$((fallback_primary_after - fallback_primary_before))
[[ "$fallback_primary_delta" -gt 0 ]] || {
  echo "熔断观察未访问主厂商fixture" >&2
  exit 1
}
assert_fixture_counter_delta "$FALLBACK_FIXTURE_BEFORE" "$FALLBACK_FIXTURE_AFTER" \
  vendorFallbackRequests 1 "熔断备用路由"
backup_vendor_observed=0
for _ in $(seq 1 40); do
  backup_vendor_observed="$(sql "SELECT count(*) FROM call_record WHERE trace_id = '${fallback_trace_id}' AND vendor_id = ${BACKUP_VENDOR_ID}")"
  [[ "$backup_vendor_observed" == "1" ]] && break
  sleep 0.25
done
[[ "$backup_vendor_observed" == "1" ]] || {
  echo "熔断备用调用未写入实际厂商事实" >&2
  exit 1
}

wait_persisted_records
CALL_SUMMARY="$(sql "SELECT count(*), coalesce(sum(cost), 0), sum(CASE WHEN cache_hit THEN 1 ELSE 0 END), sum(CASE WHEN error_code IS NOT NULL THEN 1 ELSE 0 END), sum(CASE WHEN interface_id = ${FIXTURE_INTERFACE_ID} THEN 1 ELSE 0 END), sum(CASE WHEN plugin_id = 'e2e-signed-connector' AND plugin_version = '1.1.0' AND pipeline_version IS NOT NULL AND length(trim(snapshot_hash)) = 64 THEN 1 ELSE 0 END) FROM call_record WHERE trace_id LIKE '${TRACE_PREFIX}-%'")"
ERROR_CODES="$(sql "SELECT coalesce(string_agg(DISTINCT error_code, ',' ORDER BY error_code), '') FROM call_record WHERE trace_id LIKE '${TRACE_PREFIX}-%' AND error_code IS NOT NULL")"
BILLING_SUMMARY="$(sql "SELECT count(*), coalesce(sum(final_amount), 0), sum(CASE WHEN interface_id = ${FIXTURE_INTERFACE_ID} THEN 1 ELSE 0 END) FROM billing_event WHERE request_id IN (SELECT request_id FROM call_record WHERE trace_id LIKE '${TRACE_PREFIX}-%')")"
IFS='|' read -r CALL_COUNT TOTAL_COST CACHE_HITS ERROR_RECORDS INTERFACE_FACTS CONNECTOR_FACTS <<< "$CALL_SUMMARY"
IFS='|' read -r BILLING_EVENTS BILLING_AMOUNT BILLING_INTERFACE_FACTS <<< "$BILLING_SUMMARY"
[[ "$INTERFACE_FACTS" -ge "$EXPECTED_RECORDS" && "$CONNECTOR_FACTS" -ge "$EXPECTED_RECORDS" && "$ERROR_RECORDS" -ge 4 ]] || {
  echo "CallRecord连接器事实或错误矩阵不完整" >&2
  exit 1
}
[[ "$BILLING_EVENTS" -ge 1 && "$BILLING_INTERFACE_FACTS" -eq "$BILLING_EVENTS" ]] || {
  echo "BillingEvent未落库" >&2
  exit 1
}

FINAL_FIXTURE_STATE="$(fixture_state)"

printf 'apiE2e=passed\n'
printf 'primaryFlows=single-http,token-business,async-polling\n'
printf 'cacheMissCost=%s cacheHitCost=%s cacheHits=%s\n' "$CACHE_MISS_COST" "$CACHE_HIT_COST" "$CACHE_HITS"
printf 'callRecords=%s interfaceFacts=%s connectorFacts=%s errorRecords=%s errorCodes=%s\n' "$CALL_COUNT" "$INTERFACE_FACTS" "$CONNECTOR_FACTS" "$ERROR_RECORDS" "$ERROR_CODES"
printf 'billingEvents=%s billingInterfaceFacts=%s billingAmount=%s\n' "$BILLING_EVENTS" "$BILLING_INTERFACE_FACTS" "$BILLING_AMOUNT"
printf 'backupRoute=READY sentHttpErrorNoFallback=true circuitFallback=true actualVendorObserved=true\n'
printf 'fixtureRequests=vendor:%s echo:%s fallback:%s token:%s business:%s asyncSubmit:%s asyncPoll:%s\n' \
  "$(fixture_counter "$FINAL_FIXTURE_STATE" vendorRequests)" \
  "$(fixture_counter "$FINAL_FIXTURE_STATE" vendorEchoRequests)" \
  "$(fixture_counter "$FINAL_FIXTURE_STATE" vendorFallbackRequests)" \
  "$(fixture_counter "$FINAL_FIXTURE_STATE" tokenRequests)" \
  "$(fixture_counter "$FINAL_FIXTURE_STATE" businessRequests)" \
  "$(fixture_counter "$FINAL_FIXTURE_STATE" asyncSubmissions)" \
  "$(fixture_counter "$FINAL_FIXTURE_STATE" asyncPolls)"
printf 'legacyInventory=passed total=%s classifications=%s\n' "$INVENTORY_TOTAL" "$INVENTORY_CLASSIFICATIONS"
printf 'migration=STABLE observationGatePassed=true\n'
