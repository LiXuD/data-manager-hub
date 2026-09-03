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
NACOS_NAMESPACE="connector-e2e-${RUN_TOKEN//_/-}"

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
if ! E2E_SIGNING_KEY_ID="${E2E_SIGNING_KEY_ID:-platform-default}" \
  "$SCRIPT_DIR/build-signed-artifact.sh" "$OUTPUT_DIR" >/dev/null; then
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
  printf 'E2E_NACOS_NAMESPACE=%q\n' "$NACOS_NAMESPACE"
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

FIXTURE_TENANT_CODE="connector-e2e-tenant-${RUN_TOKEN//_/-}"
FIXTURE_TENANT_ID="$(psql -X -v ON_ERROR_STOP=1 -Atq \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -c "
    INSERT INTO tenant_info (
      tenant_code, tenant_name, tenant_type, status, max_api_keys, max_callers, deleted
    ) VALUES (
      '$FIXTURE_TENANT_CODE', 'Connector E2E Tenant', 'enterprise', 'active', 50, 50, FALSE
    ) RETURNING id")"
FIXTURE_ADMIN_PASSWORD='Admin123!'
FIXTURE_ADMIN_PASSWORD_HASH='$2a$12$JpCO8T1TRskTtV71hRsLueR5KOPHEJhzBThHWQ04GoZJJ8rMYBCpe'
psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" <<SQL >/dev/null
INSERT INTO user_info (
  username, nickname, password, tenant_id, status, deleted
) VALUES (
  'admin', 'Connector E2E Admin', '$FIXTURE_ADMIN_PASSWORD_HASH', $FIXTURE_TENANT_ID, 'active', FALSE
)
ON CONFLICT (username) DO UPDATE SET
  nickname = EXCLUDED.nickname,
  password = EXCLUDED.password,
  tenant_id = EXCLUDED.tenant_id,
  status = EXCLUDED.status,
  deleted = FALSE;

INSERT INTO user_role (user_id, role_id, deleted)
SELECT user_info.id, role_info.id, FALSE
FROM user_info
JOIN role_info ON LOWER(role_info.role_code) = 'admin'
WHERE user_info.username = 'admin'
  AND role_info.status = 'active'
  AND role_info.deleted = FALSE
ON CONFLICT (user_id, role_id) DO UPDATE SET deleted = FALSE;
SQL

FIXTURE_CODE="e2e_${RUN_TOKEN}"
FIXTURE_VENDOR_CODE="e2e-vendor-${RUN_TOKEN//_/-}"
FIXTURE_BACKUP_VENDOR_CODE="e2e-backup-vendor-${RUN_TOKEN//_/-}"
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
    ), backup_vendor_row AS (
      INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, deleted)
      VALUES ('$FIXTURE_BACKUP_VENDOR_CODE', 'Connector E2E Backup Vendor', 'test', 'active', FALSE)
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
        vendor_id, data_type_id, data_type_code, interface_id,
        timeout, retry_count, runtime_mode, connector_version, status, deleted
      )
      SELECT vendor_id, data_type_id, '$FIXTURE_CODE', id,
        5000, 0, 'PLUGIN', 0, 'inactive', FALSE
      FROM interface_row
      RETURNING id, vendor_id, data_type_id, interface_id
    ), backup_config_row AS (
      INSERT INTO vendor_config (
        vendor_id, data_type_id, data_type_code, interface_id,
        timeout, retry_count, runtime_mode, connector_version, status, deleted
      )
      SELECT backup_vendor_row.id, interface_row.data_type_id, '$FIXTURE_CODE', interface_row.id,
        5000, 0, 'PLUGIN', 0, 'inactive', FALSE
      FROM interface_row CROSS JOIN backup_vendor_row
      RETURNING id, vendor_id, data_type_id, interface_id
    )
    SELECT config_row.vendor_id, config_row.data_type_id,
           config_row.interface_id, config_row.id,
           backup_config_row.vendor_id, backup_config_row.id
    FROM config_row CROSS JOIN backup_config_row")"
IFS='|' read -r FIXTURE_VENDOR_ID FIXTURE_DATA_TYPE_ID \
  FIXTURE_INTERFACE_ID FIXTURE_VENDOR_CONFIG_ID FIXTURE_BACKUP_VENDOR_ID \
  FIXTURE_BACKUP_VENDOR_CONFIG_ID <<< "$FIXTURE_DATA"

FIXTURE_BILLING_PLAN_ID="$(psql -X -v ON_ERROR_STOP=1 -Atq \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
  -v vendor_id="$FIXTURE_VENDOR_ID" \
  -v interface_id="$FIXTURE_INTERFACE_ID" \
  -v plan_code="CONNECTOR-E2E-${RUN_TOKEN}" \
  -f "$SCRIPT_DIR/seed-billing-plan.sql")"
FIXTURE_BACKUP_BILLING_PLAN_ID="$(psql -X -v ON_ERROR_STOP=1 -Atq \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
  -v vendor_id="$FIXTURE_BACKUP_VENDOR_ID" \
  -v interface_id="$FIXTURE_INTERFACE_ID" \
  -v plan_code="CONNECTOR-E2E-BACKUP-${RUN_TOKEN}" \
  -f "$SCRIPT_DIR/seed-billing-plan.sql")"

FIXTURE_LEGACY_DRAFT_ID="$(psql -X -v ON_ERROR_STOP=1 -Atq \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -c "
    INSERT INTO vendor_connector_version (
      vendor_config_id, draft_version, pipeline_snapshot, authoring_mode,
      security_version, status, created_at, updated_at
    ) VALUES (
      $FIXTURE_VENDOR_CONFIG_ID,
      1,
      jsonb_build_array(
        jsonb_build_object(
          'stageKey', 'request-builder', 'capability', 'REQUEST_BUILDER',
          'pluginId', 'legacy-http', 'pluginVersion', '1.0.0',
          'order', 0, 'enabled', TRUE,
          'config', jsonb_build_object(
            'apiUrl', 'https://127.0.0.1:$HTTPS_PORT/vendor/echo',
            'method', 'POST',
            'headers', jsonb_build_object('Accept', 'application/json'),
            'contentType', 'application/json; charset=utf-8',
            'requestMapping', jsonb_build_object(),
            'connectTimeoutMs', 5000, 'readTimeoutMs', 5000,
            'totalTimeoutMs', 5000, 'idempotencyPolicy', 'NON_IDEMPOTENT',
            'maxResponseBytes', 10485760
          )
        ),
        jsonb_build_object(
          'stageKey', 'transport', 'capability', 'TRANSPORT',
          'pluginId', 'legacy-http', 'pluginVersion', '1.0.0',
          'order', 100, 'enabled', TRUE, 'config', jsonb_build_object()
        ),
        jsonb_build_object(
          'stageKey', 'response-parser', 'capability', 'RESPONSE_PARSER',
          'pluginId', 'legacy-http', 'pluginVersion', '1.0.0',
          'order', 200, 'enabled', TRUE, 'config', jsonb_build_object()
        ),
        jsonb_build_object(
          'stageKey', 'response-normalizer', 'capability', 'RESPONSE_NORMALIZER',
          'pluginId', 'legacy-http', 'pluginVersion', '1.0.0',
          'order', 300, 'enabled', TRUE,
          'config', jsonb_build_object('responseMapping', jsonb_build_object('success', 'success'))
        )
      ),
      'ADVANCED_LEGACY', 0, 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) RETURNING id")"
RUN_TOKEN_SAFE="$(printf '%s' "$RUN_TOKEN" | tr '_' '-')"
FIXTURE_CALLER_CODE="e2e-caller-$RUN_TOKEN_SAFE"
FIXTURE_PRODUCT_CODE="e2e-product-$RUN_TOKEN_SAFE"
FIXTURE_SCENE_CODE="e2e-scene-$RUN_TOKEN_SAFE"
FIXTURE_API_KEY="e2e-api-key-$RUN_TOKEN_SAFE"
FIXTURE_API_SECRET="e2e-api-secret-$RUN_TOKEN_SAFE"
FIXTURE_CONNECTOR_SECRET_REF="connector.e2e.client-secret"
FIXTURE_CONNECTOR_SECRET_VALUE="e2e-token-secret-$RUN_TOKEN_SAFE"
FIXTURE_CALLER_ID="$(psql -X -v ON_ERROR_STOP=1 -Atq -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -c "INSERT INTO caller_info (caller_code, caller_name, tenant_id, caller_type, status, deleted) VALUES ('$FIXTURE_CALLER_CODE', 'Connector E2E Caller', $FIXTURE_TENANT_ID, 'system', 'active', FALSE) RETURNING id")"
FIXTURE_PRODUCT_ID="$(psql -X -v ON_ERROR_STOP=1 -Atq -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -c "INSERT INTO caller_product (caller_id, product_code, product_name, cache_scope, status, deleted) VALUES ($FIXTURE_CALLER_ID, '$FIXTURE_PRODUCT_CODE', 'Connector E2E Product', 'CALLER', 'active', FALSE) RETURNING id")"
FIXTURE_API_KEY_ID="$(psql -X -v ON_ERROR_STOP=1 -Atq -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -c "INSERT INTO api_key (caller_id, key_name, api_key, api_secret, rate_limit_enabled, rate_limit, quota_limit, quota_used, status, expire_time, deleted) VALUES ($FIXTURE_CALLER_ID, 'Connector E2E Key', '$FIXTURE_API_KEY', '$FIXTURE_API_SECRET', TRUE, 100000, 1000000, 0, 'active', CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE) RETURNING id")"
psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" <<SQL >/dev/null
INSERT INTO user_caller (user_id, caller_id, created_by)
SELECT user_info.id, $FIXTURE_CALLER_ID, user_info.id
FROM user_info
WHERE user_info.username = 'admin'
ON CONFLICT (user_id, caller_id) DO NOTHING;

INSERT INTO api_key_product (api_key_id, product_id)
VALUES ($FIXTURE_API_KEY_ID, $FIXTURE_PRODUCT_ID)
ON CONFLICT (api_key_id, product_id) DO NOTHING;

INSERT INTO call_scene (tenant_id, scene_code, scene_name, status, deleted)
VALUES ($FIXTURE_TENANT_ID, '$FIXTURE_SCENE_CODE', 'Connector E2E Scene', 'active', FALSE)
ON CONFLICT (tenant_id, scene_code) DO UPDATE SET status = 'active', deleted = FALSE;

INSERT INTO api_key_interface (
  api_key_id, interface_id, created_by, grant_source, status, cache_enabled,
  approved_cache_days, effective_at, version
)
SELECT $FIXTURE_API_KEY_ID, $FIXTURE_INTERFACE_ID, user_info.id, 'LEGACY_ADMIN',
       'ACTIVE', TRUE, 1, CURRENT_TIMESTAMP, 0
FROM user_info
WHERE user_info.username = 'admin'
ON CONFLICT (api_key_id, interface_id) DO UPDATE SET
  status = 'ACTIVE',
  cache_enabled = TRUE,
  approved_cache_days = 1,
  revoked_at = NULL,
  revoked_by = NULL,
  revoke_reason = NULL;
SQL
{
  printf 'FIXTURE_VENDOR_ID=%q\n' "$FIXTURE_VENDOR_ID"
  printf 'FIXTURE_BACKUP_VENDOR_ID=%q\n' "$FIXTURE_BACKUP_VENDOR_ID"
  printf 'FIXTURE_DATA_TYPE_ID=%q\n' "$FIXTURE_DATA_TYPE_ID"
  printf 'FIXTURE_INTERFACE_ID=%q\n' "$FIXTURE_INTERFACE_ID"
  printf 'FIXTURE_VENDOR_CONFIG_ID=%q\n' "$FIXTURE_VENDOR_CONFIG_ID"
  printf 'FIXTURE_BACKUP_VENDOR_CONFIG_ID=%q\n' "$FIXTURE_BACKUP_VENDOR_CONFIG_ID"
  printf 'FIXTURE_BILLING_PLAN_ID=%q\n' "$FIXTURE_BILLING_PLAN_ID"
  printf 'FIXTURE_BACKUP_BILLING_PLAN_ID=%q\n' "$FIXTURE_BACKUP_BILLING_PLAN_ID"
  printf 'FIXTURE_LEGACY_DRAFT_ID=%q\n' "$FIXTURE_LEGACY_DRAFT_ID"
  printf 'FIXTURE_TENANT_ID=%q\n' "$FIXTURE_TENANT_ID"
  printf 'FIXTURE_ADMIN_USERNAME=%q\n' 'admin'
  printf 'FIXTURE_ADMIN_PASSWORD=%q\n' "$FIXTURE_ADMIN_PASSWORD"
  printf 'FIXTURE_CALLER_ID=%q\n' "$FIXTURE_CALLER_ID"
  printf 'FIXTURE_PRODUCT_ID=%q\n' "$FIXTURE_PRODUCT_ID"
  printf 'FIXTURE_SCENE_CODE=%q\n' "$FIXTURE_SCENE_CODE"
  printf 'FIXTURE_CONNECTOR_SECRET_REF=%q\n' "$FIXTURE_CONNECTOR_SECRET_REF"
  printf 'FIXTURE_CONNECTOR_SECRET_VALUE=%q\n' "$FIXTURE_CONNECTOR_SECRET_VALUE"
  printf 'FIXTURE_VENDOR_CODE=%q\n' "$FIXTURE_VENDOR_CODE"
  printf 'FIXTURE_BACKUP_VENDOR_CODE=%q\n' "$FIXTURE_BACKUP_VENDOR_CODE"
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
echo "FIXTURE_SIGNING_PUBLIC_KEY_BASE64=$FIXTURE_SIGNING_PUBLIC_KEY_BASE64"
echo "FIXTURE_SIGNING_PUBLIC_KEY_PEM=$FIXTURE_SIGNING_PUBLIC_KEY_PEM"
echo "FIXTURE_ACCESS_SIGNING_KEY_RESOURCE=$FIXTURE_ACCESS_SIGNING_KEY_RESOURCE"
echo "FIXTURE_TLS_TRUSTSTORE=$FIXTURE_TLS_TRUSTSTORE"
echo "FIXTURE_IMPORT_REQUEST=$OUTPUT_DIR/import-request.json"
echo "FIXTURE_VENDOR_ENDPOINT=$FIXTURE_VENDOR_ENDPOINT"
echo "FIXTURE_VENDOR_CONFIG_ID=$FIXTURE_VENDOR_CONFIG_ID"
echo "敏感凭据、签名值和TLS密码仅保存在状态文件中，不在终端输出；请直接安全加载该文件。"
echo "清理命令: $SCRIPT_DIR/cleanup-e2e.sh $STATE_FILE"
