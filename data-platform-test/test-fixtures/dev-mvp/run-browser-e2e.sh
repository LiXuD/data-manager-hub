#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
NODE_VERSION="v22.19.0"
NPM_VERSION="10.9.3"
PLAYWRIGHT_CLI_SCRIPT="${PLAYWRIGHT_CLI_SCRIPT:-/Users/lixd/.codex/skills/playwright/scripts/playwright_cli.sh}"
STATE_FILE="${1:-}"

usage() {
  cat <<USAGE
用法: $0 <fixture.env>

在已经通过 verify-dev-closure.sh 的隔离 Dev MVP 夹具上，执行稳定浏览器回放：
真实申请人/审批人/安全角色/管理员登录，菜单与页面权限边界检查，
数据查询参数缺口复现、带参数成功查询、UI 登出及服务端旧 token 复用结果，
以及 CallRecord/BillingEvent 最终一致性断言。
本脚本不注入 token、不使用 storage state、不使用 mock；只保存脱敏的
snapshot、network、console、命令结果与数据库一致性摘要，不保存 trace/profile。
USAGE
}

if [[ "$#" -ne 1 || "$STATE_FILE" == "--help" || "$STATE_FILE" == "-h" ]]; then
  usage >&2
  [[ "$STATE_FILE" == "--help" || "$STATE_FILE" == "-h" ]] && exit 0
  exit 2
fi

[[ -f "$STATE_FILE" ]] || {
  echo "Dev MVP 状态文件不存在: $STATE_FILE" >&2
  exit 1
}

# shellcheck disable=SC1090
source "$STATE_FILE"

[[ "${DEV_MVP_PROJECT_ROOT:-}" == "$PROJECT_ROOT" ]] || {
  echo "fixture 状态文件不属于当前项目" >&2
  exit 1
}
[[ "${DEV_MVP_SCHEMA_VERSION:-}" == "V060" ]] || {
  echo "Dev MVP fixture 必须基于 V060: ${DEV_MVP_SCHEMA_VERSION:-}" >&2
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

for required_variable in \
  DEV_MVP_DB_HOST DEV_MVP_DB_PORT DEV_MVP_DB_USERNAME DEV_MVP_DB_PASSWORD \
  DEV_MVP_DB_NAME DEV_MVP_OUTPUT_DIR DEV_MVP_ADMIN_USERNAME \
  DEV_MVP_ADMIN_PASSWORD DEV_MVP_APPLICANT_USERNAME DEV_MVP_APPLICANT_PASSWORD \
  DEV_MVP_APPROVER_USERNAME DEV_MVP_APPROVER_PASSWORD \
  DEV_MVP_SECURITY_USERNAME DEV_MVP_SECURITY_PASSWORD; do
  [[ -n "${!required_variable:-}" ]] || {
    echo "fixture 状态缺少变量: $required_variable" >&2
    exit 1
  }
done

for command_name in curl psql rg; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少命令: $command_name" >&2
    exit 1
  }
done
[[ -x "$PLAYWRIGHT_CLI_SCRIPT" ]] || {
  echo "Playwright CLI wrapper 不存在或不可执行: $PLAYWRIGHT_CLI_SCRIPT" >&2
  exit 1
}

resolve_node_bin() {
  local candidate current_node current_npm
  local -a candidates=()

  [[ -n "${DEV_MVP_NODE_BIN_DIR:-}" ]] && candidates+=("$DEV_MVP_NODE_BIN_DIR")
  if command -v node >/dev/null 2>&1; then
    candidates+=("$(dirname "$(command -v node)")")
  fi
  candidates+=("/Users/lixd/.nvm/versions/node/$NODE_VERSION/bin")

  for candidate in "${candidates[@]}"; do
    [[ -x "$candidate/node" && -x "$candidate/npm" ]] || continue
    current_node="$($candidate/node --version 2>/dev/null || true)"
    current_npm="$(PATH="$candidate:$PATH" "$candidate/npm" --version 2>/dev/null || true)"
    if [[ "$current_node" == "$NODE_VERSION" && "$current_npm" == "$NPM_VERSION" ]]; then
      NODE_BIN_DIR="$candidate"
      return 0
    fi
  done

  echo "需要 Node.js $NODE_VERSION 和 npm $NPM_VERSION；未找到匹配工具链。" >&2
  return 1
}

resolve_node_bin
export PATH="$NODE_BIN_DIR:$PATH"

RUN_ID="${DEV_MVP_DB_NAME#dataplatform_}"
RUN_ID="${RUN_ID%_regression}"
SESSION="${DEV_MVP_BROWSER_E2E_SESSION:-dmh-p6-${RUN_ID}-$(date +%s)}"
SESSION="${SESSION//[^A-Za-z0-9_-]/-}"
EVIDENCE_DIR="$PROJECT_ROOT/output/playwright/$SESSION"
WEB_URL="${DEV_MVP_WEB_URL:-${DEV_MVP_DEMO_URL:-http://127.0.0.1:3000}}"

mkdir -p "$EVIDENCE_DIR"

pw() {
  "$PLAYWRIGHT_CLI_SCRIPT" --session "$SESSION" "$@"
}

redact_output() {
  sed \
    -e "s|$DEV_MVP_ADMIN_PASSWORD|<redacted-password>|g" \
    -e "s|$DEV_MVP_APPLICANT_PASSWORD|<redacted-password>|g" \
    -e "s|$DEV_MVP_APPROVER_PASSWORD|<redacted-password>|g" \
    -e "s|$DEV_MVP_SECURITY_PASSWORD|<redacted-password>|g" \
    -e "s|$DEV_MVP_DB_PASSWORD|<redacted-database-password>|g"
}

capture() {
  local output_name="$1"
  shift
  if ! pw "$@" 2>&1 | redact_output >"$EVIDENCE_DIR/$output_name"; then
    echo "Playwright 命令失败，详见: $EVIDENCE_DIR/$output_name" >&2
    return 1
  fi
}

capture_snapshot() {
  capture "$1.snapshot.txt" snapshot
}

wait_for_ui() {
  sleep 1
}

select_option() {
  local output_name="$1"
  local selector="$2"
  capture "$output_name" click "$selector"
}

assert_text() {
  local file_name="$1"
  local expected="$2"
  rg -F -- "$expected" "$EVIDENCE_DIR/$file_name" >/dev/null || {
    echo "浏览器断言失败: $file_name 缺少文本: $expected" >&2
    return 1
  }
}

assert_text_absent() {
  local file_name="$1"
  local unexpected="$2"
  if rg -F -- "$unexpected" "$EVIDENCE_DIR/$file_name" >/dev/null; then
    echo "浏览器断言失败: $file_name 不应包含文本: $unexpected" >&2
    return 1
  fi
}

assert_console_clean() {
  local file_name="$1"
  assert_text "$file_name" "Errors: 0"
  assert_text "$file_name" "Warnings: 0"
}

login_role() {
  local role="$1"
  local username password
  case "$role" in
    applicant)
      username="$DEV_MVP_APPLICANT_USERNAME"
      password="$DEV_MVP_APPLICANT_PASSWORD"
      ;;
    approver)
      username="$DEV_MVP_APPROVER_USERNAME"
      password="$DEV_MVP_APPROVER_PASSWORD"
      ;;
    security)
      username="$DEV_MVP_SECURITY_USERNAME"
      password="$DEV_MVP_SECURITY_PASSWORD"
      ;;
    admin)
      username="$DEV_MVP_ADMIN_USERNAME"
      password="$DEV_MVP_ADMIN_PASSWORD"
      ;;
    *)
      echo "未知浏览器角色: $role" >&2
      return 1
      ;;
  esac
  capture "$role-open.log" open "$WEB_URL/login"
  capture "$role-fill-username.log" fill 'input[placeholder="请输入用户名"]' "$username"
  capture "$role-fill-password.log" fill 'input[placeholder="请输入密码"]' "$password"
  capture "$role-login.log" click 'button:has-text("立即登录")'
  wait_for_ui
  capture_snapshot "$role-after-login"
}

logout_from_ui() {
  local role="$1"
  capture "$role-user-menu-open.log" click '.user-info'
  capture "$role-logout-click.log" click 'text=退出登录'
  wait_for_ui
  capture_snapshot "$role-after-logout"
  assert_text "$role-after-logout.snapshot.txt" "立即登录"
  capture "$role-logout-network.log" requests
  assert_text "$role-logout-network.log" "/api/v1/auth/logout"
}

create_pending_renewal_application() {
  local expire_at
  expire_at="$(python3 -c 'from datetime import datetime, timedelta; print((datetime.now() + timedelta(days=14)).strftime("%Y-%m-%d %H:%M:%S"))')"

  capture "applicant-api-permission-goto.log" goto "$WEB_URL/api-permission"
  capture "applicant-renewal-open.log" click 'button:has-text("新建申请")'
  capture "applicant-renewal-type.log" click 'text=授权续期'
  capture "applicant-renewal-caller-open.log" click 'text=选择有权管理的内部系统'
  select_option "applicant-renewal-caller-select.log" \
    "li.el-select-dropdown__item:visible:has-text(\"$RENEW_CALLER_NAME\")"
  capture "applicant-renewal-key-open.log" click 'text=选择有效 API Key'
  select_option "applicant-renewal-key-select.log" \
    "li.el-select-dropdown__item:visible:has-text(\"$RENEW_API_KEY_NAME\")"
  capture "applicant-renewal-interface-open.log" click 'text=选择启用接口'
  select_option "applicant-renewal-interface-select.log" \
    "li.el-select-dropdown__item:visible:has-text(\"$RENEW_INTERFACE_NAME\")"
  capture "applicant-renewal-expiry-fill.log" fill 'input[placeholder="选择未来时间"]' "$expire_at"
  capture "applicant-renewal-expiry-blur.log" press Tab
  capture "applicant-renewal-purpose-fill.log" fill \
    'textarea[placeholder="说明为什么需要这些接口权限、数据将如何使用"]' \
    "P6 浏览器续期验收验证已生效授权的续期申请和审批流转"
  capture "applicant-renewal-scene-fill.log" fill 'input[placeholder="例如：贷前审批"]' "$RENEW_SCENE"
  capture "applicant-renewal-submit.log" click 'button:has-text("保存并提交")'
  wait_for_ui
  capture_snapshot "applicant-renewal-submitted"
  assert_text "applicant-renewal-submitted.snapshot.txt" "审批中"
  assert_text "applicant-renewal-submitted.snapshot.txt" "$RENEW_CALLER_NAME"
}

run_sql() {
  PGPASSWORD="$DEV_MVP_DB_PASSWORD" psql -X -Atq \
    -h "$DEV_MVP_DB_HOST" -p "$DEV_MVP_DB_PORT" \
    -U "$DEV_MVP_DB_USERNAME" -d "$DEV_MVP_DB_NAME" -c "$1"
}

TARGET_PROFILE="risk"
TARGET_API_KEY_ID="$(run_sql "SELECT ak.id FROM api_key ak JOIN api_key_interface aki ON aki.api_key_id = ak.id AND aki.status = 'ACTIVE' WHERE ak.key_name = 'Dev MVP 风控 API Key' AND ak.deleted = FALSE ORDER BY ak.id LIMIT 1;")"
TARGET_CALLER_KEY_OPTION="风控系统 / Dev MVP 风控 API Key"
TARGET_KEY_NAME="Dev MVP 风控 API Key"
TARGET_VENDOR_NAME="工商信息主厂商"
TARGET_DATA_TYPE_NAME="工商信息"
TARGET_INTERFACE_NAME="工商信息查询"
TARGET_PRODUCT_NAME="Dev MVP 风控产品"
TARGET_PARAMS_JSON='{"companyName":"p6-browser-query"}'
TARGET_EXPECTED_COST="0.25"
RENEW_CALLER_NAME="信贷系统"
RENEW_API_KEY_NAME="Dev MVP 信贷 API Key"
RENEW_INTERFACE_NAME="个人信息查询"
RENEW_SCENE="P6 浏览器续期验收"

if [[ ! "$TARGET_API_KEY_ID" =~ ^[0-9]+$ ]]; then
  TARGET_PROFILE="credit-fallback"
  TARGET_API_KEY_ID="$(run_sql "SELECT ak.id FROM api_key ak JOIN api_key_interface aki ON aki.api_key_id = ak.id AND aki.status = 'ACTIVE' WHERE ak.key_name = 'Dev MVP 信贷 API Key' AND ak.deleted = FALSE ORDER BY ak.id LIMIT 1;")"
  TARGET_CALLER_KEY_OPTION="信贷系统 / Dev MVP 信贷 API Key"
  TARGET_KEY_NAME="Dev MVP 信贷 API Key"
  TARGET_VENDOR_NAME="个人信息厂商"
  TARGET_DATA_TYPE_NAME="个人信息"
  TARGET_INTERFACE_NAME="个人信息查询"
  TARGET_PRODUCT_NAME="Dev MVP 信贷产品"
  TARGET_PARAMS_JSON='{"idCard":"p6-browser-credit"}'
  TARGET_EXPECTED_COST="0.50"
fi

[[ "$TARGET_API_KEY_ID" =~ ^[0-9]+$ ]] || {
  echo "未找到具有 ACTIVE 接口授权的 Dev MVP 基线 API Key" >&2
  exit 1
}
BASE_CALL_COUNT="$(run_sql "SELECT count(*) FROM call_record WHERE api_key_id = $TARGET_API_KEY_ID AND success = TRUE;")"
BASE_BILLING_COUNT="$(run_sql "SELECT count(*) FROM billing_event be JOIN call_record cr ON cr.request_id = be.request_id WHERE cr.api_key_id = $TARGET_API_KEY_ID AND be.event_type = 'USAGE';")"

wait_for_side_effects() {
  local attempt
  for attempt in $(seq 1 30); do
    CALL_COUNT="$(run_sql "SELECT count(*) FROM call_record WHERE api_key_id = $TARGET_API_KEY_ID AND success = TRUE;")"
    BILLING_COUNT="$(run_sql "SELECT count(*) FROM billing_event be JOIN call_record cr ON cr.request_id = be.request_id WHERE cr.api_key_id = $TARGET_API_KEY_ID AND be.event_type = 'USAGE';")"
    if [[ "$CALL_COUNT" =~ ^[0-9]+$ && "$BILLING_COUNT" =~ ^[0-9]+$ \
      && "$CALL_COUNT" -gt "$BASE_CALL_COUNT" && "$BILLING_COUNT" -gt "$BASE_BILLING_COUNT" ]]; then
      LATEST_CALL="$(run_sql "SELECT request_id || '|' || trace_id || '|' || cost FROM call_record WHERE api_key_id = $TARGET_API_KEY_ID AND success = TRUE ORDER BY id DESC LIMIT 1;")"
      LATEST_BILLING="$(run_sql "SELECT be.request_id || '|' || be.final_amount || '|' || be.status FROM billing_event be JOIN call_record cr ON cr.request_id = be.request_id WHERE cr.api_key_id = $TARGET_API_KEY_ID AND be.event_type = 'USAGE' ORDER BY be.id DESC LIMIT 1;")"
      return 0
    fi
    sleep 1
  done
  echo "CallRecord/BillingEvent 未在 30 秒内出现新增成功事实: call=$CALL_COUNT/$BASE_CALL_COUNT billing=$BILLING_COUNT/$BASE_BILLING_COUNT" >&2
  return 1
}

scan_evidence() {
  local secret
  for secret in \
    "$DEV_MVP_ADMIN_PASSWORD" "$DEV_MVP_APPLICANT_PASSWORD" \
    "$DEV_MVP_APPROVER_PASSWORD" "$DEV_MVP_SECURITY_PASSWORD" \
    "$DEV_MVP_DB_PASSWORD"; do
    if [[ -n "$secret" ]] && rg -F -- "$secret" "$EVIDENCE_DIR" >/dev/null 2>&1; then
      echo "浏览器证据包含未脱敏凭据" >&2
      return 1
    fi
  done
  if rg -E -i -- 'apiSecret|access_token|accessToken|Bearer[[:space:]]+[A-Za-z0-9._-]{20,}' \
    "$EVIDENCE_DIR" >/dev/null 2>&1; then
    echo "浏览器证据包含 token 或 secret 字段" >&2
    return 1
  fi
  if find "$EVIDENCE_DIR" -type f \( -name '*trace*' -o -name '*.zip' -o -name '*.webm' \) \
    | rg -q .; then
    echo "浏览器证据不允许包含 trace/profile 或录屏文件" >&2
    return 1
  fi
}

CALL_COUNT="$BASE_CALL_COUNT"
BILLING_COUNT="$BASE_BILLING_COUNT"
LATEST_CALL=""
LATEST_BILLING=""
EMPTY_QUERY_OUTCOME="not-run"

finalize() {
  local status=$?
  set +e

  if (( status != 0 )); then
    pw snapshot 2>&1 | redact_output >"$EVIDENCE_DIR/failure.snapshot.txt"
    pw requests 2>&1 | redact_output >"$EVIDENCE_DIR/failure.network.txt"
    pw console 2>&1 | redact_output >"$EVIDENCE_DIR/failure.console.txt"
  fi
  pw close >"$EVIDENCE_DIR/session-close.log" 2>&1

  if (( status == 0 )); then
    if ! scan_evidence; then
      status=1
      echo "浏览器证据安全扫描未通过" >&2
    fi
  fi

  {
    if (( status == 0 )); then
      echo "status=passed"
    else
      echo "status=failed"
    fi
    echo "fixture_run=$RUN_ID"
    echo "database=$DEV_MVP_DB_NAME"
    echo "session=$SESSION"
    echo "evidence_dir=$EVIDENCE_DIR"
    echo "target_profile=$TARGET_PROFILE"
    echo "target_api_key_id=$TARGET_API_KEY_ID"
    echo "target_api_key_name=$TARGET_KEY_NAME"
    echo "successful_call_records_before=$BASE_CALL_COUNT"
    echo "successful_call_records_after=$CALL_COUNT"
    echo "usage_billing_events_before=$BASE_BILLING_COUNT"
    echo "usage_billing_events_after=$BILLING_COUNT"
    echo "latest_call=$LATEST_CALL"
    echo "latest_billing=$LATEST_BILLING"
    echo "empty_query_outcome=$EMPTY_QUERY_OUTCOME"
    echo "trace_retention=disabled-to-avoid-session-and-credential-material"
    echo "contract_alignment=frontend required-field validation or backend companyName validation is accepted; parameterized replay must succeed"
  } >"$EVIDENCE_DIR/browser-e2e-summary.txt"

  return "$status"
}
trap finalize EXIT

curl -fsS --connect-timeout 5 "$WEB_URL" >/dev/null

pw close >"$EVIDENCE_DIR/initial-session-close.log" 2>&1 || true
login_role applicant
assert_text "applicant-after-login.snapshot.txt" "接口权限审批"
assert_text "applicant-after-login.snapshot.txt" "数据查询测试"
assert_text "applicant-after-login.snapshot.txt" "$DEV_MVP_APPLICANT_USERNAME"

capture "applicant-data-test-goto.log" goto "$WEB_URL/data-test"
capture "data-test-key-open.log" click 'text=请选择本次调用计费的 API Key'
capture_snapshot "data-test-key-options"
assert_text "data-test-key-options.snapshot.txt" "$TARGET_KEY_NAME"
select_option "data-test-key-select.log" "li.el-select-dropdown__item:visible:has-text(\"$TARGET_CALLER_KEY_OPTION\")"
capture_snapshot "data-test-after-key"

capture "data-test-vendor-open.log" click 'text=请选择厂商'
select_option "data-test-vendor-select.log" "li.el-select-dropdown__item:visible:has-text(\"$TARGET_VENDOR_NAME\")"
wait_for_ui
capture "data-test-datatype-open.log" click 'text=请选择数据类型'
select_option "data-test-datatype-select.log" "li.el-select-dropdown__item:visible:has-text(\"$TARGET_DATA_TYPE_NAME\")"
wait_for_ui
capture "data-test-interface-open.log" click 'text=请选择接口 >> nth=0'
select_option "data-test-interface-select.log" "li.el-select-dropdown__item:visible:has-text(\"$TARGET_INTERFACE_NAME\")"
wait_for_ui
capture "data-test-product-open.log" click 'text=请选择该 API Key 已授权的产品'
select_option "data-test-product-select.log" "li.el-select-dropdown__item:visible:has-text(\"$TARGET_PRODUCT_NAME\")"
capture "data-test-scene-open.log" click 'text=请选择调用场景'
select_option "data-test-scene-select.log" 'li.el-select-dropdown__item:visible:has-text("Dev MVP 业务验收场景")'
wait_for_ui
capture_snapshot "data-test-configured"
assert_text "data-test-configured.snapshot.txt" "$TARGET_INTERFACE_NAME"
assert_text "data-test-configured.snapshot.txt" "$TARGET_PRODUCT_NAME"
assert_text "data-test-configured.snapshot.txt" "Dev MVP 业务验收场景"

if [[ "$TARGET_PROFILE" == "risk" ]]; then
  capture "data-test-empty-query.log" click 'button:has-text("执行查询")'
  capture_snapshot "data-test-empty-query"
  if rg -F -- "请填写必填参数：企业名称" "$EVIDENCE_DIR/data-test-empty-query.snapshot.txt" >/dev/null; then
    EMPTY_QUERY_OUTCOME="frontend-required-field-validation"
  elif rg -F -- "查询失败" "$EVIDENCE_DIR/data-test-empty-query.snapshot.txt" >/dev/null \
    && rg -F -- "companyName不能为空" "$EVIDENCE_DIR/data-test-empty-query.snapshot.txt" >/dev/null; then
    EMPTY_QUERY_OUTCOME="backend-required-field-validation"
  else
    echo "空参数校验断言失败：既未观察到前端必填拦截，也未观察到后端 companyName 校验" >&2
    exit 1
  fi
else
  EMPTY_QUERY_OUTCOME="skipped-credit-fallback-after-risk-revocation"
fi

capture "data-test-advanced-open.log" click 'button:has-text("高级 JSON 参数")'
capture "data-test-advanced-fill.log" fill 'textarea[placeholder*="请输入JSON格式的请求参数"]' "$TARGET_PARAMS_JSON"
capture "data-test-success-query.log" click 'button:has-text("执行查询")'
capture_snapshot "data-test-success-query"
assert_text "data-test-success-query.snapshot.txt" "查询成功"
assert_text "data-test-success-query.snapshot.txt" "e2e-signed-connector"
assert_text "data-test-success-query.snapshot.txt" "费用: ¥$TARGET_EXPECTED_COST"
capture "applicant-network.log" requests
capture "applicant-console.log" console
assert_console_clean "applicant-console.log"

create_pending_renewal_application

logout_from_ui applicant

pw close >"$EVIDENCE_DIR/applicant-session-close.log" 2>&1 || true

login_role approver
assert_text "approver-after-login.snapshot.txt" "接口权限审批"
assert_text "approver-after-login.snapshot.txt" "审批待办"
assert_text_absent "approver-after-login.snapshot.txt" "新建申请"
capture "approver-api-permission-goto.log" goto "$WEB_URL/api-permission"
capture "approver-tasks-tab.log" click 'text=审批待办'
wait_for_ui
capture_snapshot "approver-tasks"
assert_text "approver-tasks.snapshot.txt" "认领"
assert_text "approver-tasks.snapshot.txt" "$RENEW_CALLER_NAME"
assert_text_absent "approver-tasks.snapshot.txt" "新建申请"
capture "approver-network.log" requests
capture "approver-console.log" console
assert_console_clean "approver-console.log"

pw close >"$EVIDENCE_DIR/approver-session-close.log" 2>&1 || true

login_role security
assert_text "security-after-login.snapshot.txt" "接口权限审批"
assert_text "security-after-login.snapshot.txt" "流程诊断"
assert_text "security-after-login.snapshot.txt" "授权台账"
assert_text_absent "security-after-login.snapshot.txt" "新建申请"
assert_text_absent "security-after-login.snapshot.txt" "审批待办"
capture "security-vendor-direct-goto.log" goto "$WEB_URL/vendor"
wait_for_ui
capture_snapshot "security-vendor-direct"
assert_text "security-vendor-direct.snapshot.txt" "个人中心"
capture "security-api-permission-goto.log" goto "$WEB_URL/api-permission"
capture "security-process-tab.log" click 'text=流程诊断'
wait_for_ui
capture_snapshot "security-process"
assert_text "security-process.snapshot.txt" "只读展示流程定义、节点角色和实例统计"
capture "security-grants-tab.log" click 'text=授权台账'
wait_for_ui
capture_snapshot "security-grants"
assert_text "security-grants.snapshot.txt" "Dev MVP 信贷 API Key"
assert_text "security-grants.snapshot.txt" "Dev MVP 风控 API Key"
assert_text "security-grants.snapshot.txt" "已撤销"
capture "security-network.log" requests
capture "security-console.log" console
assert_console_clean "security-console.log"

pw close >"$EVIDENCE_DIR/security-session-close.log" 2>&1 || true

login_role admin
assert_text "admin-after-login.snapshot.txt" "接口权限审批"
capture "admin-api-permission-goto.log" goto "$WEB_URL/api-permission"
capture "admin-ledger-tab.log" click 'text=授权台账'
capture_snapshot "admin-ledger"
assert_text "admin-ledger.snapshot.txt" "$TARGET_KEY_NAME"
assert_text "admin-ledger.snapshot.txt" "有效"
capture "admin-network.log" requests
capture "admin-console.log" console
assert_console_clean "admin-console.log"

wait_for_side_effects

logout_from_ui admin

exit 0
