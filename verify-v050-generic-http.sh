#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-123456}"
export DB_HOST DB_PORT DB_USERNAME DB_PASSWORD PGPASSWORD="$DB_PASSWORD"

V050_DB_PATTERN='^dataplatform_v050_[a-z0-9_]+_regression$'
V050_RUN_ID="$(date -u +%Y%m%d%H%M%S)_$$"
FRESH_DB="dataplatform_v050_${V050_RUN_ID}_fresh_regression"
V049_TEMPLATE_DB="dataplatform_v050_${V050_RUN_ID}_v049template_regression"
UPGRADE_DB="dataplatform_v050_${V050_RUN_ID}_upgrade_regression"
PARENT_DRIFT_DB="dataplatform_v050_${V050_RUN_ID}_parentdrift_regression"
VERSION_DRIFT_DB="dataplatform_v050_${V050_RUN_ID}_versiondrift_regression"
OTHER_VERSION_DB="dataplatform_v050_${V050_RUN_ID}_otherversion_regression"
SAFE_ROLLBACK_DB="dataplatform_v050_${V050_RUN_ID}_saferollback_regression"
HALT_SPEC_DB="dataplatform_v050_${V050_RUN_ID}_haltspec_regression"
HALT_PIPELINE_DB="dataplatform_v050_${V050_RUN_ID}_haltpipeline_regression"
HALT_TEST_DB="dataplatform_v050_${V050_RUN_ID}_halttest_regression"
HALT_ACTIVATION_DB="dataplatform_v050_${V050_RUN_ID}_haltactivation_regression"
HALT_CALL_DB="dataplatform_v050_${V050_RUN_ID}_haltcall_regression"
HALT_BILLING_DB="dataplatform_v050_${V050_RUN_ID}_haltbilling_regression"
V050_DATABASES=(
  "$FRESH_DB" "$V049_TEMPLATE_DB" "$UPGRADE_DB"
  "$PARENT_DRIFT_DB" "$VERSION_DRIFT_DB" "$OTHER_VERSION_DB"
  "$SAFE_ROLLBACK_DB" "$HALT_SPEC_DB" "$HALT_PIPELINE_DB"
  "$HALT_TEST_DB" "$HALT_ACTIVATION_DB" "$HALT_CALL_DB" "$HALT_BILLING_DB"
)
V050_LOG_DIR="$(mktemp -d)"
V050_PSQL=(psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME")

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

assert_v050_db_name() {
  local database_name="$1"
  [[ "$database_name" =~ $V050_DB_PATTERN ]] || fail "拒绝非 V050 隔离数据库名: $database_name"
  [[ "$database_name" != "dataplatform" ]] || fail "拒绝默认/生产数据库名"
  [[ "$database_name" != *"v049_dev"* ]] || fail "拒绝触碰 V049 开发回归库: $database_name"
}

for v050_database_name in "${V050_DATABASES[@]}"; do
  assert_v050_db_name "$v050_database_name"
done

psql_v050() {
  local database_name="$1"
  shift
  assert_v050_db_name "$database_name"
  "${V050_PSQL[@]}" -d "$database_name" "$@"
}

query_v050_scalar() {
  local database_name="$1"
  local query="$2"
  psql_v050 "$database_name" -Atq -c "$query"
}

create_v050_database() {
  local database_name="$1"
  assert_v050_db_name "$database_name"
  "${V050_PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$database_name\" WITH (FORCE)" >/dev/null
  "${V050_PSQL[@]}" -d postgres -c "CREATE DATABASE \"$database_name\" OWNER \"$DB_USERNAME\"" >/dev/null
}

clone_v050_database() {
  local template_name="$1"
  local database_name="$2"
  assert_v050_db_name "$template_name"
  assert_v050_db_name "$database_name"
  "${V050_PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$database_name\" WITH (FORCE)" >/dev/null
  "${V050_PSQL[@]}" -d postgres -c \
    "CREATE DATABASE \"$database_name\" OWNER \"$DB_USERNAME\" TEMPLATE \"$template_name\"" >/dev/null
}

drop_v050_database() {
  local database_name="$1"
  assert_v050_db_name "$database_name"
  "${V050_PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$database_name\" WITH (FORCE)" >/dev/null
}

cleanup_v050() {
  local database_name
  set +e
  for database_name in "${V050_DATABASES[@]}"; do
    drop_v050_database "$database_name" >/dev/null 2>&1
  done
  rm -rf "$V050_LOG_DIR"
}
trap cleanup_v050 EXIT

run_v050_migration() {
  local database_name="$1"
  local command_name="$2"
  local log_file="$V050_LOG_DIR/${database_name}_${command_name}.log"
  if ! DB_NAME="$database_name" bash ./migrate-db.sh "$command_name" >"$log_file" 2>&1; then
    sed -n '1,220p' "$log_file" >&2
    fail "DB_NAME=$database_name migrate-db.sh $command_name 失败"
  fi
}

run_v050_rollback() {
  local database_name="$1"
  local log_file="$V050_LOG_DIR/${database_name}_rollback.log"
  if ! DB_NAME="$database_name" MIGRATION_CONFIRM_ROLLBACK="$database_name" \
      bash ./migrate-db.sh rollback-count 1 >"$log_file" 2>&1; then
    sed -n '1,220p' "$log_file" >&2
    fail "V050 rollback 失败: $database_name"
  fi
}

expect_v050_update_failure() {
  local database_name="$1"
  local expected_text="$2"
  local log_file="$V050_LOG_DIR/${database_name}_expected_update_failure.log"
  if DB_NAME="$database_name" bash ./migrate-db.sh update >"$log_file" 2>&1; then
    fail "预期 V050 update HALT 但成功: $database_name"
  fi
  grep -Fq "$expected_text" "$log_file" || {
    sed -n '1,220p' "$log_file" >&2
    fail "V050 HALT 原因不匹配: $expected_text"
  }
}

expect_v050_rollback_failure() {
  local database_name="$1"
  local expected_text="$2"
  local log_file="$V050_LOG_DIR/${database_name}_expected_rollback_failure.log"
  if DB_NAME="$database_name" MIGRATION_CONFIRM_ROLLBACK="$database_name" \
      bash ./migrate-db.sh rollback-count 1 >"$log_file" 2>&1; then
    fail "预期 U050 HALT 但 rollback 成功: $database_name"
  fi
  grep -Fq "$expected_text" "$log_file" || {
    sed -n '1,220p' "$log_file" >&2
    fail "U050 HALT 原因不匹配: $expected_text"
  }
}

assert_v050_seed() {
  local database_name="$1"
  psql_v050 "$database_name" <<'SQL'
DO $$
BEGIN
  IF (SELECT count(*) FROM databasechangelog
      WHERE id = 'seed-generic-http-connector-2026-08-20'
        AND author = 'data-platform' AND exectype = 'EXECUTED') <> 1 THEN
    RAISE EXCEPTION 'V050 changeset history missing';
  END IF;
  IF (SELECT count(*) FROM connector_plugin WHERE plugin_id = 'generic-http') <> 1
     OR NOT EXISTS (
       SELECT 1 FROM connector_plugin
       WHERE plugin_id = 'generic-http' AND display_name = 'Generic HTTP'
         AND provider = 'data-platform'
         AND description = 'Built-in standard single-request HTTPS connector'
         AND status = 'ACTIVE' AND deleted = FALSE
         AND created_by IS NULL AND updated_by IS NULL) THEN
    RAISE EXCEPTION 'generic-http parent mismatch';
  END IF;
  IF (SELECT count(*) FROM connector_plugin_version WHERE plugin_id = 'generic-http') <> 1
     OR NOT EXISTS (
       SELECT 1 FROM connector_plugin_version
       WHERE plugin_id = 'generic-http' AND version = '2.0.0' AND spi_version = '1.1'
         AND entry_class = 'com.dataplatform.common.plugin.runtime.GenericHttpConnectorPlugin'
         AND artifact_uri = 'builtin://generic-http/2.0.0'
         AND lower(btrim(artifact_sha256)) = '8f0f535850e77d2680a3159e2de1044c61024cebe13bc55089f4566ef1744b16'
         AND detached_signature = 'builtin' AND signing_key_id = 'builtin'
         AND manifest_json->>'manifestVersion' = '2'
         AND manifest_json->>'pluginId' = 'generic-http'
         AND manifest_json->>'version' = '2.0.0'
         AND manifest_json->>'displayName' = 'Generic HTTP'
         AND manifest_json->>'provider' = 'data-platform'
         AND manifest_json->>'description' = 'Built-in standard single-request HTTPS connector'
         AND manifest_json->>'entryClass' = entry_class
         AND manifest_json->'configSchema' = config_schema_json
         AND manifest_json->'capabilities' = capabilities
         AND manifest_json->'permissions' = permission_manifest
         AND capabilities = '["REQUEST_BUILDER","REQUEST_PROCESSOR","RESPONSE_PARSER"]'::jsonb
         AND permission_manifest = '{"networkHosts":[],"networkProtocols":[]}'::jsonb
         AND min_host_version = '1.0.0' AND status = 'ACTIVE'
         AND safe_error_code IS NULL AND safe_error_digest IS NULL AND verified_at IS NOT NULL
         AND manifest_version = '2' AND authoring_model = 'SIMPLE_CONNECTOR'
         AND connector_kind = 'GENERIC_HTTP' AND transport_mode = 'HOST_SINGLE_HTTP'
         AND output_mode = 'HOST_MAPPING'
         AND compatibility_manifest = '{"dataTypeCodes":["*"],"vendorCodes":["*"]}'::jsonb
         AND manifest_json->'compatibility' = compatibility_manifest
         AND created_by IS NULL AND updated_by IS NULL) THEN
    RAISE EXCEPTION 'generic-http:2.0.0 version mismatch';
  END IF;
  IF to_regprocedure('public.v050_canonical_jsonb(jsonb)') IS NOT NULL
     OR to_regprocedure('public.v050_sha256(jsonb)') IS NOT NULL
     OR to_regprocedure('public.v050_rollback_canonical_jsonb(jsonb)') IS NOT NULL
     OR to_regprocedure('public.v050_rollback_sha256(jsonb)') IS NOT NULL THEN
    RAISE EXCEPTION 'V050 leaked global hash helper functions';
  END IF;
END $$;
SQL
}

assert_v049_without_seed() {
  local database_name="$1"
  psql_v050 "$database_name" <<'SQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM databasechangelog
                 WHERE id = 'add-connector-product-spec-2026-08-12'
                   AND author = 'data-platform')
     OR EXISTS (SELECT 1 FROM databasechangelog
                WHERE id = 'seed-generic-http-connector-2026-08-20'
                  AND author = 'data-platform')
     OR EXISTS (SELECT 1 FROM connector_plugin WHERE plugin_id = 'generic-http')
     OR EXISTS (SELECT 1 FROM connector_plugin_version WHERE plugin_id = 'generic-http') THEN
    RAISE EXCEPTION 'database is not an exact V049 surface without generic seed';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_trigger
                 WHERE tgname = 'trg_connector_plugin_version_immutable'
                   AND tgenabled = 'O' AND NOT tgisinternal)
     OR NOT EXISTS (SELECT 1 FROM pg_trigger
                    WHERE tgname = 'trg_connector_plugin_reject_delete'
                      AND tgenabled = 'O' AND NOT tgisinternal) THEN
    RAISE EXCEPTION 'V047 triggers were not restored';
  END IF;
END $$;
SQL
}

assert_u050_halt_atomic() {
  local database_name="$1"
  [[ "$(query_v050_scalar "$database_name" "SELECT count(*) FROM databasechangelog WHERE id='seed-generic-http-connector-2026-08-20' AND author='data-platform'")" == "1" ]] \
    || fail "U050 HALT 删除了 changeset 历史: $database_name"
  [[ "$(query_v050_scalar "$database_name" "SELECT count(*) FROM connector_plugin WHERE plugin_id='generic-http'")" == "1" ]] \
    || fail "U050 HALT 删除了 parent: $database_name"
  [[ "$(query_v050_scalar "$database_name" "SELECT count(*) FROM connector_plugin_version WHERE plugin_id='generic-http' AND version='2.0.0'")" == "1" ]] \
    || fail "U050 HALT 删除了 version: $database_name"
  [[ "$(query_v050_scalar "$database_name" "SELECT count(*) FROM pg_trigger WHERE tgname IN ('trg_connector_plugin_version_immutable','trg_connector_plugin_reject_delete') AND tgenabled='O'")" == "2" ]] \
    || fail "U050 HALT 改变了 V047 trigger: $database_name"
}

delete_v050_history() {
  local database_name="$1"
  psql_v050 "$database_name" -c \
    "DELETE FROM databasechangelog WHERE id='seed-generic-http-connector-2026-08-20' AND author='data-platform'" >/dev/null
}

run_static_contracts() {
  local database_name="$1"
  local jdbc_url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${database_name}"
  mvn -pl data-platform-common-runtime -am \
    -Dtest=GenericHttpSeedMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test \
    >"$V050_LOG_DIR/static_contract.log" 2>&1 || {
      sed -n '1,220p' "$V050_LOG_DIR/static_contract.log" >&2
      fail "Generic HTTP SQL/static metadata contract failed"
    }
  mvn -pl data-platform-access/data-platform-access-service -am \
    -Dtest=MasterdataConnectorPluginMetadataResolverTest,GenericHttpSeedDatabaseAcceptanceTest \
    -Dv050.jdbcUrl="$jdbc_url" -Dv050.db.username="$DB_USERNAME" \
    -Dsurefire.failIfNoSpecifiedTests=false test \
    >"$V050_LOG_DIR/access_contract.log" 2>&1 || {
      tail -n 220 "$V050_LOG_DIR/access_contract.log" >&2
      fail "Access generic metadata resolver contract failed"
    }
  mvn -pl data-platform-masterdata/data-platform-masterdata-service -am \
    -Dtest=ConnectorSpecCompilerTest,GenericHttpSeedDatabaseAcceptanceTest \
    -Dv050.jdbcUrl="$jdbc_url" -Dv050.db.username="$DB_USERNAME" \
    -Dsurefire.failIfNoSpecifiedTests=false test \
    >"$V050_LOG_DIR/compiler_contract.log" 2>&1 || {
      tail -n 220 "$V050_LOG_DIR/compiler_contract.log" >&2
      fail "Masterdata generic compiler contract failed"
    }
}

create_v050_database "$FRESH_DB"
run_v050_migration "$FRESH_DB" dry-run
drop_v050_database "$FRESH_DB"
create_v050_database "$FRESH_DB"
run_v050_migration "$FRESH_DB" update
run_v050_migration "$FRESH_DB" validate
assert_v050_seed "$FRESH_DB"
run_static_contracts "$FRESH_DB"
pass "Liquibase validate/dry-run、V001-V050 fresh 与 Java static/compiler/resolver 合成契约"

FRESH_STATE_BEFORE="$(query_v050_scalar "$FRESH_DB" "SELECT md5(string_agg(row_to_json(row_data)::text, '|' ORDER BY row_data.id)) FROM (SELECT * FROM connector_plugin_version WHERE plugin_id='generic-http') row_data")"
run_v050_migration "$FRESH_DB" update
FRESH_STATE_AFTER="$(query_v050_scalar "$FRESH_DB" "SELECT md5(string_agg(row_to_json(row_data)::text, '|' ORDER BY row_data.id)) FROM (SELECT * FROM connector_plugin_version WHERE plugin_id='generic-http') row_data")"
[[ "$FRESH_STATE_BEFORE" == "$FRESH_STATE_AFTER" ]] || fail "重复 update 改变 generic seed"
assert_v050_seed "$FRESH_DB"
pass "V050 直接重复 update 为幂等 no-op"

clone_v050_database "$FRESH_DB" "$V049_TEMPLATE_DB"
run_v050_rollback "$V049_TEMPLATE_DB"
assert_v049_without_seed "$V049_TEMPLATE_DB"
psql_v050 "$V049_TEMPLATE_DB" --single-transaction \
  -f sql/rollbacks/U050__seed_generic_http_connector_v2.sql >/dev/null
assert_v049_without_seed "$V049_TEMPLATE_DB"
pass "U050 safe rollback、重复 U050 no-op 与 V047 trigger 恢复"

clone_v050_database "$V049_TEMPLATE_DB" "$UPGRADE_DB"
psql_v050 "$UPGRADE_DB" <<'SQL'
CREATE TABLE v050_upgrade_probe(snapshot TEXT NOT NULL);
INSERT INTO v050_upgrade_probe(snapshot)
SELECT jsonb_build_object(
  'parents', COALESCE((SELECT jsonb_agg(to_jsonb(row_data) ORDER BY row_data.id)
                       FROM connector_plugin row_data), '[]'::jsonb),
  'versions', COALESCE((SELECT jsonb_agg(to_jsonb(row_data) ORDER BY row_data.id)
                        FROM connector_plugin_version row_data), '[]'::jsonb),
  'connectors', COALESCE((SELECT jsonb_agg(to_jsonb(row_data) ORDER BY row_data.id)
                          FROM vendor_connector_version row_data), '[]'::jsonb),
  'tests', COALESCE((SELECT jsonb_agg(to_jsonb(row_data) ORDER BY row_data.id)
                     FROM vendor_connector_test_fact row_data), '[]'::jsonb)
)::TEXT;
SQL
run_v050_migration "$UPGRADE_DB" update
assert_v050_seed "$UPGRADE_DB"
psql_v050 "$UPGRADE_DB" <<'SQL'
DO $$
DECLARE
  before_snapshot TEXT;
  after_snapshot TEXT;
BEGIN
  SELECT snapshot INTO before_snapshot FROM v050_upgrade_probe;
  SELECT jsonb_build_object(
    'parents', COALESCE((SELECT jsonb_agg(to_jsonb(row_data) ORDER BY row_data.id)
                         FROM connector_plugin row_data WHERE plugin_id <> 'generic-http'), '[]'::jsonb),
    'versions', COALESCE((SELECT jsonb_agg(to_jsonb(row_data) ORDER BY row_data.id)
                          FROM connector_plugin_version row_data WHERE plugin_id <> 'generic-http'), '[]'::jsonb),
    'connectors', COALESCE((SELECT jsonb_agg(to_jsonb(row_data) ORDER BY row_data.id)
                            FROM vendor_connector_version row_data), '[]'::jsonb),
    'tests', COALESCE((SELECT jsonb_agg(to_jsonb(row_data) ORDER BY row_data.id)
                       FROM vendor_connector_test_fact row_data), '[]'::jsonb)
  )::TEXT INTO after_snapshot;
  IF before_snapshot IS DISTINCT FROM after_snapshot THEN
    RAISE EXCEPTION 'V049->V050 changed existing catalogue/runtime bytes';
  END IF;
END $$;
SQL
pass "V049->V050 仅新增 exact seed，既有目录/pipeline/hash/test facts 字节哨兵不变"

clone_v050_database "$FRESH_DB" "$PARENT_DRIFT_DB"
delete_v050_history "$PARENT_DRIFT_DB"
psql_v050 "$PARENT_DRIFT_DB" -c \
  "UPDATE connector_plugin SET provider='drift-provider' WHERE plugin_id='generic-http'" >/dev/null
expect_v050_update_failure "$PARENT_DRIFT_DB" "generic-http parent catalogue fact drifted"
[[ "$(query_v050_scalar "$PARENT_DRIFT_DB" "SELECT provider FROM connector_plugin WHERE plugin_id='generic-http'")" == "drift-provider" ]] \
  || fail "parent drift failure was not atomic"
psql_v050 "$PARENT_DRIFT_DB" -c \
  "UPDATE connector_plugin SET provider='data-platform' WHERE plugin_id='generic-http'" >/dev/null
PARENT_FACT_BEFORE="$(query_v050_scalar "$PARENT_DRIFT_DB" "SELECT md5(row_to_json(parent_fact)::text) FROM (SELECT * FROM connector_plugin WHERE plugin_id='generic-http') parent_fact")"
psql_v050 "$PARENT_DRIFT_DB" <<'SQL'
ALTER TABLE connector_plugin_version DISABLE TRIGGER trg_connector_plugin_version_immutable;
DELETE FROM connector_plugin_version WHERE plugin_id = 'generic-http' AND version = '2.0.0';
ALTER TABLE connector_plugin_version ENABLE TRIGGER trg_connector_plugin_version_immutable;
SQL
run_v050_migration "$PARENT_DRIFT_DB" update
assert_v050_seed "$PARENT_DRIFT_DB"
PARENT_FACT_AFTER="$(query_v050_scalar "$PARENT_DRIFT_DB" "SELECT md5(row_to_json(parent_fact)::text) FROM (SELECT * FROM connector_plugin WHERE plugin_id='generic-http') parent_fact")"
[[ "$PARENT_FACT_BEFORE" == "$PARENT_FACT_AFTER" ]] \
  || fail "V050 补全缺失 version 时改写了 exact parent"
pass "preexisting parent drift HALT；exact parent + 缺失 version 只补 version 且 parent 字节不变"

clone_v050_database "$FRESH_DB" "$VERSION_DRIFT_DB"
delete_v050_history "$VERSION_DRIFT_DB"
psql_v050 "$VERSION_DRIFT_DB" <<'SQL'
ALTER TABLE connector_plugin_version DISABLE TRIGGER trg_connector_plugin_version_immutable;
UPDATE connector_plugin_version SET artifact_sha256 = repeat('f', 64)
WHERE plugin_id = 'generic-http' AND version = '2.0.0';
ALTER TABLE connector_plugin_version ENABLE TRIGGER trg_connector_plugin_version_immutable;
SQL
expect_v050_update_failure "$VERSION_DRIFT_DB" "generic-http:2.0.0 catalogue fact drifted"
[[ "$(query_v050_scalar "$VERSION_DRIFT_DB" "SELECT btrim(artifact_sha256) FROM connector_plugin_version WHERE plugin_id='generic-http' AND version='2.0.0'")" == "$(printf 'f%.0s' {1..64})" ]] \
  || fail "version drift failure was not atomic"
psql_v050 "$VERSION_DRIFT_DB" <<'SQL'
ALTER TABLE connector_plugin_version DISABLE TRIGGER trg_connector_plugin_version_immutable;
UPDATE connector_plugin_version
SET artifact_sha256 = '8f0f535850e77d2680a3159e2de1044c61024cebe13bc55089f4566ef1744b16'
WHERE plugin_id = 'generic-http' AND version = '2.0.0';
ALTER TABLE connector_plugin_version ENABLE TRIGGER trg_connector_plugin_version_immutable;
SQL
run_v050_migration "$VERSION_DRIFT_DB" update
assert_v050_seed "$VERSION_DRIFT_DB"
pass "preexisting version drift HALT、零写入、修复后恢复"

clone_v050_database "$FRESH_DB" "$OTHER_VERSION_DB"
delete_v050_history "$OTHER_VERSION_DB"
psql_v050 "$OTHER_VERSION_DB" <<'SQL'
INSERT INTO connector_plugin_version (
  plugin_id, version, spi_version, entry_class, artifact_uri, artifact_sha256,
  detached_signature, signing_key_id, manifest_json, config_schema_json,
  capabilities, permission_manifest, min_host_version, status, verified_at,
  created_at, updated_at, manifest_version, authoring_model, connector_kind,
  transport_mode, output_mode, compatibility_manifest
)
SELECT plugin_id, '2.0.1', spi_version, entry_class,
       'builtin://generic-http/2.0.1', repeat('e', 64), detached_signature,
       signing_key_id, jsonb_set(manifest_json, '{version}', '"2.0.1"'::jsonb),
       config_schema_json, capabilities, permission_manifest, min_host_version,
       status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       manifest_version, authoring_model, connector_kind, transport_mode,
       output_mode, compatibility_manifest
FROM connector_plugin_version
WHERE plugin_id = 'generic-http' AND version = '2.0.0';
SQL
expect_v050_update_failure "$OTHER_VERSION_DB" "unexpected generic-http version already exists"
[[ "$(query_v050_scalar "$OTHER_VERSION_DB" "SELECT count(*) FROM connector_plugin_version WHERE plugin_id='generic-http'")" == "2" ]] \
  || fail "other-version HALT was not atomic"
psql_v050 "$OTHER_VERSION_DB" <<'SQL'
ALTER TABLE connector_plugin_version DISABLE TRIGGER trg_connector_plugin_version_immutable;
DELETE FROM connector_plugin_version WHERE plugin_id = 'generic-http' AND version = '2.0.1';
ALTER TABLE connector_plugin_version ENABLE TRIGGER trg_connector_plugin_version_immutable;
SQL
run_v050_migration "$OTHER_VERSION_DB" update
assert_v050_seed "$OTHER_VERSION_DB"
pass "unexpected generic-http version HALT、零写入、清理后恢复"

clone_v050_database "$FRESH_DB" "$SAFE_ROLLBACK_DB"
run_v050_rollback "$SAFE_ROLLBACK_DB"
assert_v049_without_seed "$SAFE_ROLLBACK_DB"
run_v050_migration "$SAFE_ROLLBACK_DB" update
assert_v050_seed "$SAFE_ROLLBACK_DB"
pass "安全 rollback 后可重新应用 V050"

clone_v050_database "$FRESH_DB" "$HALT_SPEC_DB"
psql_v050 "$HALT_SPEC_DB" <<'SQL'
DO $$
DECLARE
  target_id BIGINT;
  target_config BIGINT;
BEGIN
  SELECT id INTO target_id FROM vendor_connector_version WHERE status = 'DRAFT' ORDER BY id LIMIT 1;
  IF target_id IS NULL THEN
    SELECT config.id INTO target_config
    FROM vendor_config config
    WHERE NOT EXISTS (SELECT 1 FROM vendor_connector_version version
                      WHERE version.vendor_config_id = config.id AND version.status = 'DRAFT')
    ORDER BY config.id LIMIT 1;
    INSERT INTO vendor_connector_version (
      vendor_config_id, draft_version, pipeline_snapshot, security_version, status,
      authoring_mode, connector_spec, spec_hash, compiler_version, compile_hash)
    VALUES (target_config, 1,
      '[{"stageKey":"connector.request-builder","pluginId":"generic-http","pluginVersion":"2.0.0","config":{},"configHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]',
      0, 'DRAFT', 'SIMPLE_CONNECTOR',
      '{"specVersion":"1","plugin":{"pluginId":"generic-http","pluginVersion":"2.0.0"},"config":{}}',
      repeat('b',64), '1.0.0', repeat('c',64));
  ELSE
    UPDATE vendor_connector_version SET
      pipeline_snapshot = '[{"stageKey":"connector.request-builder","pluginId":"generic-http","pluginVersion":"2.0.0","config":{},"configHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]',
      authoring_mode = 'SIMPLE_CONNECTOR',
      connector_spec = '{"specVersion":"1","plugin":{"pluginId":"generic-http","pluginVersion":"2.0.0"},"config":{}}',
      spec_hash = repeat('b',64), compiler_version = '1.0.0', compile_hash = repeat('c',64)
    WHERE id = target_id;
  END IF;
END $$;
SQL
expect_v050_rollback_failure "$HALT_SPEC_DB" "generic-http is referenced"
assert_u050_halt_atomic "$HALT_SPEC_DB"
pass "U050 对任意 DRAFT connector_spec 引用 HALT 且无半回滚"

clone_v050_database "$FRESH_DB" "$HALT_PIPELINE_DB"
psql_v050 "$HALT_PIPELINE_DB" <<'SQL'
INSERT INTO vendor_connector_version (
  vendor_config_id, version_no, draft_version, pipeline_snapshot, snapshot_hash,
  security_version, status, hash_algorithm, integrity_hash, authoring_mode)
SELECT min(id),
       COALESCE((SELECT max(version_no) FROM vendor_connector_version), 0) + 100,
       0,
       '[{"stageKey":"connector.request-builder","pluginId":"generic-http","pluginVersion":"2.0.0","config":{},"configHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]',
       repeat('d',64), 0, 'SUPERSEDED', 'V2_EMBEDDED', repeat('d',64), 'ADVANCED_LEGACY'
FROM vendor_config;
SQL
expect_v050_rollback_failure "$HALT_PIPELINE_DB" "generic-http is referenced"
assert_u050_halt_atomic "$HALT_PIPELINE_DB"
pass "U050 对 published/superseded pipeline 引用 HALT 且无半回滚"

clone_v050_database "$FRESH_DB" "$HALT_TEST_DB"
psql_v050 "$HALT_TEST_DB" <<'SQL'
INSERT INTO vendor_connector_test_fact (
  vendor_config_id, draft_version, snapshot_hash, plugin_bindings,
  test_succeeded, result_digest, authoring_mode)
SELECT min(id), 999, repeat('a',64), '["generic-http:2.0.0"]',
       TRUE, repeat('b',64), 'ADVANCED_LEGACY'
FROM vendor_config;
SQL
expect_v050_rollback_failure "$HALT_TEST_DB" "generic-http is referenced"
assert_u050_halt_atomic "$HALT_TEST_DB"
pass "U050 对 immutable test fact plugin binding 引用 HALT 且无半回滚"

clone_v050_database "$FRESH_DB" "$HALT_ACTIVATION_DB"
psql_v050 "$HALT_ACTIVATION_DB" <<'SQL'
INSERT INTO connector_plugin_activation (
  service_instance_id, plugin_id, plugin_version, artifact_sha256,
  host_version, state, last_heartbeat_at, created_at, updated_at)
VALUES ('v050-regression', 'generic-http', '2.0.0',
  '8f0f535850e77d2680a3159e2de1044c61024cebe13bc55089f4566ef1744b16',
  '1.0.0', 'READY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
SQL
expect_v050_rollback_failure "$HALT_ACTIVATION_DB" "generic-http is referenced"
assert_u050_halt_atomic "$HALT_ACTIVATION_DB"
pass "U050 对任意 activation 状态引用 HALT 且无半回滚"

clone_v050_database "$FRESH_DB" "$HALT_CALL_DB"
psql_v050 "$HALT_CALL_DB" <<'SQL'
DO $$
BEGIN
  UPDATE call_record SET plugin_id = 'generic-http', plugin_version = '2.0.0'
  WHERE id = (SELECT min(id) FROM call_record);
  IF NOT FOUND THEN
    INSERT INTO call_record(
      tenant_id, caller_id, api_key_id, vendor_id, vendor_code, data_type,
      request_id, plugin_id, plugin_version)
    VALUES (
      1, 1, 1, 1, 'V050_REGRESSION', 'V050_REGRESSION',
      'v050-call-regression', 'generic-http', '2.0.0');
  END IF;
END $$;
SQL
expect_v050_rollback_failure "$HALT_CALL_DB" "generic-http is referenced"
assert_u050_halt_atomic "$HALT_CALL_DB"
pass "U050 对 call_record 引用 HALT 且无半回滚"

clone_v050_database "$FRESH_DB" "$HALT_BILLING_DB"
psql_v050 "$HALT_BILLING_DB" <<'SQL'
DO $$
BEGIN
  UPDATE billing_event SET plugin_id = 'generic-http', plugin_version = '2.0.0'
  WHERE id = (SELECT min(id) FROM billing_event);
  IF NOT FOUND THEN
    INSERT INTO billing_event (
      request_id, event_type, plan_id, plan_code, plan_version, template_code,
      accounting_purpose, vendor_id, vendor_code, interface_id, interface_code,
      billable, quantity, unit, usage_before, base_amount, adjustment_amount,
      final_amount, currency, status, pricing_snapshot, billing_period, call_time,
      plugin_id, plugin_version)
    SELECT 'v050-billing-regression', 'USAGE', plan.id, plan.plan_code, plan.version,
           plan.template_code, plan.accounting_purpose, plan.vendor_id,
           plan.vendor_code, plan.interface_id, plan.interface_code,
           FALSE, 0, 'CALL', 0, 0, 0, 0, plan.currency, 'POSTED', '{}',
           CURRENT_DATE, CURRENT_TIMESTAMP, 'generic-http', '2.0.0'
    FROM billing_plan plan
    ORDER BY plan.id
    LIMIT 1;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'fresh baseline must provide one billing_plan regression fixture';
    END IF;
  END IF;
END $$;
SQL
expect_v050_rollback_failure "$HALT_BILLING_DB" "generic-http is referenced"
assert_u050_halt_atomic "$HALT_BILLING_DB"
pass "U050 对 billing_event 引用 HALT 且无半回滚"

echo "V050/U050 Generic HTTP 隔离验证完成；13 个时间戳回归数据库将在退出时清理。"
