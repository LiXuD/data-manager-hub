#!/usr/bin/env bash

set -euo pipefail

STATE_FILE="${1:-}"
[[ -f "$STATE_FILE" ]] || { echo "用法: $0 <fixture.env>" >&2; exit 2; }
# shellcheck disable=SC1090
source "$STATE_FILE"

if [[ -n "${FIXTURE_HTTPS_PID:-}" ]] && kill -0 "$FIXTURE_HTTPS_PID" >/dev/null 2>&1; then
  PROCESS_COMMAND="$(ps -p "$FIXTURE_HTTPS_PID" -o command= 2>/dev/null || true)"
  if [[ "$PROCESS_COMMAND" == *"fixture_https_server.py"* \
      && "$PROCESS_COMMAND" == *"$FIXTURE_OUTPUT_DIR"* ]]; then
    kill "$FIXTURE_HTTPS_PID"
    for attempt in {1..20}; do
      kill -0 "$FIXTURE_HTTPS_PID" >/dev/null 2>&1 || break
      sleep 0.25
    done
  else
    echo "PID不属于当前fixture，拒绝终止: $FIXTURE_HTTPS_PID" >&2
    exit 1
  fi
fi

if [[ -n "${E2E_DB_NAME:-}" ]]; then
  [[ "$E2E_DB_NAME" =~ ^dataplatform_connector_e2e_[0-9]{14}_[0-9]+_regression$ ]] || {
    echo "拒绝删除非隔离E2E数据库: $E2E_DB_NAME" >&2
    exit 1
  }
  export PGPASSWORD="${E2E_DB_PASSWORD:-postgres}"
  psql -X -v ON_ERROR_STOP=1 -h "${E2E_DB_HOST:-localhost}" \
    -p "${E2E_DB_PORT:-5432}" -U "${E2E_DB_USERNAME:-postgres}" -d postgres \
    -c "DROP DATABASE IF EXISTS \"$E2E_DB_NAME\" WITH (FORCE)" >/dev/null
fi

PROJECT_RUNTIME_ROOT="${E2E_PROJECT_ROOT:-}/data-platform-test/test-fixtures/.runtime/"
case "$FIXTURE_OUTPUT_DIR/" in
  "$PROJECT_RUNTIME_ROOT"connector_e2e_[0-9]*_[0-9]*/)
    rm -rf -- "$FIXTURE_OUTPUT_DIR"
    ;;
  *)
    echo "拒绝删除非fixture运行目录: $FIXTURE_OUTPUT_DIR" >&2
    exit 1
    ;;
esac

echo "隔离连接器E2E夹具已清理"
