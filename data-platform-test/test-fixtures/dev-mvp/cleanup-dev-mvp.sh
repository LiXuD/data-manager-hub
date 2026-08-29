#!/usr/bin/env bash

set -euo pipefail

STATE_FILE="${1:-}"
[[ -f "$STATE_FILE" ]] || { echo "用法: $0 <fixture.env>" >&2; exit 2; }
KEEP_OUTPUT=false
if [[ "${2:-}" == "--keep-output" ]]; then
  KEEP_OUTPUT=true
elif [[ "$#" -gt 1 ]]; then
  echo "用法: $0 <fixture.env> [--keep-output]" >&2
  exit 2
fi
# shellcheck disable=SC1090
source "$STATE_FILE"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
[[ "${DEV_MVP_PROJECT_ROOT:-}" == "$PROJECT_ROOT" ]] || {
  echo "fixture 状态文件不属于当前项目" >&2
  exit 1
}
[[ "${DEV_MVP_DB_NAME:-}" =~ ^dataplatform_dev_mvp_[0-9]{14}_[0-9]+_regression$ ]] || {
  echo "拒绝删除非隔离 Dev MVP 数据库: ${DEV_MVP_DB_NAME:-}" >&2
  exit 1
}
[[ "${DEV_MVP_OUTPUT_DIR:-}" =~ ^${PROJECT_ROOT}/data-platform-test/test-fixtures/\.runtime/dev_mvp_[0-9]{14}_[0-9]+$ ]] || {
  echo "拒绝删除非 Dev MVP 运行目录: ${DEV_MVP_OUTPUT_DIR:-}" >&2
  exit 1
}

if [[ -n "${FIXTURE_HTTPS_PID:-}" ]] \
    && [[ "$FIXTURE_HTTPS_PID" =~ ^[0-9]+$ ]] \
    && kill -0 "$FIXTURE_HTTPS_PID" >/dev/null 2>&1; then
  process_command="$(ps -p "$FIXTURE_HTTPS_PID" -o command= 2>/dev/null || true)"
  if [[ "$process_command" == *"fixture_https_server.py"* \
      && "$process_command" == *"$DEV_MVP_OUTPUT_DIR"* ]]; then
    kill "$FIXTURE_HTTPS_PID" >/dev/null 2>&1 || true
    for attempt in {1..20}; do
      kill -0 "$FIXTURE_HTTPS_PID" >/dev/null 2>&1 || break
      sleep 0.25
    done
  else
    echo "PID 不属于当前 Dev MVP fixture，拒绝终止: $FIXTURE_HTTPS_PID" >&2
    exit 1
  fi
fi

export PGPASSWORD="${DEV_MVP_DB_PASSWORD:-123456}"
printf 'DROP DATABASE IF EXISTS :"db_name" WITH (FORCE);\n' \
  | psql -X -v ON_ERROR_STOP=1 \
      -h "${DEV_MVP_DB_HOST:-127.0.0.1}" -p "${DEV_MVP_DB_PORT:-15432}" \
      -U "${DEV_MVP_DB_USERNAME:-postgres}" -d postgres \
      -v db_name="$DEV_MVP_DB_NAME" >/dev/null

if [[ "$KEEP_OUTPUT" == true ]]; then
  echo "Dev MVP 隔离数据库已清理；运行目录保留用于失败诊断: $DEV_MVP_OUTPUT_DIR"
else
  rm -rf -- "$DEV_MVP_OUTPUT_DIR"
  echo "Dev MVP 隔离数据库和运行目录已清理"
fi
