#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
STATE_FILE="${1:-}"
GATEWAY_URL="${DEV_MVP_GATEWAY_URL:-http://127.0.0.1:8888}"

[[ -f "$STATE_FILE" ]] || {
  echo "用法: $0 <fixture.env>" >&2
  exit 2
}

# shellcheck disable=SC1090
source "$STATE_FILE"
[[ "${DEV_MVP_PROJECT_ROOT:-}" == "$PROJECT_ROOT" ]] || {
  echo "fixture 状态文件不属于当前项目" >&2
  exit 1
}
[[ "${DEV_MVP_DB_NAME:-}" =~ ^dataplatform_dev_mvp_[0-9]{14}_[0-9]+_regression$ ]] || {
  echo "拒绝使用非隔离 Dev MVP 数据库: ${DEV_MVP_DB_NAME:-}" >&2
  exit 1
}
[[ "${DEV_MVP_OUTPUT_DIR:-}" =~ ^${PROJECT_ROOT}/data-platform-test/test-fixtures/\.runtime/dev_mvp_[0-9]{14}_[0-9]+$ ]] || {
  echo "拒绝使用非 Dev MVP 运行目录: ${DEV_MVP_OUTPUT_DIR:-}" >&2
  exit 1
}
[[ "${DEV_MVP_SCHEMA_VERSION:-}" == "V060" ]] || {
  echo "Dev MVP fixture 必须基于 V060: ${DEV_MVP_SCHEMA_VERSION:-}" >&2
  exit 1
}

for command_name in curl jq psql python3; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少命令: $command_name" >&2
    exit 1
  }
done

WORK_DIR="$DEV_MVP_OUTPUT_DIR/api-work"
mkdir -p "$WORK_DIR"
chmod 700 "$WORK_DIR"
redact_json_diagnostics() {
  local json_file redacted_file
  while IFS= read -r -d '' json_file; do
    redacted_file="${json_file}.redacted"
    if jq 'walk(
        if type == "object" then
          with_entries(
            if (.key | ascii_downcase | test("apikey|apisecret|token|password|secret"))
            then .value = "<redacted-sensitive-value>"
            else .
            end)
        else .
        end)' "$json_file" >"$redacted_file" 2>/dev/null; then
      mv -f -- "$redacted_file" "$json_file"
    else
      rm -f -- "$redacted_file"
    fi
  done < <(find "$WORK_DIR" -type f -name '*.json' -print0)
}
cleanup() {
  local status=$?
  if [[ "$status" -eq 0 ]]; then
    rm -rf -- "$WORK_DIR"
  else
    redact_json_diagnostics
    echo "Dev MVP API 失败诊断目录已保留: $WORK_DIR" >&2
  fi
  exit "$status"
}
trap cleanup EXIT

export PGPASSWORD="$DEV_MVP_DB_PASSWORD"
PSQL=(
  psql -X -v ON_ERROR_STOP=1
  -h "$DEV_MVP_DB_HOST" -p "$DEV_MVP_DB_PORT"
  -U "$DEV_MVP_DB_USERNAME"
)
sql() {
  "${PSQL[@]}" -Atq -d "$DEV_MVP_DB_NAME" -c "$1"
}

AUTH_TOKEN=""
ADMIN_TOKEN=""
APPLICANT_TOKEN=""
RESPONSE_FILE=""
HTTP_CODE="000"
API_CALL_NUMBER=0
TRACE_PREFIX="dev-mvp-api-${DEV_MVP_RUN_TOKEN}-$$"
PUBLIC_TRACE_IDS=""
PLUGIN_ACTIVATED=false
BUSINESS_FAILOVER_TRACE_ID=""
LOGOUT_REPLAY_PASSED=false
RISK_GRANT_REVOKED=false
REVOKED_KEY_FORBIDDEN=false

api_call() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local extra_header="${4:-}"
  local response_path="$WORK_DIR/api-${API_CALL_NUMBER}.json"
  local error_path="$WORK_DIR/api-${API_CALL_NUMBER}.err"
  local -a curl_args

  API_CALL_NUMBER=$((API_CALL_NUMBER + 1))
  RESPONSE_FILE="$response_path"
  curl_args=(curl -skS --connect-timeout 5 --max-time 30)
  [[ -n "$AUTH_TOKEN" ]] && curl_args+=(-H "Authorization: Bearer $AUTH_TOKEN")
  [[ -n "$body" ]] && curl_args+=(-H 'Content-Type: application/json')
  [[ -n "$extra_header" ]] && curl_args+=(-H "$extra_header")
  curl_args+=(-X "$method" "$GATEWAY_URL$path")
  [[ -n "$body" ]] && curl_args+=(--data-binary "$body")
  HTTP_CODE="$("${curl_args[@]}" -o "$response_path" -w '%{http_code}' \
    2>"$error_path" || true)"
}

result_code() {
  jq -r '.code // 0' "$RESPONSE_FILE" 2>/dev/null || printf '0'
}

expect_result() {
  local label="$1"
  [[ "$HTTP_CODE" == "200" && "$(result_code)" == "200" ]] || {
    echo "${label}失败: HTTP=$HTTP_CODE code=$(result_code)" >&2
    exit 1
  }
}

expect_forbidden() {
  local label="$1"
  [[ "$HTTP_CODE" == "403" && "$(result_code)" == "403" ]] || {
    echo "${label}未按预期拒绝: HTTP=$HTTP_CODE code=$(result_code)" >&2
    exit 1
  }
}

assert_response() {
  local label="$1"
  shift
  jq -e "$@" "$RESPONSE_FILE" >/dev/null 2>&1 || {
    echo "${label}响应不满足验收条件" >&2
    exit 1
  }
}

require_value() {
  local label="$1"
  local value="$2"
  [[ -n "$value" && "$value" != "null" ]] || {
    echo "缺少 Dev MVP 事实: $label" >&2
    exit 1
  }
}

run_migration_gate() {
  local validate_log="$WORK_DIR/migrate-validate.log"
  local status_log="$WORK_DIR/migrate-status.log"
  if ! (
    cd "$PROJECT_ROOT"
    DB_HOST="$DEV_MVP_DB_HOST" DB_PORT="$DEV_MVP_DB_PORT" \
      DB_USERNAME="$DEV_MVP_DB_USERNAME" DB_PASSWORD="$DEV_MVP_DB_PASSWORD" \
      DB_NAME="$DEV_MVP_DB_NAME" ./migrate-db.sh validate
  ) >"$validate_log" 2>&1; then
    tail -80 "$validate_log" >&2 || true
    echo "Dev MVP Liquibase validate 未通过" >&2
    exit 1
  fi
  if ! (
    cd "$PROJECT_ROOT"
    DB_HOST="$DEV_MVP_DB_HOST" DB_PORT="$DEV_MVP_DB_PORT" \
      DB_USERNAME="$DEV_MVP_DB_USERNAME" DB_PASSWORD="$DEV_MVP_DB_PASSWORD" \
      DB_NAME="$DEV_MVP_DB_NAME" ./migrate-db.sh status
  ) >"$status_log" 2>&1; then
    tail -80 "$status_log" >&2 || true
    echo "Dev MVP Liquibase status 未通过" >&2
    exit 1
  fi
  if ! grep -Eiq 'up to date|no changesets|没有.*待(执行|更新)' "$status_log"; then
    cat "$status_log" >&2
    echo "Dev MVP 数据库仍有待执行迁移，要求 V060 pending=0" >&2
    exit 1
  fi
  [[ "$(sql "SELECT count(*) FROM databasechangelog WHERE id IN ('widen-call-record-error-code-2026-08-27', 'bind-call-record-interface-identity-2026-08-28', 'management-permission-matrix-2026-09-01', 'serialize-billing-plan-publish-2026-09-01', 'complete-operation-log-tenant-scope-2026-09-02', 'preserve-config-version-encryption-2026-09-02', 'widen-alert-record-type-2026-09-02', 'repair-api-key-permission-parent-links-2026-09-02') AND author = 'data-platform' AND exectype = 'EXECUTED'")" == "8" ]] || {
    echo "V051—V058 整改迁移未全部记录为 EXECUTED" >&2
    exit 1
  }
  [[ "$(sql "SELECT count(*) FROM permission child JOIN permission parent ON parent.permission_code = 'caller:view' AND child.parent_id = parent.id WHERE child.permission_code IN ('apikey:view', 'apikey:add', 'apikey:edit', 'apikey:delete')")" == "4" ]] || {
    echo "V058 API Key 权限目录父级未按 permission_code 正确修复" >&2
    exit 1
  }
  [[ "$(sql "SELECT count(*) FROM databasechangelog WHERE id = 'scope-call-scene-by-tenant-2026-09-03' AND author = 'data-platform' AND exectype = 'EXECUTED'")" == "1" ]] || {
    echo "V059 调用场景租户范围迁移未记录为 EXECUTED" >&2
    exit 1
  }
  [[ "$(sql "SELECT count(*) FROM databasechangelog WHERE id = 'separate-call-record-cache-payload-2026-09-03' AND author = 'data-platform' AND exectype = 'EXECUTED'")" == "1" ]] || {
    echo "V060 调用记录缓存载荷迁移未记录为 EXECUTED" >&2
    exit 1
  }
}

load_seed_facts() {
  local token="$DEV_MVP_RUN_TOKEN"
  TENANT_ID="$(sql "SELECT id FROM tenant_info WHERE tenant_code = 'dev-mvp-tenant-$token' AND deleted = false")"
  ADMIN_USER_ID="$(sql "SELECT id FROM user_info WHERE username = 'dev-mvp-admin-$token' AND deleted = false")"
  APPLICANT_USER_ID="$(sql "SELECT id FROM user_info WHERE username = 'dev-mvp-applicant-$token' AND deleted = false")"
  PRIMARY_VENDOR_ID="$(sql "SELECT id FROM vendor_info WHERE vendor_code = 'dev-mvp-risk-primary-$token' AND deleted = false")"
  BACKUP_VENDOR_ID="$(sql "SELECT id FROM vendor_info WHERE vendor_code = 'dev-mvp-risk-backup-$token' AND deleted = false")"
  PERSONAL_VENDOR_ID="$(sql "SELECT id FROM vendor_info WHERE vendor_code = 'dev-mvp-personal-$token' AND deleted = false")"
  BUSINESS_DATA_TYPE_ID="$(sql "SELECT id FROM data_type WHERE data_type_code = 'business-registration-$token' AND deleted = false")"
  PERSONAL_DATA_TYPE_ID="$(sql "SELECT id FROM data_type WHERE data_type_code = 'personal-information-$token' AND deleted = false")"
  BUSINESS_INTERFACE_ID="$(sql "SELECT id FROM api_interface WHERE interface_code = 'DEV_MVP_BUSINESS_$token' AND deleted = false")"
  PERSONAL_INTERFACE_ID="$(sql "SELECT id FROM api_interface WHERE interface_code = 'DEV_MVP_PERSONAL_$token' AND deleted = false")"
  PRIMARY_CONFIG_ID="$(sql "SELECT vc.id FROM vendor_config vc JOIN vendor_info v ON v.id = vc.vendor_id WHERE v.vendor_code = 'dev-mvp-risk-primary-$token' AND vc.interface_id = $BUSINESS_INTERFACE_ID AND vc.deleted = false")"
  BACKUP_CONFIG_ID="$(sql "SELECT vc.id FROM vendor_config vc JOIN vendor_info v ON v.id = vc.vendor_id WHERE v.vendor_code = 'dev-mvp-risk-backup-$token' AND vc.interface_id = $BUSINESS_INTERFACE_ID AND vc.deleted = false")"
  PERSONAL_CONFIG_ID="$(sql "SELECT vc.id FROM vendor_config vc JOIN vendor_info v ON v.id = vc.vendor_id WHERE v.vendor_code = 'dev-mvp-personal-$token' AND vc.interface_id = $PERSONAL_INTERFACE_ID AND vc.deleted = false")"
  BUSINESS_INTERFACE_CODE="DEV_MVP_BUSINESS_$token"
  PERSONAL_INTERFACE_CODE="DEV_MVP_PERSONAL_$token"
  BUSINESS_DATA_TYPE_CODE="business-registration-$token"
  PERSONAL_DATA_TYPE_CODE="personal-information-$token"
  SCENE_CODE="dev-mvp-scene-$token"
  PRIMARY_VENDOR_CODE="dev-mvp-risk-primary-$token"
  BACKUP_VENDOR_CODE="dev-mvp-risk-backup-$token"
  PERSONAL_VENDOR_CODE="dev-mvp-personal-$token"

  require_value tenant "$TENANT_ID"
  require_value admin-user "$ADMIN_USER_ID"
  require_value applicant-user "$APPLICANT_USER_ID"
  require_value primary-vendor "$PRIMARY_VENDOR_ID"
  require_value backup-vendor "$BACKUP_VENDOR_ID"
  require_value personal-vendor "$PERSONAL_VENDOR_ID"
  require_value business-data-type "$BUSINESS_DATA_TYPE_ID"
  require_value personal-data-type "$PERSONAL_DATA_TYPE_ID"
  require_value business-interface "$BUSINESS_INTERFACE_ID"
  require_value personal-interface "$PERSONAL_INTERFACE_ID"
  require_value primary-config "$PRIMARY_CONFIG_ID"
  require_value backup-config "$BACKUP_CONFIG_ID"
  require_value personal-config "$PERSONAL_CONFIG_ID"

  [[ "$(sql "SELECT count(*) FROM user_role ur JOIN role_info r ON r.id = ur.role_id WHERE ur.user_id = $ADMIN_USER_ID AND ur.deleted = false AND r.role_code IN ('api_interface_approver', 'platform_security_admin') AND r.deleted = false")" == "0" ]] || {
    echo "Dev MVP 管理员与审批/安全角色发生耦合" >&2
    exit 1
  }

  local counts
  counts="$(sql "SELECT
      (SELECT count(*) FROM vendor_info WHERE vendor_code IN ('dev-mvp-risk-primary-$token', 'dev-mvp-risk-backup-$token', 'dev-mvp-personal-$token') AND deleted = false),
      (SELECT count(*) FROM data_type WHERE data_type_code IN ('business-registration-$token', 'personal-information-$token') AND deleted = false),
      (SELECT count(*) FROM api_interface WHERE interface_code IN ('DEV_MVP_BUSINESS_$token', 'DEV_MVP_PERSONAL_$token') AND deleted = false),
      (SELECT count(*) FROM vendor_config WHERE id IN ($PRIMARY_CONFIG_ID, $BACKUP_CONFIG_ID, $PERSONAL_CONFIG_ID) AND deleted = false),
      (SELECT count(*) FROM caller_info WHERE caller_code IN ('dev-mvp-risk-control-$token', 'dev-mvp-credit-$token') AND deleted = false),
      (SELECT count(*) FROM billing_plan WHERE plan_code IN ('DEV-MVP-PRIMARY-$token', 'DEV-MVP-BACKUP-$token', 'DEV-MVP-PERSONAL-$token'))")"
  IFS='|' read -r SEED_VENDOR_COUNT SEED_DATA_TYPE_COUNT SEED_INTERFACE_COUNT \
    SEED_CONFIG_COUNT SEED_CALLER_COUNT SEED_PLAN_COUNT <<< "$counts"
  [[ "$SEED_VENDOR_COUNT" == "3" && "$SEED_DATA_TYPE_COUNT" == "2" \
      && "$SEED_INTERFACE_COUNT" == "2" && "$SEED_CONFIG_COUNT" == "3" \
      && "$SEED_CALLER_COUNT" == "2" && "$SEED_PLAN_COUNT" == "3" ]] || {
    echo "Dev MVP seed cardinality 不满足 3/2/2/3/2/3" >&2
    exit 1
  }
}

login_as() {
  local role="$1"
  local username="$2"
  local password="$3"
  RESPONSE_FILE="$WORK_DIR/login-$role.json"
  HTTP_CODE="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H 'Content-Type: application/json' -X POST "$GATEWAY_URL/api/v1/auth/login" \
    --data-binary "$(jq -nc --arg username "$username" --arg password "$password" \
      '{username:$username,password:$password}')" \
    -o "$RESPONSE_FILE" -w '%{http_code}' 2>"$WORK_DIR/login-$role.err" || true)"
  local token
  token="$(jq -r '.data.token // .data.accessToken // empty' "$RESPONSE_FILE" 2>/dev/null || true)"
  [[ "$HTTP_CODE" == "200" && -n "$token" ]] || {
    echo "$role 登录失败: HTTP=$HTTP_CODE" >&2
    exit 1
  }
  AUTH_TOKEN="$token"
  case "$role" in
    admin) ADMIN_TOKEN="$token" ;;
    applicant) APPLICANT_TOKEN="$token" ;;
    approver) APPROVER_TOKEN="$token" ;;
    security) SECURITY_TOKEN="$token" ;;
    *) echo "未知 Dev MVP 角色: $role" >&2; exit 1 ;;
  esac
}

logout_replay_check() {
  local login_response="$WORK_DIR/logout-replay-login.json"
  local logout_response="$WORK_DIR/logout-replay-logout.json"
  local userinfo_response="$WORK_DIR/logout-replay-userinfo.json"
  local protected_response="$WORK_DIR/logout-replay-protected.json"
  local login_error="$WORK_DIR/logout-replay-login.err"
  local logout_error="$WORK_DIR/logout-replay-logout.err"
  local userinfo_error="$WORK_DIR/logout-replay-userinfo.err"
  local protected_error="$WORK_DIR/logout-replay-protected.err"
  local login_http replay_token logout_http userinfo_http protected_http
  local login_body

  login_body="$(jq -nc \
    --arg username "$DEV_MVP_APPLICANT_USERNAME" \
    --arg password "$DEV_MVP_APPLICANT_PASSWORD" \
    '{username:$username,password:$password}')"
  login_http="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H 'Content-Type: application/json' -X POST "$GATEWAY_URL/api/v1/auth/login" \
    --data-binary "$login_body" -o "$login_response" -w '%{http_code}' \
    2>"$login_error" || true)"
  replay_token="$(jq -r '.data.token // .data.accessToken // empty' "$login_response" 2>/dev/null || true)"
  [[ "$login_http" == "200" && -n "$replay_token" ]] || {
    echo "登出回放登录失败: HTTP=$login_http" >&2
    exit 1
  }

  logout_http="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer $replay_token" -X POST "$GATEWAY_URL/api/v1/auth/logout" \
    -o "$logout_response" -w '%{http_code}' 2>"$logout_error" || true)"
  [[ "$logout_http" == "200" ]] && jq -e '.code == 200' "$logout_response" >/dev/null 2>&1 || {
    echo "服务端登出失败: HTTP=$logout_http" >&2
    exit 1
  }

  userinfo_http="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer $replay_token" -X GET "$GATEWAY_URL/api/v1/auth/userinfo" \
    -o "$userinfo_response" -w '%{http_code}' 2>"$userinfo_error" || true)"
  [[ "$userinfo_http" == "401" ]] && jq -e '.code == 401' "$userinfo_response" >/dev/null 2>&1 || {
    echo "登出后旧 token 访问 userinfo 未返回 401: HTTP=$userinfo_http" >&2
    exit 1
  }

  protected_http="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H "Authorization: Bearer $replay_token" -X GET \
    "$GATEWAY_URL/api/v1/vendor/list?page=1&pageSize=1" \
    -o "$protected_response" -w '%{http_code}' 2>"$protected_error" || true)"
  [[ "$protected_http" == "401" ]] && jq -e '.code == 401' "$protected_response" >/dev/null 2>&1 || {
    echo "登出后旧 token 访问受保护资源未返回 401: HTTP=$protected_http" >&2
    exit 1
  }
  LOGOUT_REPLAY_PASSED=true
}

catalog_checks() {
  api_call GET "/api/v1/vendor/list?page=1&pageSize=100&keyword=$DEV_MVP_RUN_TOKEN"
  expect_result "厂商目录"
  assert_response "厂商目录" --arg token "$DEV_MVP_RUN_TOKEN" '
    (.data | map(select(.vendorCode == ("dev-mvp-risk-primary-" + $token)
      or .vendorCode == ("dev-mvp-risk-backup-" + $token)
      or .vendorCode == ("dev-mvp-personal-" + $token))) | length) == 3'

  api_call GET "/api/v1/datatype/list?page=1&pageSize=100&keyword=$DEV_MVP_RUN_TOKEN"
  expect_result "数据类型目录"
  assert_response "数据类型目录" --arg token "$DEV_MVP_RUN_TOKEN" '
    (.data | map(select(.dataTypeCode == ("business-registration-" + $token)
      or .dataTypeCode == ("personal-information-" + $token))) | length) == 2'

  api_call GET "/api/v1/interface/list?page=1&pageSize=100"
  expect_result "接口目录"
  assert_response "接口目录" --arg token "$DEV_MVP_RUN_TOKEN" '
    (.data | map(select(.interfaceCode == ("DEV_MVP_BUSINESS_" + $token)
      or .interfaceCode == ("DEV_MVP_PERSONAL_" + $token))) | length) == 2'

  api_call GET "/api/v1/vendor/config/list?interfaceId=$BUSINESS_INTERFACE_ID"
  expect_result "工商接口厂商配置"
  assert_response "工商接口厂商配置" '.data | length == 2'

  api_call GET "/api/v1/vendor/config/list?interfaceId=$PERSONAL_INTERFACE_ID"
  expect_result "个人接口厂商配置"
  assert_response "个人接口厂商配置" '.data | length == 1'

  api_call GET "/api/v1/interface/$BUSINESS_INTERFACE_ID/contract"
  expect_result "工商接口契约"
  assert_response "工商接口契约" '
    (.data.requestFields | any(.[]; .paramName == "companyName" and .required == true))
    and (.data.responseFields | any(.[]; .paramName == "success"))'
}

wait_plugin_ready() {
  local attempt
  for attempt in $(seq 1 60); do
    api_call GET "/api/v1/connector-plugin/$PLUGIN_ID/versions/$PLUGIN_VERSION/activation"
    if [[ "$HTTP_CODE" == "200" ]] && jq -e '
      .code == 200 and .data.ready == true
      and ((.data.instances // []) | length >= 1)
      and all(.data.instances[]; .state == "READY")
    ' "$RESPONSE_FILE" >/dev/null 2>&1; then
      return
    fi
    sleep 0.5
  done
  echo "签名插件未达到单实例 READY" >&2
  exit 1
}

ensure_plugin() {
  PLUGIN_ID="e2e-signed-connector"
  PLUGIN_VERSION="1.1.0"
  api_call GET "/api/v1/connector-plugin/$PLUGIN_ID/versions/$PLUGIN_VERSION/activation"
  local already_ready=0
  if [[ "$HTTP_CODE" == "200" ]] && jq -e '.code == 200 and .data.ready == true' "$RESPONSE_FILE" >/dev/null 2>&1; then
    already_ready=1
  fi

  if [[ "$already_ready" == "0" ]]; then
    api_call POST /api/v1/connector-plugin/versions/import "$(jq -nc \
      --arg artifactUri "$FIXTURE_ARTIFACT_URI" \
      --arg expectedSha256 "$FIXTURE_ARTIFACT_SHA256" \
      --arg detachedSignature "$FIXTURE_DETACHED_SIGNATURE" \
      --arg signingKeyId "$FIXTURE_SIGNING_KEY_ID" \
      '{artifactUri:$artifactUri,expectedSha256:$expectedSha256,detachedSignature:$detachedSignature,signingKeyId:$signingKeyId}')"
    if [[ "$HTTP_CODE" != "200" || "$(result_code)" != "200" ]]; then
      jq -e '((.msg // .message // "") | test("已存在|already|duplicate"; "i"))' "$RESPONSE_FILE" >/dev/null 2>&1 || \
        expect_result "导入签名插件"
    fi

    api_call POST "/api/v1/connector-plugin/$PLUGIN_ID/versions/$PLUGIN_VERSION/stage"
    if [[ "$HTTP_CODE" != "200" || "$(result_code)" != "200" ]]; then
      jq -e '((.msg // .message // "") | test("已在|already|STAGING|READY"; "i"))' "$RESPONSE_FILE" >/dev/null 2>&1 || \
        expect_result "预加载签名插件"
    fi
  fi

  wait_plugin_ready
  api_call GET "/api/v1/connector-plugin"
  expect_result "读取插件目录"
  assert_response "插件目录" '.data | any(.[]; .pluginId == "e2e-signed-connector")'
}

ensure_secret_ref() {
  SECRET_REF="dev.mvp.client-secret"
  SECRET_VALUE="dev-mvp-secret-$DEV_MVP_RUN_TOKEN"
  local existing
  existing="$(sql "SELECT id FROM vendor_config_extended WHERE vendor_id = $PERSONAL_VENDOR_ID AND config_key = '$SECRET_REF' AND status = 'active' LIMIT 1")"
  if [[ -n "$existing" ]]; then
    return
  fi
  api_call POST /api/v1/vendor/extended-config "$(jq -nc \
    --argjson vendorId "$PERSONAL_VENDOR_ID" \
    --arg configKey "$SECRET_REF" \
    --arg configValue "$SECRET_VALUE" \
    '{vendorId:$vendorId,configKey:$configKey,configValue:$configValue,configType:"secret",description:"Dev MVP token flow secret",isEncrypted:true,isActive:true}')"
  expect_result "创建 Dev MVP SecretRef"
}

spec_single() {
  local endpoint="$1"
  jq -nc --arg endpoint "$endpoint" '{
    specVersion:"1",
    plugin:{pluginId:"e2e-signed-connector",pluginVersion:"1.1.0"},
    config:{flow:"single-http",endpoint:$endpoint,connectTimeoutMs:2000,readTimeoutMs:5000,totalTimeoutMs:8000,maxResponseBytes:1048576},
    responseMapping:null
  }'
}

spec_token_business() {
  local endpoint_base="$1"
  jq -nc --arg tokenEndpoint "$endpoint_base/vendor/token" \
    --arg businessEndpoint "$endpoint_base/vendor/business" \
    --arg clientSecret "$SECRET_REF" '{
      specVersion:"1",
      plugin:{pluginId:"e2e-signed-connector",pluginVersion:"1.1.0"},
      config:{flow:"token-business",tokenEndpoint:$tokenEndpoint,businessEndpoint:$businessEndpoint,clientId:"dev-mvp-client",clientSecret:$clientSecret,connectTimeoutMs:2000,readTimeoutMs:5000,totalTimeoutMs:8000,maxResponseBytes:1048576},
      responseMapping:null
    }'
}

configure_connector() {
  local config_id="$1"
  local spec_json="$2"
  local test_params="$3"
  local label="$4"
  local draft_version

  api_call GET "/api/v1/vendor/config/$config_id/connector-spec/draft"
  expect_result "读取${label}连接器草稿"
  draft_version="$(jq -r '.data.draftVersion // 0' "$RESPONSE_FILE")"
  [[ "$draft_version" =~ ^[0-9]+$ ]] || {
    echo "${label}草稿版本无效" >&2
    exit 1
  }

  api_call PUT "/api/v1/vendor/config/$config_id/connector-spec/draft" \
    "$(jq -nc --argjson expectedDraftVersion "$draft_version" \
      --argjson connectorSpec "$spec_json" \
      '{expectedDraftVersion:$expectedDraftVersion,connectorSpec:$connectorSpec}')"
  expect_result "保存${label}连接器草稿"
  draft_version="$(jq -r '.data.draftVersion' "$RESPONSE_FILE")"

  api_call POST "/api/v1/vendor/config/$config_id/connector-spec/validate"
  expect_result "校验${label}连接器"
  assert_response "校验${label}连接器" '.data.valid == true and (.data.compiledSnapshotHash | type == "string")'

  api_call POST "/api/v1/vendor/config/$config_id/connector-spec/test" \
    "$(jq -nc --argjson params "$test_params" '{params:$params}')"
  expect_result "受控测试${label}连接器"
  assert_response "受控测试${label}连接器" '.data.success == true'

  if [[ "$PLUGIN_ACTIVATED" != true ]]; then
    activate_plugin
    PLUGIN_ACTIVATED=true
  fi

  api_call POST "/api/v1/vendor/config/$config_id/connector-spec/publish" \
    "$(jq -nc --argjson expectedDraftVersion "$draft_version" \
      '{expectedDraftVersion:$expectedDraftVersion}')"
  expect_result "发布${label}连接器"

  api_call PATCH "/api/v1/vendor/config/$config_id/status" '{"status":"active"}'
  expect_result "启用${label}厂商配置"
}

configure_connectors() {
  local endpoint_base="${DEV_MVP_FIXTURE_ENDPOINT_BASE:-${FIXTURE_ARTIFACT_URI%/e2e-signed-connector/*}}"
  ensure_secret_ref
  configure_connector "$PRIMARY_CONFIG_ID" "$(spec_single "$endpoint_base/vendor/echo")" \
    '{"companyName":"dev-mvp-primary"}' "工商主厂商"
  configure_connector "$BACKUP_CONFIG_ID" "$(spec_single "$endpoint_base/vendor/fallback")" \
    '{"companyName":"dev-mvp-backup"}' "工商备厂商"
  configure_connector "$PERSONAL_CONFIG_ID" "$(spec_token_business "$endpoint_base")" \
    '{"idCard":"dev-mvp-id-card"}' "个人Token厂商"
}

activate_plugin() {
  api_call POST "/api/v1/connector-plugin/$PLUGIN_ID/versions/$PLUGIN_VERSION/activate"
  if [[ "$HTTP_CODE" != "200" || "$(result_code)" != "200" ]]; then
    jq -e '((.msg // .message // "") | test("已激活|already active|只有STAGING"; "i"))' "$RESPONSE_FILE" >/dev/null 2>&1 || \
      expect_result "激活签名插件"
  fi
}

configure_routes() {
  api_call PUT "/api/v1/interface/$BUSINESS_INTERFACE_ID/vendor-routing" \
    "$(jq -nc --argjson primaryVendorConfigId "$PRIMARY_CONFIG_ID" \
      --argjson fallbackVendorConfigId "$BACKUP_CONFIG_ID" \
      '{primaryVendorConfigId:$primaryVendorConfigId,fallbackVendorConfigId:$fallbackVendorConfigId}')"
  expect_result "保存工商主备路由"
  assert_response "工商主备路由" \
    --argjson primary "$PRIMARY_CONFIG_ID" --argjson fallback "$BACKUP_CONFIG_ID" \
    '.data.primaryVendorConfigId == $primary and .data.fallbackVendorConfigId == $fallback
      and .data.routingReadiness == "READY"'

  api_call PUT "/api/v1/interface/$PERSONAL_INTERFACE_ID/vendor-routing" \
    "$(jq -nc --argjson primaryVendorConfigId "$PERSONAL_CONFIG_ID" \
      '{primaryVendorConfigId:$primaryVendorConfigId,fallbackVendorConfigId:null}')"
  expect_result "保存个人主路由"
  assert_response "个人主路由" --argjson primary "$PERSONAL_CONFIG_ID" \
    '.data.primaryVendorConfigId == $primary and .data.fallbackVendorConfigId == null
      and .data.routingReadiness == "READY"'

  api_call PATCH "/api/v1/interface/$BUSINESS_INTERFACE_ID/status" '{"status":"active"}'
  expect_result "启用工商接口"
  api_call PATCH "/api/v1/interface/$PERSONAL_INTERFACE_ID/status" '{"status":"active"}'
  expect_result "启用个人接口"

  [[ "$(sql "SELECT count(*) FROM api_interface WHERE id IN ($BUSINESS_INTERFACE_ID, $PERSONAL_INTERFACE_ID) AND status = 'active'")" == "2" ]] || {
    echo "Dev MVP 接口未全部启用" >&2
    exit 1
  }
  [[ "$(sql "SELECT count(*) FROM vendor_config vc JOIN vendor_connector_version v ON v.id = vc.active_connector_version_id WHERE vc.id IN ($PRIMARY_CONFIG_ID, $BACKUP_CONFIG_ID, $PERSONAL_CONFIG_ID) AND vc.status = 'active' AND v.status = 'ACTIVE' AND v.authoring_mode = 'SIMPLE_CONNECTOR' AND v.connector_spec->'plugin'->>'pluginId' = 'e2e-signed-connector' AND v.connector_spec->'plugin'->>'pluginVersion' = '1.1.0'")" == "3" ]] || {
    echo "Dev MVP 三个厂商配置未全部绑定 SIMPLE_CONNECTOR 活动版本" >&2
    exit 1
  }
}

create_products_and_keys() {
  RISK_CALLER_ID="$(sql "SELECT id FROM caller_info WHERE caller_code = 'dev-mvp-risk-control-$DEV_MVP_RUN_TOKEN' AND deleted = false")"
  CREDIT_CALLER_ID="$(sql "SELECT id FROM caller_info WHERE caller_code = 'dev-mvp-credit-$DEV_MVP_RUN_TOKEN' AND deleted = false")"
  require_value risk-caller "$RISK_CALLER_ID"
  require_value credit-caller "$CREDIT_CALLER_ID"

  RISK_PRODUCT_CODE="DEV_MVP_RISK_$DEV_MVP_RUN_TOKEN"
  CREDIT_PRODUCT_CODE="DEV_MVP_CREDIT_$DEV_MVP_RUN_TOKEN"

  api_call POST "/api/v1/caller/$RISK_CALLER_ID/products" "$(jq -nc \
    --arg productCode "$RISK_PRODUCT_CODE" \
    '{productCode:$productCode,productName:"Dev MVP 风控产品",cacheScope:"CALLER",status:"active"}')"
  expect_result "创建风控产品"
  RISK_PRODUCT_ID="$(jq -r '.data.id // empty' "$RESPONSE_FILE")"
  require_value risk-product "$RISK_PRODUCT_ID"

  api_call POST "/api/v1/caller/$CREDIT_CALLER_ID/products" "$(jq -nc \
    --arg productCode "$CREDIT_PRODUCT_CODE" \
    '{productCode:$productCode,productName:"Dev MVP 信贷产品",cacheScope:"CALLER",status:"active"}')"
  expect_result "创建信贷产品"
  CREDIT_PRODUCT_ID="$(jq -r '.data.id // empty' "$RESPONSE_FILE")"
  require_value credit-product "$CREDIT_PRODUCT_ID"

  api_call POST /api/v1/caller/apikey "$(jq -nc \
    --argjson callerId "$RISK_CALLER_ID" --arg name "Dev MVP 风控 API Key" \
    --argjson productIds "[$RISK_PRODUCT_ID]" \
    '{callerId:$callerId,name:$name,productIds:$productIds}')"
  expect_result "创建风控 API Key"
  RISK_API_KEY_ID="$(jq -r '.data.id // empty' "$RESPONSE_FILE")"
  RISK_API_KEY_VALUE="$(jq -r '.data.apiKey // empty' "$RESPONSE_FILE")"
  require_value risk-api-key-id "$RISK_API_KEY_ID"
  require_value risk-api-key-value "$RISK_API_KEY_VALUE"

  api_call POST /api/v1/caller/apikey "$(jq -nc \
    --argjson callerId "$CREDIT_CALLER_ID" --arg name "Dev MVP 信贷 API Key" \
    --argjson productIds "[$CREDIT_PRODUCT_ID]" \
    '{callerId:$callerId,name:$name,productIds:$productIds}')"
  expect_result "创建信贷 API Key"
  CREDIT_API_KEY_ID="$(jq -r '.data.id // empty' "$RESPONSE_FILE")"
  CREDIT_API_KEY_VALUE="$(jq -r '.data.apiKey // empty' "$RESPONSE_FILE")"
  require_value credit-api-key-id "$CREDIT_API_KEY_ID"
  require_value credit-api-key-value "$CREDIT_API_KEY_VALUE"
}

applicant_option_checks() {
  api_call GET /api/v1/api-permission/eligible-callers
  expect_result "读取可申请调用方"
  assert_response "可申请调用方" --argjson risk "$RISK_CALLER_ID" --argjson credit "$CREDIT_CALLER_ID" \
    '.data | (map(.id) | index($risk)) != null and (map(.id) | index($credit)) != null'

  api_call GET "/api/v1/api-permission/callers/$RISK_CALLER_ID/api-keys"
  expect_result "读取风控 API Key 选项"
  assert_response "风控 API Key 选项" --argjson key "$RISK_API_KEY_ID" '.data | any(.[]; .id == $key)'

  api_call GET "/api/v1/api-permission/interface-options?apiKeyId=$RISK_API_KEY_ID"
  expect_result "读取接口申请选项"
  assert_response "接口申请选项" --argjson interfaceId "$BUSINESS_INTERFACE_ID" \
    '.data | any(.[]; .id == $interfaceId and .status == "active" and .granted == false)'
}

create_and_submit_application() {
  local caller_id="$1"
  local api_key_id="$2"
  local interface_id="$3"
  local label="$4"
  local request_idempotency="dev-mvp-application-${DEV_MVP_RUN_TOKEN}-${label}"
  local body

  body="$(jq -nc --argjson callerId "$caller_id" --argjson apiKeyId "$api_key_id" \
    --argjson interfaceId "$interface_id" --arg expireAt "$REQUESTED_EXPIRE_AT" \
    --arg ticketNo "DEV-MVP-$DEV_MVP_RUN_TOKEN-$label" --arg scene "$SCENE_CODE" \
    '{requestType:"OPEN",callerId:$callerId,apiKeyId:$apiKeyId,interfaceIds:[$interfaceId],
      businessPurpose:"Dev MVP 业务接口真实调用验收",businessScene:$scene,expectedDailyCalls:1000,
      requestedExpireAt:$expireAt,ticketNo:$ticketNo,cacheEnabled:true,requestedCacheDays:1}')"
  api_call POST /api/v1/api-permission/applications "$body"
  expect_result "创建${label}权限申请"
  local application_id
  application_id="$(jq -r '.data.id // empty' "$RESPONSE_FILE")"
  require_value "${label}权限申请" "$application_id"
  api_call POST "/api/v1/api-permission/applications/$application_id/submit" "" \
    "Idempotency-Key: $request_idempotency"
  expect_result "提交${label}权限申请"
  assert_response "提交${label}权限申请" '.data.status == "IN_REVIEW" and (.data.processInstanceId | type == "string")'
  if [[ "$label" == "risk" ]]; then
    RISK_APPLICATION_ID="$application_id"
  else
    CREDIT_APPLICATION_ID="$application_id"
  fi
}

approve_application() {
  local application_id="$1"
  local label="$2"
  local task_id=""
  local attempt

  for attempt in $(seq 1 30); do
    api_call GET /api/v1/api-permission/tasks
    expect_result "读取${label}审批任务"
    task_id="$(jq -r --argjson applicationId "$application_id" \
      '.data[] | select(.application.id == $applicationId) | .task.id' "$RESPONSE_FILE" 2>/dev/null | head -1)"
    [[ -n "$task_id" && "$task_id" != "null" ]] && break
    sleep 1
  done
  require_value "${label}审批任务" "$task_id"

  api_call POST "/api/v1/api-permission/tasks/$task_id/claim"
  expect_result "认领${label}审批任务"

  api_call GET "/api/v1/api-permission/applications/$application_id"
  expect_result "读取${label}权限申请版本"
  local application_version
  application_version="$(jq -r '.data.application.version // empty' "$RESPONSE_FILE")"
  [[ "$application_version" =~ ^[0-9]+$ ]] || {
    echo "${label}权限申请版本无效" >&2
    exit 1
  }

  api_call POST "/api/v1/api-permission/tasks/$task_id/complete" "$(jq -nc \
    --argjson applicationVersion "$application_version" \
    --arg expireAt "$REQUESTED_EXPIRE_AT" \
    '{applicationVersion:$applicationVersion,decision:"APPROVE",approvedExpireAt:$expireAt,
      comment:"Dev MVP 审批闭环",formData:{},approvedCacheEnabled:true,approvedCacheDays:1}')"
  expect_result "完成${label}权限审批"
  assert_response "完成${label}权限审批" '.data.status == "EFFECTIVE"'
}

public_call() {
  local api_key="$1"
  local api_code="$2"
  local product_code="$3"
  local params_json="$4"
  local label="$5"
  local trace_id="${TRACE_PREFIX}-${label}"
  local response_path="$WORK_DIR/public-$label.json"
  local error_path="$WORK_DIR/public-$label.err"
  local request_id="${trace_id}-request"
  local body

  body="$(jq -nc --arg requestId "$request_id" --arg apiCode "$api_code" \
    --arg productCode "$product_code" --arg sceneCode "$SCENE_CODE" \
    --argjson params "$params_json" \
    '{requestId:$requestId,apiCode:$apiCode,apiVersion:"v1",productCode:$productCode,
      sceneCode:$sceneCode,useCache:false,cacheDays:1,params:$params}')"
  HTTP_CODE="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H 'Content-Type: application/json' -H "X-Api-Key: $api_key" \
    -H "X-Trace-Id: $trace_id" -X POST "$GATEWAY_URL/openapi/v1/query" \
    --data-binary "$body" -o "$response_path" -w '%{http_code}' \
    2>"$error_path" || true)"
  PUBLIC_RESPONSE_FILE="$response_path"
  [[ "$HTTP_CODE" == "200" ]] && jq -e '.code == 200 and .data.success == true' "$response_path" >/dev/null 2>&1 || {
    echo "${label} OpenAPI 调用未成功: HTTP=$HTTP_CODE" >&2
    exit 1
  }
  PUBLIC_TRACE_IDS="${PUBLIC_TRACE_IDS}${PUBLIC_TRACE_IDS:+|}$trace_id"
}

public_call_maybe_failed() {
  local api_key="$1"
  local api_code="$2"
  local product_code="$3"
  local params_json="$4"
  local label="$5"
  local trace_id="${TRACE_PREFIX}-${label}"
  local response_path="$WORK_DIR/public-$label.json"
  local error_path="$WORK_DIR/public-$label.err"
  local request_id="${trace_id}-request"
  local body

  body="$(jq -nc --arg requestId "$request_id" --arg apiCode "$api_code" \
    --arg productCode "$product_code" --arg sceneCode "$SCENE_CODE" \
    --argjson params "$params_json" \
    '{requestId:$requestId,apiCode:$apiCode,apiVersion:"v1",productCode:$productCode,
      sceneCode:$sceneCode,useCache:false,cacheDays:1,params:$params}')"
  HTTP_CODE="$(curl -skS --connect-timeout 5 --max-time 30 \
    -H 'Content-Type: application/json' -H "X-Api-Key: $api_key" \
    -H "X-Trace-Id: $trace_id" -X POST "$GATEWAY_URL/openapi/v1/query" \
    --data-binary "$body" -o "$response_path" -w '%{http_code}' \
    2>"$error_path" || true)"
  PUBLIC_RESPONSE_FILE="$response_path"
  PUBLIC_TRACE_IDS="${PUBLIC_TRACE_IDS}${PUBLIC_TRACE_IDS:+|}$trace_id"
}

primary_backup_call() {
  local attempt
  local label
  local params='{"companyName":"dev-mvp-failover-call","probe":"http-error"}'

  # HTTP failures are SENT and therefore must not immediately use a backup.
  # The host circuit opens only after its count-based window is full; the next
  # request is NOT_SENT and is the point where the explicit backup route applies.
  for attempt in $(seq 1 32); do
    label="business-failover-$attempt"
    public_call_maybe_failed "$RISK_API_KEY_VALUE" "$BUSINESS_INTERFACE_CODE" \
      "$RISK_PRODUCT_CODE" "$params" "$label"
    if [[ "$HTTP_CODE" == "200" ]] && jq -e \
      '.code == 200 and .data.success == true' "$PUBLIC_RESPONSE_FILE" >/dev/null 2>&1; then
      BUSINESS_FAILOVER_TRACE_ID="${TRACE_PREFIX}-${label}"
      return
    fi
    [[ "$HTTP_CODE" == "200" ]] && jq -e \
      '.code == 200 and .data.success == false and .data.errorCode == "TRANSPORT_HTTP_ERROR"' \
      "$PUBLIC_RESPONSE_FILE" >/dev/null 2>&1 || {
        echo "工商主备切换出现非预期响应: attempt=$attempt HTTP=$HTTP_CODE" >&2
        exit 1
      }
  done
  echo "工商主备切换未在限定次数内成功" >&2
  exit 1
}

wait_for_records_and_billing() {
  local record_count=0
  local billing_count=0
  local attempt
  for attempt in $(seq 1 60); do
    record_count="$(sql "SELECT count(*) FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%'")"
    billing_count="$(sql "SELECT count(*) FROM billing_event WHERE request_id IN (SELECT request_id FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%')")"
    if [[ "$record_count" -ge 3 && "$billing_count" -ge 3 ]]; then
      return
    fi
    sleep 1
  done
  echo "CallRecord/BillingEvent 未在限定时间内完整落库: records=$record_count billing=$billing_count" >&2
  exit 1
}

observation_checks() {
  api_call GET "/api/v1/call-record/list?page=1&pageSize=20&apiCode=$BUSINESS_INTERFACE_CODE"
  expect_result "读取 CallRecord"
  api_call GET "/api/v1/call-record/stats"
  expect_result "读取 CallRecord 统计"
  api_call GET "/api/v1/billing/list?page=1&pageSize=20&tenantId=$TENANT_ID"
  expect_result "读取 Billing"
  api_call GET "/api/v1/billing/stats?tenantId=$TENANT_ID"
  expect_result "读取 Billing 统计"
  api_call GET "/api/v1/billing/event/list?page=1&pageSize=20&tenantId=$TENANT_ID"
  expect_result "读取 BillingEvent"
  api_call GET '/api/v1/log/list?page=1&pageSize=20'
  expect_result "读取审计日志"
  AUDIT_API_PASSED=true
  api_call GET /api/v1/alert/health/list
  expect_result "读取健康监控"
  MONITOR_API_PASSED=true
}

revoke_risk_grant_and_verify_old_key() {
  local grant_id
  local records_before billing_before records_after billing_after

  api_call GET /api/v1/api-permission/grants
  expect_result "安全角色读取撤销前授权台账"
  grant_id="$(jq -r --argjson apiKeyId "$RISK_API_KEY_ID" \
    '.data[] | select(.apiKeyId == $apiKeyId and .status == "ACTIVE") | .id' \
    "$RESPONSE_FILE" 2>/dev/null | head -1)"
  require_value "风控 API Key 活跃授权" "$grant_id"

  api_call POST "/api/v1/api-permission/grants/$grant_id/revoke" \
    '{"reason":"Dev MVP 安全角色撤销旧授权"}'
  expect_result "安全角色撤销风控授权"
  assert_response "安全角色撤销风控授权" \
    --argjson grantId "$grant_id" \
    '.data.id == $grantId and .data.status == "REVOKED"'
  RISK_GRANT_REVOKED=true

  records_before="$(sql "SELECT count(*) FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%'")"
  billing_before="$(sql "SELECT count(*) FROM billing_event WHERE request_id IN (SELECT request_id FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%')")"
  public_call_maybe_failed "$RISK_API_KEY_VALUE" "$BUSINESS_INTERFACE_CODE" \
    "$RISK_PRODUCT_CODE" '{"companyName":"dev-mvp-revoked-key"}' revoked-key
  [[ "$HTTP_CODE" == "403" ]] && jq -e '.code == 403' "$PUBLIC_RESPONSE_FILE" >/dev/null 2>&1 || {
    echo "撤销后的旧 API Key 未被拒绝: HTTP=$HTTP_CODE" >&2
    exit 1
  }
  REVOKED_KEY_FORBIDDEN=true
  records_after="$(sql "SELECT count(*) FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%'")"
  billing_after="$(sql "SELECT count(*) FROM billing_event WHERE request_id IN (SELECT request_id FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%')")"
  [[ "$records_after" == "$records_before" && "$billing_after" == "$billing_before" ]] || {
    echo "撤销后的旧 API Key 不应新增 CallRecord/BillingEvent: records=$records_before->$records_after billing=$billing_before->$billing_after" >&2
    exit 1
  }
}

write_report() {
  local counts call_facts billing_facts application_facts grant_facts
  counts="$(sql "SELECT count(DISTINCT vendor_id), count(DISTINCT caller_id), count(DISTINCT data_type_code), count(DISTINCT interface_id), count(*) FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%'")"
  call_facts="$(sql "SELECT count(*) FILTER (WHERE interface_id IS NOT NULL), count(*) FILTER (WHERE plugin_id = 'e2e-signed-connector' AND plugin_version = '1.1.0' AND pipeline_version IS NOT NULL AND length(trim(snapshot_hash)) = 64), count(*) FILTER (WHERE vendor_id = $PRIMARY_VENDOR_ID), count(*) FILTER (WHERE vendor_id = $BACKUP_VENDOR_ID), count(*) FILTER (WHERE vendor_id = $PERSONAL_VENDOR_ID) FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%'")"
  billing_facts="$(sql "SELECT count(*), coalesce(sum(final_amount), 0), count(*) FILTER (WHERE interface_id IN ($BUSINESS_INTERFACE_ID, $PERSONAL_INTERFACE_ID)) FROM billing_event WHERE request_id IN (SELECT request_id FROM call_record WHERE trace_id LIKE '$TRACE_PREFIX-%')")"
  application_facts="$(sql "SELECT count(*) FILTER (WHERE status = 'EFFECTIVE'), count(*) FILTER (WHERE status <> 'EFFECTIVE'), count(*) FROM api_permission_application WHERE id IN ($RISK_APPLICATION_ID, $CREDIT_APPLICATION_ID)")"
  grant_facts="$(sql "SELECT count(*) FILTER (WHERE status = 'ACTIVE' AND grant_source = 'APPROVAL'), count(*) FILTER (WHERE status = 'REVOKED' AND grant_source = 'APPROVAL') FROM api_key_interface WHERE api_key_id IN ($RISK_API_KEY_ID, $CREDIT_API_KEY_ID) AND interface_id IN ($BUSINESS_INTERFACE_ID, $PERSONAL_INTERFACE_ID)")"
  IFS='|' read -r observed_vendors observed_callers observed_data_types observed_interfaces observed_records <<< "$counts"
  IFS='|' read -r interface_facts connector_facts primary_records backup_records personal_records <<< "$call_facts"
  IFS='|' read -r observed_billing billing_amount billing_interface_facts <<< "$billing_facts"
  IFS='|' read -r effective_applications non_effective_applications total_applications <<< "$application_facts"
  IFS='|' read -r active_approval_grants revoked_approval_grants <<< "$grant_facts"

  [[ "$observed_vendors" == "3" && "$observed_callers" == "2" && "$observed_data_types" == "2" \
      && "$observed_interfaces" == "2" && "$observed_records" -ge 3 ]] || {
    echo "OpenAPI 业务事实未形成 3/2/2 闭环: $counts" >&2
    exit 1
  }
  [[ "$interface_facts" == "$observed_records" && "$connector_facts" == "$observed_records" \
      && "$primary_records" -ge 1 && "$backup_records" -ge 1 && "$personal_records" -ge 1 ]] || {
    echo "CallRecord 接口/连接器/主备事实不完整" >&2
    exit 1
  }
  [[ "$(sql "SELECT count(*) FROM call_record WHERE trace_id = '$BUSINESS_FAILOVER_TRACE_ID' AND vendor_id = $BACKUP_VENDOR_ID")" == "1" ]] || {
    echo "主备切换成功请求未保留备用厂商实际事实" >&2
    exit 1
  }
  [[ "$observed_billing" -ge 3 && "$billing_interface_facts" == "$observed_billing" ]] || {
    echo "BillingEvent 事实不完整" >&2
    exit 1
  }
  [[ "$effective_applications" == "2" && "$non_effective_applications" == "0" && "$total_applications" == "2" ]] || {
    echo "权限审批未全部 EFFECTIVE" >&2
    exit 1
  }
  [[ "$active_approval_grants" == "1" && "$revoked_approval_grants" == "1" \
      && "$RISK_GRANT_REVOKED" == true && "$REVOKED_KEY_FORBIDDEN" == true ]] || {
    echo "审批授权及撤销事实不完整: active=$active_approval_grants revoked=$revoked_approval_grants" >&2
    exit 1
  }
  [[ "$LOGOUT_REPLAY_PASSED" == true ]] || {
    echo "登出后旧 token 回放事实不完整" >&2
    exit 1
  }

  jq -n \
    --arg status "passed" \
    --arg schemaVersion "$DEV_MVP_SCHEMA_VERSION" \
    --arg runToken "$DEV_MVP_RUN_TOKEN" \
    --arg database "$DEV_MVP_DB_NAME" \
    --arg gateway "$GATEWAY_URL" \
    --argjson pendingMigrations 0 \
    --argjson vendors "$SEED_VENDOR_COUNT" \
    --argjson callers "$SEED_CALLER_COUNT" \
    --argjson dataTypes "$SEED_DATA_TYPE_COUNT" \
    --argjson interfaces "$SEED_INTERFACE_COUNT" \
    --argjson connectorConfigs "$SEED_CONFIG_COUNT" \
    --argjson observedVendors "$observed_vendors" \
    --argjson observedCallers "$observed_callers" \
    --argjson observedDataTypes "$observed_data_types" \
    --argjson observedInterfaces "$observed_interfaces" \
    --argjson callRecords "$observed_records" \
    --argjson billingEvents "$observed_billing" \
    --argjson billingAmount "$billing_amount" \
    --argjson permissionApplications "$total_applications" \
    --argjson effectiveGrants "$effective_applications" \
    --argjson activeApprovalGrants "$active_approval_grants" \
    --argjson revokedApprovalGrants "$revoked_approval_grants" \
    --argjson logoutReplay401 "$LOGOUT_REPLAY_PASSED" \
    --argjson revokedKeyForbidden "$REVOKED_KEY_FORBIDDEN" \
    --argjson auditApi "$AUDIT_API_PASSED" \
    --argjson monitorApi "$MONITOR_API_PASSED" \
    '{
      status:$status, schemaVersion:$schemaVersion, runToken:$runToken, database:$database,
      gateway:$gateway, pendingMigrations:$pendingMigrations,
      seed:{vendors:$vendors,callers:$callers,dataTypes:$dataTypes,interfaces:$interfaces,connectorConfigs:$connectorConfigs},
      observed:{vendors:$observedVendors,callers:$observedCallers,dataTypes:$observedDataTypes,interfaces:$observedInterfaces,
        callRecords:$callRecords,billingEvents:$billingEvents,billingAmount:$billingAmount},
      connectorFlows:["single-http","token-business","primary-backup"],
      permissionApplications:$permissionApplications,effectiveGrants:$effectiveGrants,
      activeApprovalGrants:$activeApprovalGrants,revokedApprovalGrants:$revokedApprovalGrants,
      logoutReplay401:$logoutReplay401,revokedKeyForbidden:$revokedKeyForbidden,
      auditApi:$auditApi,monitorApi:$monitorApi
      }' > "$DEV_MVP_OUTPUT_DIR/report.json"
  chmod 600 "$DEV_MVP_OUTPUT_DIR/report.json"
  echo "DEV_MVP_REPORT=$DEV_MVP_OUTPUT_DIR/report.json"
}

run_migration_gate
load_seed_facts
login_as admin "$DEV_MVP_ADMIN_USERNAME" "$DEV_MVP_ADMIN_PASSWORD"
catalog_checks
ensure_plugin
configure_connectors
configure_routes
create_products_and_keys
login_as applicant "$DEV_MVP_APPLICANT_USERNAME" "$DEV_MVP_APPLICANT_PASSWORD"
applicant_option_checks
REQUESTED_EXPIRE_AT="$(python3 -c 'from datetime import datetime, timedelta; print((datetime.now() + timedelta(days=7)).replace(microsecond=0).isoformat())')"
create_and_submit_application "$RISK_CALLER_ID" "$RISK_API_KEY_ID" "$BUSINESS_INTERFACE_ID" risk
create_and_submit_application "$CREDIT_CALLER_ID" "$CREDIT_API_KEY_ID" "$PERSONAL_INTERFACE_ID" credit
login_as approver "$DEV_MVP_APPROVER_USERNAME" "$DEV_MVP_APPROVER_PASSWORD"
approve_application "$RISK_APPLICATION_ID" risk
approve_application "$CREDIT_APPLICATION_ID" credit
login_as applicant "$DEV_MVP_APPLICANT_USERNAME" "$DEV_MVP_APPLICANT_PASSWORD"
public_call "$RISK_API_KEY_VALUE" "$BUSINESS_INTERFACE_CODE" "$RISK_PRODUCT_CODE" \
  '{"companyName":"dev-mvp-primary-call"}' business-primary
primary_backup_call
public_call "$CREDIT_API_KEY_VALUE" "$PERSONAL_INTERFACE_CODE" "$CREDIT_PRODUCT_CODE" \
  '{"idCard":"dev-mvp-token-business"}' personal-token-business
wait_for_records_and_billing
logout_replay_check
login_as admin "$DEV_MVP_ADMIN_USERNAME" "$DEV_MVP_ADMIN_PASSWORD"
observation_checks
login_as security "$DEV_MVP_SECURITY_USERNAME" "$DEV_MVP_SECURITY_PASSWORD"
api_call GET "/api/v1/vendor/list?page=1&pageSize=20"
expect_forbidden "安全角色访问厂商管理目录"
api_call GET /api/v1/api-permission/grants
expect_result "安全角色读取授权台账"
assert_response "安全角色读取授权台账" '.data | length == 2'
api_call GET /api/v1/api-permission/process-diagnostics
expect_result "安全角色读取流程诊断"
assert_response "安全角色读取流程诊断" '.data | type == "array"'
revoke_risk_grant_and_verify_old_key
write_report
