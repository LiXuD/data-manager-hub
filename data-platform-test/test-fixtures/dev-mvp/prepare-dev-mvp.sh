#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
FIXTURE_SCRIPT_DIR="$PROJECT_ROOT/data-platform-test/test-fixtures/connector-e2e"
RUNTIME_ROOT="$PROJECT_ROOT/data-platform-test/test-fixtures/.runtime"
RUN_TOKEN="$(date -u +%Y%m%d%H%M%S)_$$"
RUN_ID="dev_mvp_$RUN_TOKEN"
OUTPUT_DIR="$RUNTIME_ROOT/$RUN_ID"
STATE_FILE="$OUTPUT_DIR/fixture.env"

DB_HOST="${DEV_MVP_DB_HOST:-127.0.0.1}"
DB_PORT="${DEV_MVP_DB_PORT:-15432}"
DB_USERNAME="${DEV_MVP_DB_USERNAME:-postgres}"
DB_PASSWORD="${DEV_MVP_DB_PASSWORD:-123456}"
DB_NAME="dataplatform_${RUN_ID}_regression"
HTTPS_PORT="${DEV_MVP_HTTPS_PORT:-$((19000 + ($$ % 1000)))}"
NACOS_NAMESPACE="${DEV_MVP_NACOS_NAMESPACE:-dev-mvp-${RUN_TOKEN//_/-}}"
ADMIN_PASSWORD='Admin123!'
PASSWORD_HASH='$2a$12$JpCO8T1TRskTtV71hRsLueR5KOPHEJhzBThHWQ04GoZJJ8rMYBCpe'

for command_name in psql curl python3; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少命令: $command_name" >&2
    exit 1
  }
done

[[ "$DB_NAME" =~ ^dataplatform_dev_mvp_[0-9]{14}_[0-9]+_regression$ ]] || {
  echo "拒绝使用非隔离 Dev MVP 数据库名: $DB_NAME" >&2
  exit 1
}
[[ "$DB_PORT" =~ ^[0-9]+$ && "$HTTPS_PORT" =~ ^[0-9]+$ ]] || {
  echo "数据库端口和 HTTPS 端口必须为数字" >&2
  exit 1
}

mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR"
{
  printf 'DEV_MVP_PROJECT_ROOT=%q\n' "$PROJECT_ROOT"
  printf 'DEV_MVP_OUTPUT_DIR=%q\n' "$OUTPUT_DIR"
  printf 'DEV_MVP_DB_HOST=%q\n' "$DB_HOST"
  printf 'DEV_MVP_DB_PORT=%q\n' "$DB_PORT"
  printf 'DEV_MVP_DB_USERNAME=%q\n' "$DB_USERNAME"
  printf 'DEV_MVP_DB_PASSWORD=%q\n' "$DB_PASSWORD"
  printf 'DEV_MVP_DB_NAME=%q\n' "$DB_NAME"
  printf 'DEV_MVP_NACOS_NAMESPACE=%q\n' "$NACOS_NAMESPACE"
  printf 'DEV_MVP_RUN_TOKEN=%q\n' "$RUN_TOKEN"
  printf 'DEV_MVP_ADMIN_USERNAME=%q\n' "dev-mvp-admin-$RUN_TOKEN"
  printf 'DEV_MVP_ADMIN_PASSWORD=%q\n' "$ADMIN_PASSWORD"
  printf 'DEV_MVP_APPLICANT_USERNAME=%q\n' "dev-mvp-applicant-$RUN_TOKEN"
  printf 'DEV_MVP_APPLICANT_PASSWORD=%q\n' "$ADMIN_PASSWORD"
  printf 'DEV_MVP_APPROVER_USERNAME=%q\n' "dev-mvp-approver-$RUN_TOKEN"
  printf 'DEV_MVP_APPROVER_PASSWORD=%q\n' "$ADMIN_PASSWORD"
  printf 'DEV_MVP_SECURITY_USERNAME=%q\n' "dev-mvp-security-$RUN_TOKEN"
  printf 'DEV_MVP_SECURITY_PASSWORD=%q\n' "$ADMIN_PASSWORD"
} > "$STATE_FILE"
chmod 600 "$STATE_FILE"

cleanup_on_error() {
  local status=$?
  if [[ "$status" -ne 0 ]]; then
    "$SCRIPT_DIR/cleanup-dev-mvp.sh" "$STATE_FILE" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap cleanup_on_error EXIT

export PGPASSWORD="$DB_PASSWORD"
database_exists="$(psql -X -Atq -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
  -c "SELECT count(*) FROM pg_database WHERE datname = '$DB_NAME'")" || {
  echo "无法连接 Dev MVP PostgreSQL: $DB_HOST:$DB_PORT" >&2
  exit 1
}
if [[ "$database_exists" != "0" ]]; then
  echo "隔离 Dev MVP 数据库已存在，拒绝覆盖: $DB_NAME" >&2
  exit 1
fi
printf 'CREATE DATABASE :"db_name" OWNER :"db_owner";\n' \
  | psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
      -v db_name="$DB_NAME" -v db_owner="$DB_USERNAME" >/dev/null

if ! (
  cd "$PROJECT_ROOT"
  DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_USERNAME="$DB_USERNAME" \
    DB_PASSWORD="$DB_PASSWORD" DB_NAME="$DB_NAME" \
    ./migrate-db.sh update
) > "$OUTPUT_DIR/migrate.log" 2>&1; then
  tail -80 "$OUTPUT_DIR/migrate.log" >&2 || true
  exit 1
fi

if ! psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" \
    -d "$DB_NAME" -v run_token="$RUN_TOKEN" -v password_hash="$PASSWORD_HASH" \
    -f "$SCRIPT_DIR/seed-dev-mvp.sql" > "$OUTPUT_DIR/seed.log" 2>&1; then
  tail -80 "$OUTPUT_DIR/seed.log" >&2 || true
  exit 1
fi

if ! E2E_SIGNING_KEY_ID="${DEV_MVP_SIGNING_KEY_ID:-platform-default}" \
  "$FIXTURE_SCRIPT_DIR/build-signed-artifact.sh" "$OUTPUT_DIR" > "$OUTPUT_DIR/build-fixture.log" 2>&1; then
  tail -80 "$OUTPUT_DIR/build-fixture.log" >&2 || true
  exit 1
fi

{
  printf 'DEV_MVP_PROJECT_ROOT=%q\n' "$PROJECT_ROOT"
  printf 'DEV_MVP_OUTPUT_DIR=%q\n' "$OUTPUT_DIR"
  printf 'DEV_MVP_DB_HOST=%q\n' "$DB_HOST"
  printf 'DEV_MVP_DB_PORT=%q\n' "$DB_PORT"
  printf 'DEV_MVP_DB_USERNAME=%q\n' "$DB_USERNAME"
  printf 'DEV_MVP_DB_PASSWORD=%q\n' "$DB_PASSWORD"
  printf 'DEV_MVP_DB_NAME=%q\n' "$DB_NAME"
  printf 'DEV_MVP_NACOS_NAMESPACE=%q\n' "$NACOS_NAMESPACE"
  printf 'DEV_MVP_RUN_TOKEN=%q\n' "$RUN_TOKEN"
  printf 'DEV_MVP_ADMIN_USERNAME=%q\n' "dev-mvp-admin-$RUN_TOKEN"
  printf 'DEV_MVP_ADMIN_PASSWORD=%q\n' "$ADMIN_PASSWORD"
  printf 'DEV_MVP_APPLICANT_USERNAME=%q\n' "dev-mvp-applicant-$RUN_TOKEN"
  printf 'DEV_MVP_APPLICANT_PASSWORD=%q\n' "$ADMIN_PASSWORD"
  printf 'DEV_MVP_APPROVER_USERNAME=%q\n' "dev-mvp-approver-$RUN_TOKEN"
  printf 'DEV_MVP_APPROVER_PASSWORD=%q\n' "$ADMIN_PASSWORD"
  printf 'DEV_MVP_SECURITY_USERNAME=%q\n' "dev-mvp-security-$RUN_TOKEN"
  printf 'DEV_MVP_SECURITY_PASSWORD=%q\n' "$ADMIN_PASSWORD"
  printf 'DEV_MVP_DB_READY=%q\n' 'true'
  printf 'DEV_MVP_SCHEMA_VERSION=%q\n' 'V059'
} >> "$STATE_FILE"

# shellcheck disable=SC1090
source "$STATE_FILE"
"$FIXTURE_SCRIPT_DIR/start-https-repository.sh" "$STATE_FILE" "$HTTPS_PORT" \
  > "$OUTPUT_DIR/start-fixture.log" 2>&1

{
  printf 'DEV_MVP_FIXTURE_ENDPOINT_BASE=%q\n' "https://127.0.0.1:$HTTPS_PORT"
  printf 'DEV_MVP_RUNNER=%q\n' "$SCRIPT_DIR/run-dev-mvp.sh"
} >> "$STATE_FILE"
chmod 600 "$STATE_FILE"
trap - EXIT

echo "DEV_MVP_STATE_FILE=$STATE_FILE"
echo "DEV_MVP_DB_NAME=$DB_NAME"
echo "DEV_MVP_FIXTURE_ENDPOINT_BASE=https://127.0.0.1:$HTTPS_PORT"
echo "Dev MVP 隔离数据库、迁移和 HTTPS 连接器夹具已准备；敏感值仅保存在状态文件中。"
