#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RUNTIME_ROOT="$PROJECT_ROOT/data-platform-test/test-fixtures/.runtime"
PREPARE_SCRIPT="$SCRIPT_DIR/prepare-dev-mvp.sh"
CLEANUP_SCRIPT="$SCRIPT_DIR/cleanup-dev-mvp.sh"
RUNNER_SCRIPT="$SCRIPT_DIR/run-dev-mvp.sh"
WEB_ROOT="$PROJECT_ROOT/data-platform-web"
GATEWAY_URL="${DEV_MVP_GATEWAY_URL:-http://127.0.0.1:8888}"
WEB_PORT="${DEV_MVP_WEB_PORT:-3000}"
NODE_VERSION="v22.19.0"
NPM_VERSION="10.9.3"
STATE_FILE=""
OWNED_STATE=0
SERVICES_STARTED=0
SERVICES_PID=""
WEB_PID=""
WEB_HTTP_CODE="000"
PREPARE_LOG=""
LATEST_REPORT="$RUNTIME_ROOT/dev-mvp-latest-report.json"
RUN_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RUN_STARTED_EPOCH="$(date +%s)"
KEEP_RUNNING="${DEV_MVP_KEEP_RUNNING:-false}"

usage() {
  cat <<USAGE
用法: $0 [fixture.env] [--keep-running|--demo]

默认在验收成功后停止服务并清理隔离数据库。--keep-running（--demo 别名）
会保留六服务、前端、HTTPS 夹具和隔离数据库，供浏览器产品演示。
USAGE
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --keep-running|--demo)
      KEEP_RUNNING=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      echo "未知选项: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      if [[ -n "$STATE_FILE" ]]; then
        echo "只能提供一个 fixture 状态文件" >&2
        exit 2
      fi
      STATE_FILE="$1"
      ;;
  esac
  shift
done

case "$KEEP_RUNNING" in
  true|false) ;;
  *)
    echo "DEV_MVP_KEEP_RUNNING 必须为 true 或 false" >&2
    exit 2
    ;;
esac

case "${DEV_MVP_SKIP_BUILD:-false}" in
  true) BUILD_MODE=skip-build ;;
  false) BUILD_MODE=full-build ;;
  *)
    echo "DEV_MVP_SKIP_BUILD 必须为 true 或 false" >&2
    exit 2
    ;;
esac

for command_name in curl jq psql python3; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少命令: $command_name" >&2
    exit 1
  }
done

[[ "$WEB_PORT" =~ ^[0-9]+$ ]] || {
  echo "DEV_MVP_WEB_PORT 必须为数字" >&2
  exit 1
}

resolve_node_bin() {
  local candidate current_node current_npm nvm_dir home_dir
  local -a candidates=()

  [[ -n "${DEV_MVP_NODE_BIN_DIR:-}" ]] && candidates+=("$DEV_MVP_NODE_BIN_DIR")
  if command -v node >/dev/null 2>&1; then
    candidates+=("$(dirname "$(command -v node)")")
  fi
  nvm_dir="${NVM_DIR:-}"
  [[ -n "$nvm_dir" ]] && candidates+=("$nvm_dir/versions/node/$NODE_VERSION/bin")
  home_dir="${HOME:-}"
  [[ -n "$home_dir" ]] && candidates+=("$home_dir/.nvm/versions/node/$NODE_VERSION/bin")

  for candidate in "${candidates[@]}"; do
    [[ -x "$candidate/node" && -x "$candidate/npm" ]] || continue
    current_node="$("$candidate/node" --version 2>/dev/null || true)"
    current_npm="$(PATH="$candidate:$PATH" "$candidate/npm" --version 2>/dev/null || true)"
    if [[ "$current_node" == "$NODE_VERSION" && "$current_npm" == "$NPM_VERSION" ]]; then
      NODE_BIN_DIR="$candidate"
      NODE_RUNTIME_VERSION="$current_node"
      NPM_RUNTIME_VERSION="$current_npm"
      return 0
    fi
  done

  echo "需要 Node.js $NODE_VERSION 和 npm $NPM_VERSION；未找到匹配工具链。" >&2
  echo "可通过 DEV_MVP_NODE_BIN_DIR 指定目录，或安装 nvm 的 Node $NODE_VERSION。" >&2
  return 1
}

validate_state() {
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
  [[ "${DEV_MVP_SCHEMA_VERSION:-}" == "V059" ]] || {
    echo "Dev MVP fixture 必须基于 V059: ${DEV_MVP_SCHEMA_VERSION:-}" >&2
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
    DEV_MVP_SECURITY_USERNAME DEV_MVP_SECURITY_PASSWORD \
    FIXTURE_ARTIFACT_URI FIXTURE_ARTIFACT_SHA256 FIXTURE_DETACHED_SIGNATURE \
    FIXTURE_SIGNING_KEY_ID FIXTURE_SIGNING_PUBLIC_KEY_BASE64 \
    FIXTURE_ACCESS_SIGNING_KEY_RESOURCE FIXTURE_JAVA_TLS_OPTIONS; do
    [[ -n "${!required_variable:-}" ]] || {
      echo "fixture 状态缺少变量: $required_variable" >&2
      exit 1
    }
  done
}

prepare_state() {
  mkdir -p "$RUNTIME_ROOT"
  PREPARE_LOG="$(mktemp -t dmh-dev-mvp-prepare.XXXXXX)"
  if ! "$PREPARE_SCRIPT" >"$PREPARE_LOG" 2>&1; then
    cat "$PREPARE_LOG" >&2
    exit 1
  fi
  STATE_FILE="$(sed -n 's/^DEV_MVP_STATE_FILE=//p' "$PREPARE_LOG" | tail -1)"
  [[ -n "$STATE_FILE" ]] || {
    cat "$PREPARE_LOG" >&2
    echo "prepare-dev-mvp.sh 未返回状态文件" >&2
    exit 1
  }
  OWNED_STATE=1
}

ensure_local_infra() {
  command -v docker >/dev/null 2>&1 || {
    echo "缺少 docker；Dev MVP 需要本地 Docker 基础设施" >&2
    exit 1
  }
  [[ -f "$PROJECT_ROOT/docker-compose.local-infra.yml" ]] || {
    echo "本地开发基础设施 Compose 文件不存在" >&2
    exit 1
  }

  local infra_log="$RUNTIME_ROOT/dev-mvp-infra.log"
  local container status attempt
  mkdir -p "$RUNTIME_ROOT"
  : > "$infra_log"
  if ! (
    cd "$PROJECT_ROOT"
    DMH_POSTGRES_DB=dataplatform \
      DMH_POSTGRES_USER="${DEV_MVP_DB_USERNAME:-postgres}" \
      DMH_POSTGRES_PASSWORD="${DEV_MVP_DB_PASSWORD:-123456}" \
      DMH_POSTGRES_PORT="${DEV_MVP_DB_PORT:-15432}" \
      DMH_REDIS_PORT=6379 DMH_REDIS_PASSWORD=redis_password \
      DMH_KAFKA_PORT=9092 DMH_NACOS_PORT=8848 \
      docker compose -f docker-compose.local-infra.yml up -d
  ) >>"$infra_log" 2>&1; then
    tail -120 "$infra_log" >&2 || true
    echo "本地开发基础设施启动失败" >&2
    exit 1
  fi

  for container in dmh-local-postgres dmh-local-redis dmh-local-kafka dmh-local-nacos; do
    for attempt in $(seq 1 90); do
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$container" 2>/dev/null || true)"
      [[ "$status" == "healthy" ]] && break
      if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
        tail -120 "$infra_log" >&2 || true
        echo "基础设施容器状态异常: $container=$status" >&2
        exit 1
      fi
      sleep 1
    done
    [[ "$status" == "healthy" ]] || {
      tail -120 "$infra_log" >&2 || true
      echo "基础设施容器未就绪: $container" >&2
      exit 1
    }
  done
}

stop_web() {
  if [[ -n "$WEB_PID" ]] && kill -0 "$WEB_PID" >/dev/null 2>&1; then
    kill "$WEB_PID" >/dev/null 2>&1 || true
  fi
  if command -v lsof >/dev/null 2>&1; then
    local listener_pid process_command
    for listener_pid in $(lsof -t -iTCP:"$WEB_PORT" -sTCP:LISTEN 2>/dev/null || true); do
      process_command="$(ps -p "$listener_pid" -o command= 2>/dev/null || true)"
      if [[ "$process_command" == *"$WEB_ROOT"* || "$process_command" == *"vite"* ]]; then
        kill "$listener_pid" >/dev/null 2>&1 || true
      fi
    done
  fi
  WEB_PID=""
}

stop_services() {
  [[ "$SERVICES_STARTED" == "1" ]] || return 0
  "$PROJECT_ROOT/stop-services.sh" >"$DEV_MVP_OUTPUT_DIR/stop-services.log" 2>&1 || true
  SERVICES_STARTED=0
}

cleanup() {
  local status=$?
  trap - EXIT
  if [[ "$status" -eq 0 && "$KEEP_RUNNING" == true ]]; then
    [[ -z "$PREPARE_LOG" ]] || rm -f -- "$PREPARE_LOG"
    exit 0
  fi
  stop_web || true
  stop_services || true
  if [[ "$OWNED_STATE" == "1" && -f "$STATE_FILE" ]]; then
    if [[ "$status" -eq 0 ]]; then
      "$CLEANUP_SCRIPT" "$STATE_FILE" >"$RUNTIME_ROOT/dev-mvp-cleanup.log" 2>&1 || true
    else
      "$CLEANUP_SCRIPT" "$STATE_FILE" --keep-output >"$RUNTIME_ROOT/dev-mvp-cleanup.log" 2>&1 || true
      echo "失败诊断目录已保留: ${DEV_MVP_OUTPUT_DIR:-未知}" >&2
    fi
  fi
  [[ -z "$PREPARE_LOG" ]] || rm -f -- "$PREPARE_LOG"
  exit "$status"
}
trap cleanup EXIT

resolve_node_bin
ensure_local_infra
if [[ -z "$STATE_FILE" ]]; then
  prepare_state
fi
validate_state

SERVICES_LOG="$DEV_MVP_OUTPUT_DIR/services.log"
FRONTEND_LOG="$DEV_MVP_OUTPUT_DIR/frontend.log"
FRONTEND_CHECK="$DEV_MVP_OUTPUT_DIR/frontend-check.html"
NPM_USER_CONFIG="$DEV_MVP_OUTPUT_DIR/npm-user-config"
SERVICE_HEALTH="$DEV_MVP_OUTPUT_DIR/service-health.json"
BUSINESS_LOG="$DEV_MVP_OUTPUT_DIR/business-acceptance.log"
mkdir -p "$DEV_MVP_OUTPUT_DIR"
chmod 700 "$DEV_MVP_OUTPUT_DIR"
printf 'registry=https://registry.npmjs.org/\n' > "$NPM_USER_CONFIG"
chmod 600 "$NPM_USER_CONFIG"

FIXTURE_REPOSITORY_PREFIX="${FIXTURE_ARTIFACT_URI%/1.1.0/connector-plugin.jar}"

start_services() {
  : > "$SERVICES_LOG"
  SERVICES_STARTED=1
  if ! (
    cd "$PROJECT_ROOT"
    export SPRING_PROFILES_ACTIVE=dev
    export START_LOCAL_INFRA=true
    export MIGRATE_DB=true
    export NACOS_CONFIG_SYNC=true
    export NACOS_SERVER_ADDR=127.0.0.1:8848
    export NACOS_NAMESPACE="${DEV_MVP_NACOS_NAMESPACE:-dev}"
    export NACOS_GROUP=DEFAULT_GROUP
    export DB_HOST="$DEV_MVP_DB_HOST" DB_PORT="$DEV_MVP_DB_PORT"
    export DB_USERNAME="$DEV_MVP_DB_USERNAME" DB_PASSWORD="$DEV_MVP_DB_PASSWORD"
    export DB_NAME="$DEV_MVP_DB_NAME"
    export DMH_POSTGRES_DB=dataplatform
    export DMH_POSTGRES_USER="$DEV_MVP_DB_USERNAME"
    export DMH_POSTGRES_PASSWORD="$DEV_MVP_DB_PASSWORD"
    export REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD=redis_password
    export KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
    export CONNECTOR_ARTIFACT_REPOSITORY_HOST=127.0.0.1
    export CONNECTOR_ARTIFACT_REPOSITORY_PATH=/e2e-signed-connector
    export CONNECTOR_ARTIFACT_REPOSITORY_PREFIX="$FIXTURE_REPOSITORY_PREFIX"
    export CONNECTOR_VENDOR_ALLOWED_HOST=127.0.0.1
    export CONNECTOR_ALLOW_PRIVATE_NETWORKS=true
    export CONNECTOR_SIGNING_PUBLIC_KEY_BASE64="$FIXTURE_SIGNING_PUBLIC_KEY_BASE64"
    export CONNECTOR_SIGNING_PUBLIC_KEY_RESOURCE="$FIXTURE_ACCESS_SIGNING_KEY_RESOURCE"
    export CONNECTOR_JAVA_TLS_OPTIONS="$FIXTURE_JAVA_TLS_OPTIONS"
    export CONNECTOR_PLUGIN_CACHE_DIR="$DEV_MVP_OUTPUT_DIR/plugins-cache"
    export CONNECTOR_INSTANCE_ID=data-platform-access:8082
    export CONNECTOR_HOST_VERSION=1.0.0
    export SKIP_BUILD="${DEV_MVP_SKIP_BUILD:-false}"
    if [[ "$KEEP_RUNNING" == true ]]; then
      python3 - <<'PY'
import subprocess
import sys

result = subprocess.run(["./start-services.sh"], start_new_session=True, check=False)
sys.exit(result.returncode)
PY
    else
      ./start-services.sh
    fi
  ) >"$SERVICES_LOG" 2>&1; then
    # start-services.sh performs a fixed 45-second check. Access may still be
    # completing Flowable/plugin initialization when that check expires.
    local ready=0 attempt port code
    for attempt in $(seq 1 90); do
      ready=1
      for port in 8086 8081 8084 8085 8082 8888; do
        code="$(curl --noproxy '*' -sS --connect-timeout 2 --max-time 3 \
          -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/actuator/health" 2>/dev/null || true)"
        if [[ "$code" != "200" ]]; then
          ready=0
          break
        fi
      done
      [[ "$ready" == "1" ]] && break
      sleep 1
    done
    if [[ "$ready" != "1" ]]; then
      tail -120 "$SERVICES_LOG" >&2 || true
      echo "六服务启动失败" >&2
      exit 1
    fi
  fi
}

check_services() {
  local service port code status services_json='[]'
  for service_port in \
    'identity 8086' 'masterdata 8081' 'billing 8084' \
    'governance 8085' 'access 8082' 'gateway 8888'; do
    service="${service_port% *}"
    port="${service_port##* }"
    code="$(curl --noproxy '*' -sS --connect-timeout 3 --max-time 5 \
      -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/actuator/health" 2>/dev/null || true)"
    status=failed
    [[ "$code" == "200" ]] && status=passed
    services_json="$(jq -c --arg service "$service" --argjson port "$port" \
      --arg httpCode "$code" --arg status "$status" \
      '. + [{service:$service,port:$port,httpCode:$httpCode,status:$status}]' \
      <<<"$services_json")"
    [[ "$status" == passed ]] || {
      printf '%s\n' "$services_json" > "$SERVICE_HEALTH"
      cat "$SERVICE_HEALTH" >&2
      echo "服务健康检查失败: $service:$port" >&2
      exit 1
    }
  done
  printf '%s\n' "$services_json" > "$SERVICE_HEALTH"
}

start_frontend() {
  : > "$FRONTEND_LOG"
  (
    cd "$WEB_ROOT"
    export PATH="$NODE_BIN_DIR:$PATH"
    export VITE_PROXY_TARGET="$GATEWAY_URL"
    export NPM_CONFIG_USERCONFIG="$NPM_USER_CONFIG"
    export npm_config_engine_strict=true
    "$NODE_BIN_DIR/npm" ci --no-audit --no-fund
  ) >"$FRONTEND_LOG" 2>&1
  if [[ "$KEEP_RUNNING" == true ]]; then
    WEB_PID="$(
      cd "$WEB_ROOT"
      export PATH="$NODE_BIN_DIR:$PATH"
      export VITE_PROXY_TARGET="$GATEWAY_URL"
      python3 - "$FRONTEND_LOG" "$NODE_BIN_DIR/node" \
        "$WEB_ROOT/node_modules/vite/bin/vite.js" "$WEB_PORT" <<'PY'
import os
import subprocess
import sys

with open(sys.argv[1], "ab", buffering=0) as log:
    process = subprocess.Popen(
        [sys.argv[2], sys.argv[3], "--host", "127.0.0.1", "--port", sys.argv[4]],
        env=os.environ.copy(),
        stdout=log,
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
print(process.pid)
PY
    )"
  else
    (
      cd "$WEB_ROOT"
      export PATH="$NODE_BIN_DIR:$PATH"
      export VITE_PROXY_TARGET="$GATEWAY_URL"
      exec "$NODE_BIN_DIR/npm" run dev -- --host 127.0.0.1 --port "$WEB_PORT"
    ) >>"$FRONTEND_LOG" 2>&1 &
    WEB_PID=$!
  fi
  for attempt in $(seq 1 120); do
    WEB_HTTP_CODE="$(curl --noproxy '*' -sS --connect-timeout 3 --max-time 5 \
      -o "$FRONTEND_CHECK" -w '%{http_code}' \
      "http://127.0.0.1:$WEB_PORT/" 2>/dev/null || true)"
    if [[ "$WEB_HTTP_CODE" == "200" ]]; then
      return 0
    fi
    if ! kill -0 "$WEB_PID" >/dev/null 2>&1; then
      tail -120 "$FRONTEND_LOG" >&2 || true
      echo "前端启动失败" >&2
      exit 1
    fi
    sleep 1
  done
  tail -120 "$FRONTEND_LOG" >&2 || true
  echo "前端未在限定时间内就绪" >&2
  exit 1
}

start_services
check_services
start_frontend

if ! DEV_MVP_GATEWAY_URL="$GATEWAY_URL" "$RUNNER_SCRIPT" "$STATE_FILE" \
    >"$BUSINESS_LOG" 2>&1; then
  tail -160 "$BUSINESS_LOG" >&2 || true
  echo "Dev MVP 业务验收失败" >&2
  exit 1
fi

BUSINESS_REPORT="$(sed -n 's/^DEV_MVP_REPORT=//p' "$BUSINESS_LOG" | tail -1)"
[[ -f "$BUSINESS_REPORT" ]] || {
  cat "$BUSINESS_LOG" >&2
  echo "业务验收未生成机器可读报告" >&2
  exit 1
}
check_services

TEMP_REPORT="$BUSINESS_REPORT.with-runtime.json"
RUN_FINISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RUN_FINISHED_EPOCH="$(date +%s)"
SOURCE_GIT_SHA="$(git -C "$PROJECT_ROOT" rev-parse HEAD)"
SOURCE_GIT_DIRTY=false
if [[ -n "$(git -C "$PROJECT_ROOT" status --porcelain --untracked-files=normal)" ]]; then
  SOURCE_GIT_DIRTY=true
fi
jq --arg frontendUrl "http://127.0.0.1:$WEB_PORT" \
  --arg httpCode "$WEB_HTTP_CODE" \
  --arg nodeVersion "$NODE_RUNTIME_VERSION" \
  --arg npmVersion "$NPM_RUNTIME_VERSION" \
  --arg gitSha "$SOURCE_GIT_SHA" \
  --argjson gitDirty "$SOURCE_GIT_DIRTY" \
  --arg startedAt "$RUN_STARTED_AT" \
  --arg finishedAt "$RUN_FINISHED_AT" \
  --arg buildMode "$BUILD_MODE" \
  --argjson durationSeconds "$((RUN_FINISHED_EPOCH - RUN_STARTED_EPOCH))" \
  --argjson keepRunning "$KEEP_RUNNING" \
  --slurpfile services "$SERVICE_HEALTH" \
  '. + {
    reportVersion:2,
    source:{gitSha:$gitSha,gitDirty:$gitDirty},
    execution:{startedAt:$startedAt,finishedAt:$finishedAt,durationSeconds:$durationSeconds,buildMode:$buildMode,keepRunning:$keepRunning},
    frontend:{url:$frontendUrl,httpCode:$httpCode,nodeVersion:$nodeVersion,npmVersion:$npmVersion},
    services:$services[0]
  }' \
  "$BUSINESS_REPORT" > "$TEMP_REPORT"
mv -- "$TEMP_REPORT" "$BUSINESS_REPORT"
chmod 600 "$BUSINESS_REPORT"
mkdir -p "$RUNTIME_ROOT"
install -m 600 "$BUSINESS_REPORT" "$LATEST_REPORT"

echo "DEV_MVP_REPORT=$LATEST_REPORT"
echo "Dev MVP dev 闭环通过：V059 无待迁移、六服务健康、前端可访问、3/2/2 业务事实和审批/调用/计费/审计/监控均已验收。"

if [[ "$KEEP_RUNNING" == true ]]; then
  {
    printf 'DEV_MVP_DEMO_MODE=%q\n' true
    printf 'DEV_MVP_WEB_PID=%q\n' "$WEB_PID"
    printf 'DEV_MVP_WEB_PORT=%q\n' "$WEB_PORT"
    printf 'DEV_MVP_GATEWAY_URL=%q\n' "$GATEWAY_URL"
  } >> "$STATE_FILE"
  chmod 600 "$STATE_FILE"
  echo "DEV_MVP_STATE_FILE=$STATE_FILE"
  echo "DEV_MVP_DEMO_URL=http://127.0.0.1:$WEB_PORT"
  echo "DEV_MVP_DEMO_CLEANUP=rtk bash $CLEANUP_SCRIPT $STATE_FILE --stop-runtime"
  echo "Dev MVP demo 模式已保留；登录凭据仅保存在权限为 600 的状态文件中。"
fi
