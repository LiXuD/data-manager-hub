#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RUN_TOKEN="$(date -u +%Y%m%d%H%M%S)_$$"
RUN_ID="connector_e2e_$RUN_TOKEN"
OUTPUT_DIR="$PROJECT_ROOT/data-platform-test/test-fixtures/.runtime/$RUN_ID"
STATE_FILE="$OUTPUT_DIR/fixture.env"
DB_HOST="${E2E_DB_HOST:-localhost}"
DB_PORT="${E2E_DB_PORT:-5432}"
DB_USERNAME="${E2E_DB_USERNAME:-postgres}"
DB_PASSWORD="${E2E_DB_PASSWORD:-postgres}"
DB_NAME="dataplatform_${RUN_ID}_regression"
HTTPS_PORT="${E2E_HTTPS_PORT:-$((18000 + ($$ % 1000)))}"

for command_name in psql curl python3; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少命令: $command_name" >&2
    exit 1
  }
done
[[ "$DB_NAME" =~ ^dataplatform_connector_e2e_[0-9]{14}_[0-9]+_regression$ ]] || {
  echo "拒绝使用非隔离E2E数据库名: $DB_NAME" >&2
  exit 1
}
[[ "$DB_PORT" =~ ^[0-9]+$ && "$HTTPS_PORT" =~ ^[0-9]+$ ]] || {
  echo "数据库端口和HTTPS端口必须为数字" >&2
  exit 1
}

mkdir -p "$OUTPUT_DIR"
if ! "$SCRIPT_DIR/build-signed-artifact.sh" "$OUTPUT_DIR" >/dev/null; then
  case "$OUTPUT_DIR/" in
    "$PROJECT_ROOT/data-platform-test/test-fixtures/.runtime/connector_e2e_"[0-9]*_[0-9]*/)
      rm -rf -- "$OUTPUT_DIR"
      ;;
    *)
      echo "fixture构建失败且运行目录未通过安全校验: $OUTPUT_DIR" >&2
      ;;
  esac
  exit 1
fi
{
  printf 'E2E_PROJECT_ROOT=%q\n' "$PROJECT_ROOT"
  printf 'E2E_DB_HOST=%q\n' "$DB_HOST"
  printf 'E2E_DB_PORT=%q\n' "$DB_PORT"
  printf 'E2E_DB_USERNAME=%q\n' "$DB_USERNAME"
  printf 'E2E_DB_PASSWORD=%q\n' "$DB_PASSWORD"
  printf 'E2E_DB_NAME=%q\n' "$DB_NAME"
} >> "$STATE_FILE"

cleanup_on_error() {
  "$SCRIPT_DIR/cleanup-e2e.sh" "$STATE_FILE" >/dev/null 2>&1 || true
}
trap cleanup_on_error ERR

export PGPASSWORD="$DB_PASSWORD"
if [[ "$(psql -X -Atq -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
    -c "SELECT count(*) FROM pg_database WHERE datname = '$DB_NAME'")" != "0" ]]; then
  echo "隔离E2E数据库已存在，拒绝覆盖: $DB_NAME" >&2
  exit 1
fi
psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
  -c "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USERNAME\"" >/dev/null

(cd "$PROJECT_ROOT" && DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_USERNAME="$DB_USERNAME" \
  DB_PASSWORD="$DB_PASSWORD" DB_NAME="$DB_NAME" ./migrate-db.sh update >/dev/null)

FIXTURE_CODE="e2e_${RUN_TOKEN}"
FIXTURE_VENDOR_CODE="e2e-vendor-${RUN_TOKEN//_/-}"
FIXTURE_INTERFACE_CODE="E2E_SIGNED_CONNECTOR_${RUN_TOKEN}"
FIXTURE_DATA="$(psql -X -v ON_ERROR_STOP=1 -Atq -F '|' \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -c "
    WITH data_type_row AS (
      INSERT INTO data_type (data_type_code, data_type_name, data_category, status, deleted)
      VALUES ('$FIXTURE_CODE', 'Connector E2E Fixture', 'test', 'active', FALSE)
      RETURNING id
    ), vendor_row AS (
      INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, deleted)
      VALUES ('$FIXTURE_VENDOR_CODE', 'Connector E2E Vendor', 'test', 'active', FALSE)
      RETURNING id
    ), interface_row AS (
      INSERT INTO api_interface (
        interface_code, interface_name, data_type_id, vendor_id, path,
        request_schema, response_schema, status, deleted
      )
      SELECT '$FIXTURE_INTERFACE_CODE', 'Signed Connector E2E', data_type_row.id, vendor_row.id,
        '/fixture/signed-connector',
        '{\"type\":\"object\"}'::jsonb,
        '{\"type\":\"object\"}'::jsonb, 'active', FALSE
      FROM data_type_row CROSS JOIN vendor_row
      RETURNING id, data_type_id, vendor_id
    ), config_row AS (
      INSERT INTO vendor_config (
        vendor_id, data_type_id, data_type_code, interface_id, api_url, method,
        timeout, retry_count, header_config, request_template, response_mapping,
        status, deleted
      )
      SELECT vendor_id, data_type_id, '$FIXTURE_CODE', id,
        'https://localhost:$HTTPS_PORT/vendor/echo', 'POST', 5000, 0,
        '{\"X-Connector-Fixture\":\"e2e-signed-connector\"}'::jsonb,
        '{}'::jsonb, '{}'::jsonb, 'active', FALSE
      FROM interface_row
      RETURNING id, vendor_id, data_type_id, interface_id
    )
    SELECT config_row.vendor_id, config_row.data_type_id,
           config_row.interface_id, config_row.id
    FROM config_row")"
IFS='|' read -r FIXTURE_VENDOR_ID FIXTURE_DATA_TYPE_ID \
  FIXTURE_INTERFACE_ID FIXTURE_VENDOR_CONFIG_ID <<< "$FIXTURE_DATA"
{
  printf 'FIXTURE_VENDOR_ID=%q\n' "$FIXTURE_VENDOR_ID"
  printf 'FIXTURE_DATA_TYPE_ID=%q\n' "$FIXTURE_DATA_TYPE_ID"
  printf 'FIXTURE_INTERFACE_ID=%q\n' "$FIXTURE_INTERFACE_ID"
  printf 'FIXTURE_VENDOR_CONFIG_ID=%q\n' "$FIXTURE_VENDOR_CONFIG_ID"
  printf 'FIXTURE_VENDOR_CODE=%q\n' "$FIXTURE_VENDOR_CODE"
  printf 'FIXTURE_INTERFACE_CODE=%q\n' "$FIXTURE_INTERFACE_CODE"
} >> "$STATE_FILE"

"$SCRIPT_DIR/start-https-repository.sh" "$STATE_FILE" "$HTTPS_PORT" >/dev/null
# shellcheck disable=SC1090
source "$STATE_FILE"
python3 - "$OUTPUT_DIR/import-request.json" "$FIXTURE_ARTIFACT_URI" \
  "$FIXTURE_ARTIFACT_SHA256" "$FIXTURE_DETACHED_SIGNATURE" "$FIXTURE_SIGNING_KEY_ID" <<'PY'
import json
import pathlib
import sys

pathlib.Path(sys.argv[1]).write_text(json.dumps({
    "artifactUri": sys.argv[2],
    "expectedSha256": sys.argv[3],
    "detachedSignature": sys.argv[4],
    "signingKeyId": sys.argv[5],
}, indent=2) + "\n", encoding="utf-8")
PY

"$SCRIPT_DIR/verify-fixture.sh" "$STATE_FILE"
trap - ERR

echo "E2E_STATE_FILE=$STATE_FILE"
echo "E2E_DB_NAME=$DB_NAME"
echo "FIXTURE_ARTIFACT_URI=$FIXTURE_ARTIFACT_URI"
echo "FIXTURE_ARTIFACT_SHA256=$FIXTURE_ARTIFACT_SHA256"
echo "FIXTURE_DETACHED_SIGNATURE=$FIXTURE_DETACHED_SIGNATURE"
echo "FIXTURE_SIGNING_KEY_ID=$FIXTURE_SIGNING_KEY_ID"
echo "FIXTURE_SIGNING_PUBLIC_KEY_BASE64=$FIXTURE_SIGNING_PUBLIC_KEY_BASE64"
echo "FIXTURE_SIGNING_PUBLIC_KEY_PEM=$FIXTURE_SIGNING_PUBLIC_KEY_PEM"
echo "FIXTURE_ACCESS_SIGNING_KEY_RESOURCE=$FIXTURE_ACCESS_SIGNING_KEY_RESOURCE"
echo "FIXTURE_TLS_TRUSTSTORE=$FIXTURE_TLS_TRUSTSTORE"
echo "FIXTURE_TLS_TRUSTSTORE_PASSWORD=$FIXTURE_TLS_TRUSTSTORE_PASSWORD"
echo "FIXTURE_JAVA_TLS_OPTIONS=$FIXTURE_JAVA_TLS_OPTIONS"
echo "FIXTURE_IMPORT_REQUEST=$OUTPUT_DIR/import-request.json"
echo "FIXTURE_VENDOR_ENDPOINT=$FIXTURE_VENDOR_ENDPOINT"
echo "FIXTURE_VENDOR_CONFIG_ID=$FIXTURE_VENDOR_CONFIG_ID"
echo "清理命令: $SCRIPT_DIR/cleanup-e2e.sh $STATE_FILE"
