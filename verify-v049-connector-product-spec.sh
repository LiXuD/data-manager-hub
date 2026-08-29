#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-123456}"
export DB_HOST DB_PORT DB_USERNAME DB_PASSWORD PGPASSWORD="$DB_PASSWORD"

DB_NAME_PATTERN='^dataplatform_v049_[a-z0-9_]+_regression$'
RUN_ID="$(date -u +%Y%m%d%H%M%S)"
FRESH_DB="dataplatform_v049_${RUN_ID}_fresh_regression"
V048_TEMPLATE_DB="dataplatform_v049_${RUN_ID}_v048template_regression"
UPGRADE_DB="dataplatform_v049_${RUN_ID}_upgrade_regression"
MISSING_DEPENDENCY_DB="dataplatform_v049_${RUN_ID}_missing_dependency_regression"
PREEXISTING_V2_DB="dataplatform_v049_${RUN_ID}_preexisting_v2_regression"
SAFE_ROLLBACK_DB="dataplatform_v049_${RUN_ID}_safe_rollback_regression"
HALT_PLUGIN_DB="dataplatform_v049_${RUN_ID}_halt_plugin_regression"
HALT_CONNECTOR_DB="dataplatform_v049_${RUN_ID}_halt_connector_regression"
HALT_TEST_DB="dataplatform_v049_${RUN_ID}_halt_test_regression"
REGRESSION_DATABASES=(
  "$FRESH_DB"
  "$V048_TEMPLATE_DB"
  "$UPGRADE_DB"
  "$MISSING_DEPENDENCY_DB"
  "$PREEXISTING_V2_DB"
  "$SAFE_ROLLBACK_DB"
  "$HALT_PLUGIN_DB"
  "$HALT_CONNECTOR_DB"
  "$HALT_TEST_DB"
)
LOG_DIR="$(mktemp -d)"
PSQL=(psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME")

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

assert_regression_db_name() {
  local db_name="$1"
  [[ "$db_name" =~ $DB_NAME_PATTERN ]] || fail "拒绝非隔离数据库名: $db_name"
  [[ "$db_name" != "dataplatform" ]] || fail "拒绝生产数据库名: $db_name"
}

for database_name in "${REGRESSION_DATABASES[@]}"; do
  assert_regression_db_name "$database_name"
done

psql_db() {
  local db_name="$1"
  shift
  assert_regression_db_name "$db_name"
  "${PSQL[@]}" -d "$db_name" "$@"
}

create_database() {
  local db_name="$1"
  assert_regression_db_name "$db_name"
  "${PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$db_name\" WITH (FORCE)" >/dev/null
  "${PSQL[@]}" -d postgres -c "CREATE DATABASE \"$db_name\" OWNER \"$DB_USERNAME\"" >/dev/null
}

clone_database() {
  local template_db="$1"
  local db_name="$2"
  assert_regression_db_name "$template_db"
  assert_regression_db_name "$db_name"
  "${PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$db_name\" WITH (FORCE)" >/dev/null
  "${PSQL[@]}" -d postgres -c \
    "CREATE DATABASE \"$db_name\" OWNER \"$DB_USERNAME\" TEMPLATE \"$template_db\"" >/dev/null
  # V049 assertions stop before V050. V051 and V052 are independent
  # forward-only repairs, so remove both history rows in this isolated clone
  # before using rollback-count to target U050/U049.
  delete_forward_only_history "$db_name"
}

delete_forward_only_history() {
  local db_name="$1"
  psql_db "$db_name" -c "DELETE FROM databasechangelog WHERE author = 'data-platform' AND id IN ('widen-call-record-error-code-2026-08-27', 'bind-call-record-interface-identity-2026-08-28')" >/dev/null
}

drop_database() {
  local db_name="$1"
  assert_regression_db_name "$db_name"
  "${PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$db_name\" WITH (FORCE)" >/dev/null
}

cleanup() {
  local database_name
  set +e
  for database_name in "${REGRESSION_DATABASES[@]}"; do
    drop_database "$database_name" >/dev/null 2>&1
  done
  rm -rf "$LOG_DIR"
}
trap cleanup EXIT

run_migration() {
  local db_name="$1"
  shift
  assert_regression_db_name "$db_name"
  local log_file="$LOG_DIR/${db_name}_$1.log"
  if ! DB_NAME="$db_name" bash ./migrate-db.sh "$@" >"$log_file" 2>&1; then
    echo "迁移命令失败: DB_NAME=$db_name ./migrate-db.sh $*" >&2
    sed -n '1,180p' "$log_file" >&2
    return 1
  fi
}

run_rollback() {
  local db_name="$1"
  assert_regression_db_name "$db_name"
  local log_file="$LOG_DIR/${db_name}_rollback.log"
  if ! DB_NAME="$db_name" MIGRATION_CONFIRM_ROLLBACK="$db_name" \
      bash ./migrate-db.sh rollback-count 1 >"$log_file" 2>&1; then
    echo "回滚命令失败: $db_name" >&2
    sed -n '1,180p' "$log_file" >&2
    return 1
  fi
}

# The repository changelog now continues through V052. A V049 regression must
# finish on the exact V049 surface, so apply the current changelog and then
# transactionally remove only the unreferenced built-in V050 seed. This keeps
# the V049 assertions strict (including rejecting every pre-existing v2 row)
# instead of teaching them to ignore later product facts.
run_v049_update() {
  local db_name="$1"
  assert_regression_db_name "$db_name"
  run_migration "$db_name" update
  delete_forward_only_history "$db_name"
  if [[ "$(psql_db "$db_name" -Atq -c \
      "SELECT count(*) FROM databasechangelog WHERE id = 'seed-generic-http-connector-2026-08-20' AND author = 'data-platform'")" != "0" ]]; then
    run_rollback "$db_name"
  fi
  [[ "$(psql_db "$db_name" -Atq -c \
      "SELECT count(*) FROM databasechangelog WHERE id = 'seed-generic-http-connector-2026-08-20' AND author = 'data-platform'")" == "0" ]] \
    || fail "V049-only 验证仍残留 V050 changeset: $db_name"
  [[ "$(psql_db "$db_name" -Atq -c \
      "SELECT count(*) FROM connector_plugin WHERE plugin_id = 'generic-http'")" == "0" ]] \
    || fail "V049-only 验证仍残留 generic-http parent: $db_name"
  [[ "$(psql_db "$db_name" -Atq -c \
      "SELECT count(*) FROM connector_plugin_version WHERE plugin_id = 'generic-http'")" == "0" ]] \
    || fail "V049-only 验证仍残留 generic-http version: $db_name"
}

expect_update_failure() {
  local db_name="$1"
  local expected_text="$2"
  local log_file="$LOG_DIR/${db_name}_expected_update_failure.log"
  if DB_NAME="$db_name" bash ./migrate-db.sh update >"$log_file" 2>&1; then
    fail "预期 V049 HALT 但 update 成功: $db_name"
  fi
  grep -Fq "$expected_text" "$log_file" || {
    sed -n '1,180p' "$log_file" >&2
    fail "V049 HALT 原因不匹配，未找到: $expected_text"
  }
}

expect_rollback_failure() {
  local db_name="$1"
  local expected_text="$2"
  local log_file="$LOG_DIR/${db_name}_expected_rollback_failure.log"
  if DB_NAME="$db_name" MIGRATION_CONFIRM_ROLLBACK="$db_name" \
      bash ./migrate-db.sh rollback-count 1 >"$log_file" 2>&1; then
    fail "预期 U049 HALT 但 rollback 成功: $db_name"
  fi
  grep -Fq "$expected_text" "$log_file" || {
    sed -n '1,180p' "$log_file" >&2
    fail "U049 HALT 原因不匹配，未找到: $expected_text"
  }
}

assert_v049_schema() {
  local db_name="$1"
  psql_db "$db_name" <<'SQL'
DO $$
DECLARE
  plugin_function TEXT;
  connector_function TEXT;
BEGIN
  IF (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'connector_plugin_version'
        AND column_name IN ('manifest_version', 'authoring_model', 'connector_kind',
                            'transport_mode', 'output_mode', 'compatibility_manifest')) <> 6 THEN
    RAISE EXCEPTION 'V049 插件投影列不完整';
  END IF;
  IF (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'vendor_connector_version'
        AND column_name IN ('authoring_mode', 'connector_spec', 'spec_hash',
                            'compiler_version', 'compile_hash')) <> 5 THEN
    RAISE EXCEPTION 'V049 连接器产品列不完整';
  END IF;
  IF (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'vendor_connector_test_fact'
        AND column_name IN ('authoring_mode', 'spec_hash', 'compile_hash')) <> 3 THEN
    RAISE EXCEPTION 'V049 测试事实列不完整';
  END IF;
  IF (SELECT count(*) FROM pg_constraint
      WHERE conname IN ('ck_connector_plugin_manifest_projection_v049',
                        'ck_connector_plugin_manifest_binding_v049',
                        'ck_vendor_connector_authoring_mode_v049',
                        'ck_vendor_connector_product_spec_v049',
                        'ck_vendor_connector_test_authoring_v049')) <> 5 THEN
    RAISE EXCEPTION 'V049 约束不完整';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_trigger
                 WHERE tgname = 'trg_vendor_connector_test_fact_immutable'
                   AND tgenabled = 'O' AND NOT tgisinternal) THEN
    RAISE EXCEPTION '测试事实不可变 trigger 未恢复启用';
  END IF;
  IF (SELECT count(*) FROM pg_class
      WHERE relnamespace = 'public'::regnamespace
        AND relname IN ('idx_connector_plugin_product_catalog_v049',
                        'idx_vendor_connector_authoring_mode_v049',
                        'idx_vendor_connector_test_product_gate_v049')) <> 3 THEN
    RAISE EXCEPTION 'V049 索引不完整';
  END IF;
  SELECT pg_get_functiondef('reject_connector_plugin_version_identity_mutation()'::regprocedure)
  INTO plugin_function;
  SELECT pg_get_functiondef('enforce_vendor_connector_version_immutability()'::regprocedure)
  INTO connector_function;
  IF position('NEW.manifest_version' IN plugin_function) = 0
     OR position('NEW.compatibility_manifest' IN plugin_function) = 0 THEN
    RAISE EXCEPTION '插件投影未纳入不可变函数';
  END IF;
  IF position('NEW.authoring_mode' IN connector_function) = 0
     OR position('NEW.connector_spec' IN connector_function) = 0
     OR position('NEW.spec_hash' IN connector_function) = 0
     OR position('NEW.compiler_version' IN connector_function) = 0
     OR position('NEW.compile_hash' IN connector_function) = 0 THEN
    RAISE EXCEPTION '发布连接器产品事实未纳入不可变函数';
  END IF;
  IF to_regprocedure('v049_connector_canonical_jsonb(jsonb)') IS NOT NULL
     OR to_regprocedure('v049_connector_sha256(jsonb)') IS NOT NULL
     OR to_regprocedure('v049_connector_v1_integrity(text,jsonb)') IS NOT NULL THEN
    RAISE EXCEPTION 'V049 临时校验函数未清理';
  END IF;
  IF (SELECT count(*) FROM databasechangelog
      WHERE id = 'add-connector-product-spec-2026-08-12'
        AND author = 'data-platform') <> 1 THEN
    RAISE EXCEPTION 'V049 changeset 历史不唯一';
  END IF;
  IF EXISTS (SELECT 1 FROM databasechangelog
             WHERE id = 'seed-generic-http-connector-2026-08-20'
               AND author = 'data-platform')
     OR EXISTS (SELECT 1 FROM connector_plugin WHERE plugin_id = 'generic-http')
     OR EXISTS (SELECT 1 FROM connector_plugin_version WHERE plugin_id = 'generic-http') THEN
    RAISE EXCEPTION 'V049-only 验证表面混入 V050 generic-http seed';
  END IF;
END $$;
SQL
}

assert_v048_surface() {
  local db_name="$1"
  psql_db "$db_name" <<'SQL'
DO $$
DECLARE
  plugin_function TEXT;
  connector_function TEXT;
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'public'
               AND ((table_name = 'connector_plugin_version' AND column_name = 'manifest_version')
                 OR (table_name = 'vendor_connector_version' AND column_name = 'authoring_mode')
                 OR (table_name = 'vendor_connector_test_fact' AND column_name = 'compile_hash'))) THEN
    RAISE EXCEPTION 'U049 后仍残留新增列';
  END IF;
  IF EXISTS (SELECT 1 FROM databasechangelog
             WHERE id = 'add-connector-product-spec-2026-08-12'
               AND author = 'data-platform') THEN
    RAISE EXCEPTION 'U049 后仍残留 changeset';
  END IF;
  SELECT pg_get_functiondef('reject_connector_plugin_version_identity_mutation()'::regprocedure)
  INTO plugin_function;
  SELECT pg_get_functiondef('enforce_vendor_connector_version_immutability()'::regprocedure)
  INTO connector_function;
  IF position('manifest_version' IN plugin_function) > 0
     OR position('authoring_mode' IN connector_function) > 0 THEN
    RAISE EXCEPTION 'U049 未恢复 V047 不可变函数边界';
  END IF;
END $$;
SQL
}

assert_v049_backfill() {
  local db_name="$1"
  psql_db "$db_name" <<'SQL'
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM connector_plugin_version
             WHERE manifest_version <> '1'
                OR authoring_model <> 'ADVANCED_PIPELINE'
                OR connector_kind IS NOT NULL OR transport_mode IS NOT NULL
                OR output_mode IS NOT NULL OR compatibility_manifest IS NOT NULL) THEN
    RAISE EXCEPTION '既有插件未安全回填为 v1/ADVANCED_PIPELINE';
  END IF;
  IF EXISTS (SELECT 1 FROM vendor_connector_version
             WHERE authoring_mode <> 'ADVANCED_LEGACY'
                OR connector_spec IS NOT NULL OR spec_hash IS NOT NULL
                OR compiler_version IS NOT NULL OR compile_hash IS NOT NULL) THEN
    RAISE EXCEPTION '既有连接器未安全回填为 ADVANCED_LEGACY';
  END IF;
  IF EXISTS (SELECT 1 FROM vendor_connector_test_fact
             WHERE authoring_mode <> 'ADVANCED_LEGACY'
                OR spec_hash IS NOT NULL OR compile_hash IS NOT NULL) THEN
    RAISE EXCEPTION '既有测试事实未安全回填为 ADVANCED_LEGACY';
  END IF;
END $$;
SQL
}

snapshot_v049_state() {
  local db_name="$1"
  psql_db "$db_name" -Atq -c "
    SELECT md5(
      COALESCE((SELECT string_agg(id || ':' || author || ':' || exectype,
                                 '|' ORDER BY orderexecuted)
                FROM databasechangelog), '')
      || '|' || (SELECT count(*)::TEXT FROM information_schema.columns
                 WHERE table_schema = 'public')
      || '|' || (SELECT count(*)::TEXT FROM pg_constraint
                 WHERE connamespace = 'public'::regnamespace)
      || '|' || COALESCE((SELECT string_agg(id || ':' || manifest_version || ':' || authoring_model,
                                            '|' ORDER BY id)
                          FROM connector_plugin_version), '')
      || '|' || COALESCE((SELECT string_agg(id || ':' || authoring_mode || ':' || pipeline_snapshot::TEXT,
                                            '|' ORDER BY id)
                          FROM vendor_connector_version), ''))"
}

seed_v2_plugin() {
  local db_name="$1"
  psql_db "$db_name" <<'SQL'
INSERT INTO connector_plugin (
    plugin_id, display_name, provider, description, status, deleted
) VALUES ('v049-halt-plugin', 'V049 Halt Plugin', 'regression', 'rollback probe', 'ACTIVE', FALSE);

INSERT INTO connector_plugin_version (
    plugin_id, version, spi_version, entry_class, artifact_uri, artifact_sha256,
    detached_signature, signing_key_id, manifest_json, config_schema_json,
    capabilities, permission_manifest, min_host_version, status,
    manifest_version, authoring_model, connector_kind, transport_mode,
    output_mode, compatibility_manifest
) VALUES (
    'v049-halt-plugin', '2.0.0', '1.1', 'example.V049Plugin',
    'builtin://v049-halt-plugin/2.0.0', repeat('a', 64), 'regression', 'regression',
    ('{"manifestVersion":"2","pluginId":"v049-halt-plugin","version":"2.0.0",' ||
    '"spiVersion":"1.1","displayName":"V049 Halt Plugin","provider":"regression",' ||
    '"entryClass":"example.V049Plugin","authoringModel":"SIMPLE_CONNECTOR",' ||
    '"connectorKind":"DEDICATED_VENDOR","transportMode":"HOST_SINGLE_HTTP",' ||
    '"outputMode":"HOST_MAPPING","capabilities":["REQUEST_BUILDER","RESPONSE_PARSER"],' ||
    '"compatibility":{"vendorCodes":["*"]},"minHostVersion":"2.0.0",' ||
    '"configSchema":{"type":"object"},' ||
    '"permissions":{"networkProtocols":["https"],"networkHosts":[]}}')::jsonb,
    '{"type":"object"}', '["REQUEST_BUILDER","RESPONSE_PARSER"]',
    '{"networkProtocols":["https"],"networkHosts":[]}', '2.0.0', 'VERIFIED',
    '2', 'SIMPLE_CONNECTOR', 'DEDICATED_VENDOR', 'HOST_SINGLE_HTTP',
    'HOST_MAPPING', '{"vendorCodes":["*"]}'
);
SQL
}

seed_simple_connector() {
  local db_name="$1"
  psql_db "$db_name" <<'SQL'
INSERT INTO vendor_connector_version (
    vendor_config_id, version_no, draft_version, pipeline_snapshot, snapshot_hash,
    hash_algorithm, integrity_hash, authoring_mode, connector_spec, spec_hash,
    compiler_version, compile_hash, security_version, status
)
SELECT min(id), NULL, 1, '[{"probe":1}]', NULL, NULL, NULL,
       'SIMPLE_CONNECTOR', '{"pluginId":"v049-probe","config":{}}',
       repeat('a', 64), '1.0.0', repeat('b', 64), 0, 'DRAFT'
FROM vendor_config;
SQL
}

seed_simple_test_fact() {
  local db_name="$1"
  psql_db "$db_name" <<'SQL'
INSERT INTO vendor_connector_test_fact (
    vendor_config_id, draft_version, snapshot_hash, authoring_mode, spec_hash,
    compile_hash, plugin_bindings, test_succeeded, result_digest
)
SELECT min(id), 1, repeat('a', 64), 'SIMPLE_CONNECTOR', repeat('b', 64),
       repeat('c', 64), '[]', TRUE, repeat('d', 64)
FROM vendor_config;
SQL
}

assert_halt_atomic() {
  local db_name="$1"
  local expected_reference="$2"
  assert_v049_schema "$db_name"
  if [[ "$(psql_db "$db_name" -Atq -c "$expected_reference")" != "1" ]]; then
    fail "U049 HALT 后引用事实丢失或发生半回滚: $db_name"
  fi
}

exercise_constraint_matrix() {
  local db_name="$1"
  psql_db "$db_name" <<'SQL'
INSERT INTO connector_plugin (
    plugin_id, display_name, provider, description, status, deleted
) VALUES
    ('v049-valid-plugin', 'V049 Valid Plugin', 'regression', 'constraint probe', 'ACTIVE', FALSE),
    ('v049-invalid-plugin', 'V049 Invalid Plugin', 'regression', 'constraint probe', 'ACTIVE', FALSE),
    ('v049-drift-plugin', 'V049 Drift Plugin', 'regression', 'constraint probe', 'ACTIVE', FALSE);

INSERT INTO connector_plugin_version (
    plugin_id, version, spi_version, entry_class, artifact_uri, artifact_sha256,
    detached_signature, signing_key_id, manifest_json, config_schema_json,
    capabilities, permission_manifest, min_host_version, status,
    manifest_version, authoring_model, connector_kind, transport_mode,
    output_mode, compatibility_manifest
) VALUES (
    'v049-valid-plugin', '2.0.0', '1.1', 'example.ValidPlugin',
    'builtin://v049-valid-plugin/2.0.0', repeat('a', 64), 'regression', 'regression',
    ('{"manifestVersion":"2","pluginId":"v049-valid-plugin","version":"2.0.0",' ||
    '"spiVersion":"1.1","entryClass":"example.ValidPlugin","authoringModel":"SIMPLE_CONNECTOR",' ||
    '"connectorKind":"DEDICATED_VENDOR","transportMode":"HOST_SINGLE_HTTP",' ||
    '"outputMode":"HOST_MAPPING","configSchema":{"type":"object"},' ||
    '"capabilities":["REQUEST_BUILDER","RESPONSE_PARSER"],' ||
    '"compatibility":{"vendorCodes":["ACME"]},"minHostVersion":"2.0.0",' ||
    '"permissions":{"networkProtocols":["https"],"networkHosts":[]}}')::jsonb,
    '{"type":"object"}', '["REQUEST_BUILDER","RESPONSE_PARSER"]',
    '{"networkProtocols":["https"],"networkHosts":[]}', '2.0.0', 'VERIFIED',
    '2', 'SIMPLE_CONNECTOR', 'DEDICATED_VENDOR', 'HOST_SINGLE_HTTP',
    'HOST_MAPPING', '{"vendorCodes":["ACME"]}'
);

DO $$
DECLARE
  missing_projection_rejected BOOLEAN := FALSE;
  drift_rejected BOOLEAN := FALSE;
  v1_projection_rejected BOOLEAN := FALSE;
  draft_missing_rejected BOOLEAN := FALSE;
  compiler_rejected BOOLEAN := FALSE;
  oversized_rejected BOOLEAN := FALSE;
  empty_pipeline_rejected BOOLEAN := FALSE;
  legacy_spec_rejected BOOLEAN := FALSE;
  simple_test_rejected BOOLEAN := FALSE;
  legacy_test_rejected BOOLEAN := FALSE;
  plugin_frozen BOOLEAN := FALSE;
  authoring_frozen BOOLEAN := FALSE;
  spec_frozen BOOLEAN := FALSE;
  spec_hash_frozen BOOLEAN := FALSE;
  compiler_frozen BOOLEAN := FALSE;
  compile_hash_frozen BOOLEAN := FALSE;
  pipeline_frozen BOOLEAN := FALSE;
  draft_id BIGINT;
  published_id BIGINT;
  affected_rows INTEGER;
  min_config BIGINT;
  max_config BIGINT;
  next_version INTEGER;
BEGIN
  SELECT min(id), max(id) INTO min_config, max_config FROM vendor_config;
  IF min_config IS NULL OR max_config IS NULL OR min_config = max_config THEN
    RAISE EXCEPTION 'V049 约束矩阵需要两个隔离 vendor_config fixture';
  END IF;

  BEGIN
    INSERT INTO connector_plugin_version (
        plugin_id, version, spi_version, entry_class, artifact_uri, artifact_sha256,
        detached_signature, signing_key_id, manifest_json, config_schema_json,
        capabilities, permission_manifest, min_host_version, status,
        manifest_version, authoring_model, connector_kind, transport_mode,
        output_mode, compatibility_manifest
    ) VALUES (
        'v049-invalid-plugin', '2.0.0', '1.1', 'example.InvalidPlugin',
        'builtin://v049-invalid-plugin/2.0.0', repeat('b', 64), 'regression', 'regression',
        ('{"manifestVersion":"2","pluginId":"v049-invalid-plugin","version":"2.0.0",' ||
        '"spiVersion":"1.1","entryClass":"example.InvalidPlugin",' ||
        '"authoringModel":"SIMPLE_CONNECTOR","connectorKind":"DEDICATED_VENDOR",' ||
        '"transportMode":"HOST_SINGLE_HTTP","outputMode":"HOST_MAPPING",' ||
        '"compatibility":{"vendorCodes":["*"]},"configSchema":{"type":"object"},' ||
        '"capabilities":["REQUEST_BUILDER","RESPONSE_PARSER"],"minHostVersion":"2.0.0",' ||
        '"permissions":{"networkProtocols":["https"],"networkHosts":[]}}')::jsonb,
        '{"type":"object"}', '["REQUEST_BUILDER","RESPONSE_PARSER"]', '{}', '2.0.0', 'VERIFIED',
        '2', 'SIMPLE_CONNECTOR', 'DEDICATED_VENDOR', NULL, 'HOST_MAPPING', '{"vendorCodes":["*"]}'
    );
  EXCEPTION WHEN check_violation THEN
    missing_projection_rejected := TRUE;
  END;

  BEGIN
    INSERT INTO connector_plugin_version (
        plugin_id, version, spi_version, entry_class, artifact_uri, artifact_sha256,
        detached_signature, signing_key_id, manifest_json, config_schema_json,
        capabilities, permission_manifest, min_host_version, status,
        manifest_version, authoring_model, connector_kind, transport_mode,
        output_mode, compatibility_manifest
    ) VALUES (
        'v049-drift-plugin', '2.0.0', '1.1', 'example.DriftPlugin',
        'builtin://v049-drift-plugin/2.0.0', repeat('c', 64), 'regression', 'regression',
        ('{"manifestVersion":"2","pluginId":"v049-drift-plugin","version":"2.0.0",' ||
        '"spiVersion":"1.1","entryClass":"example.DriftPlugin",' ||
        '"authoringModel":"SIMPLE_CONNECTOR","connectorKind":"DEDICATED_VENDOR",' ||
        '"transportMode":"HOST_SINGLE_HTTP","outputMode":"HOST_MAPPING",' ||
        '"compatibility":{"vendorCodes":["ACME"]},"configSchema":{"type":"object"},' ||
        '"capabilities":["REQUEST_BUILDER","RESPONSE_PARSER"],"minHostVersion":"2.0.0",' ||
        '"permissions":{"networkProtocols":["https"],"networkHosts":[]}}')::jsonb,
        '{"type":"object"}', '["REQUEST_BUILDER","RESPONSE_PARSER"]', '{}', '2.0.0', 'VERIFIED',
        '2', 'SIMPLE_CONNECTOR', 'DEDICATED_VENDOR', 'HOST_SINGLE_HTTP', 'HOST_MAPPING',
        '{"vendorCodes":["OTHER"]}'
    );
  EXCEPTION WHEN check_violation THEN
    drift_rejected := TRUE;
  END;

  BEGIN
    INSERT INTO connector_plugin_version (
        plugin_id, version, spi_version, entry_class, artifact_uri, artifact_sha256,
        detached_signature, signing_key_id, manifest_json, config_schema_json,
        capabilities, permission_manifest, min_host_version, status,
        manifest_version, authoring_model, connector_kind
    ) VALUES (
        'v049-invalid-plugin', '1.0.0', '1.0', 'example.InvalidPlugin',
        'builtin://v049-invalid-plugin/1.0.0', repeat('d', 64), 'regression', 'regression',
        ('{"manifestVersion":"1","pluginId":"v049-invalid-plugin","version":"1.0.0",' ||
        '"spiVersion":"1.0","entryClass":"example.InvalidPlugin"}')::jsonb,
        '{}', '["TRANSPORT"]', '{}', '1.0.0', 'VERIFIED',
        '1', 'ADVANCED_PIPELINE', 'DEDICATED_VENDOR'
    );
  EXCEPTION WHEN check_violation THEN
    v1_projection_rejected := TRUE;
  END;

  INSERT INTO vendor_connector_version (
      vendor_config_id, version_no, draft_version, pipeline_snapshot, snapshot_hash,
      hash_algorithm, integrity_hash, authoring_mode, connector_spec, spec_hash,
      compiler_version, compile_hash, security_version, status
  ) VALUES (
      min_config, NULL, 1, '[{"revision":1}]', NULL, NULL, NULL,
      'SIMPLE_CONNECTOR', '{"pluginId":"v049-valid-plugin","config":{"revision":1}}',
      repeat('1', 64), '1.0.0', repeat('2', 64), 0, 'DRAFT'
  ) RETURNING id INTO draft_id;

  UPDATE vendor_connector_version
  SET draft_version = 2,
      connector_spec = '{"pluginId":"v049-valid-plugin","config":{"revision":2}}',
      spec_hash = repeat('3', 64), compiler_version = '1.1', compile_hash = repeat('4', 64),
      pipeline_snapshot = '[{"revision":2}]'
  WHERE id = draft_id AND draft_version = 1 AND status = 'DRAFT';
  GET DIAGNOSTICS affected_rows = ROW_COUNT;
  IF affected_rows <> 1 OR NOT EXISTS (
      SELECT 1 FROM vendor_connector_version
      WHERE id = draft_id AND draft_version = 2 AND snapshot_hash IS NULL
        AND connector_spec = '{"pluginId":"v049-valid-plugin","config":{"revision":2}}'::jsonb
        AND spec_hash = repeat('3', 64) AND compiler_version = '1.1'
        AND compile_hash = repeat('4', 64) AND pipeline_snapshot = '[{"revision":2}]'::jsonb
  ) THEN
    RAISE EXCEPTION 'SIMPLE DRAFT CAS authoring update was not preserved';
  END IF;

  BEGIN
    INSERT INTO vendor_connector_version (
        vendor_config_id, draft_version, pipeline_snapshot, authoring_mode,
        connector_spec, spec_hash, compiler_version, compile_hash, security_version, status
    ) VALUES (max_config, 1, '[{"probe":1}]', 'SIMPLE_CONNECTOR', '{}',
              repeat('a', 64), '1.0.0', NULL, 0, 'DRAFT');
  EXCEPTION WHEN check_violation THEN
    draft_missing_rejected := TRUE;
  END;

  BEGIN
    INSERT INTO vendor_connector_version (
        vendor_config_id, draft_version, pipeline_snapshot, authoring_mode,
        connector_spec, spec_hash, compiler_version, compile_hash, security_version, status
    ) VALUES (max_config, 1, '[{"probe":1}]', 'SIMPLE_CONNECTOR', '{}',
              repeat('a', 64), '1x0x0', repeat('b', 64), 0, 'DRAFT');
  EXCEPTION WHEN check_violation THEN
    compiler_rejected := TRUE;
  END;

  BEGIN
    INSERT INTO vendor_connector_version (
        vendor_config_id, draft_version, pipeline_snapshot, authoring_mode,
        connector_spec, spec_hash, compiler_version, compile_hash, security_version, status
    ) VALUES (max_config, 1, '[{"probe":1}]', 'SIMPLE_CONNECTOR',
              jsonb_build_object('payload', repeat('x', 131073)), repeat('a', 64),
              '1.0.0', repeat('b', 64), 0, 'DRAFT');
  EXCEPTION WHEN check_violation THEN
    oversized_rejected := TRUE;
  END;

  BEGIN
    INSERT INTO vendor_connector_version (
        vendor_config_id, draft_version, pipeline_snapshot, authoring_mode,
        connector_spec, spec_hash, compiler_version, compile_hash, security_version, status
    ) VALUES (max_config, 1, '[]', 'SIMPLE_CONNECTOR', '{}', repeat('a', 64),
              '1.0.0', repeat('b', 64), 0, 'DRAFT');
  EXCEPTION WHEN check_violation THEN
    empty_pipeline_rejected := TRUE;
  END;

  BEGIN
    INSERT INTO vendor_connector_version (
        vendor_config_id, draft_version, pipeline_snapshot, authoring_mode,
        connector_spec, security_version, status
    ) VALUES (max_config, 1, '[]', 'ADVANCED_LEGACY', '{}', 0, 'DRAFT');
  EXCEPTION WHEN check_violation THEN
    legacy_spec_rejected := TRUE;
  END;

  SELECT COALESCE(max(version_no), 0) + 100 INTO next_version
  FROM vendor_connector_version WHERE vendor_config_id = max_config;
  INSERT INTO vendor_connector_version (
      vendor_config_id, version_no, draft_version, pipeline_snapshot, snapshot_hash,
      hash_algorithm, integrity_hash, authoring_mode, connector_spec, spec_hash,
      compiler_version, compile_hash, security_version, status
  ) VALUES (
      max_config, next_version, 2, '[{"published":true}]', repeat('5', 64),
      'V2_EMBEDDED', repeat('5', 64), 'SIMPLE_CONNECTOR',
      '{"pluginId":"v049-valid-plugin","config":{}}', repeat('6', 64),
      '1.0.0', repeat('7', 64), 0, 'PUBLISHED'
  ) RETURNING id INTO published_id;

  BEGIN
    UPDATE connector_plugin_version SET compatibility_manifest = '{"vendorCodes":["OTHER"]}'
    WHERE plugin_id = 'v049-valid-plugin' AND version = '2.0.0';
    RAISE EXCEPTION 'plugin projection mutation unexpectedly succeeded';
  EXCEPTION WHEN raise_exception THEN
    plugin_frozen := SQLERRM = 'connector_plugin_version artifact identity is immutable';
  END;
  BEGIN
    UPDATE vendor_connector_version SET authoring_mode = 'ADVANCED_LEGACY' WHERE id = published_id;
    RAISE EXCEPTION 'published authoring mutation unexpectedly succeeded';
  EXCEPTION WHEN raise_exception THEN
    authoring_frozen := SQLERRM = 'published vendor_connector_version facts are immutable';
  END;
  BEGIN
    UPDATE vendor_connector_version SET connector_spec = '{"changed":true}' WHERE id = published_id;
    RAISE EXCEPTION 'published spec mutation unexpectedly succeeded';
  EXCEPTION WHEN raise_exception THEN
    spec_frozen := SQLERRM = 'published vendor_connector_version facts are immutable';
  END;
  BEGIN
    UPDATE vendor_connector_version SET spec_hash = repeat('8', 64) WHERE id = published_id;
    RAISE EXCEPTION 'published spec hash mutation unexpectedly succeeded';
  EXCEPTION WHEN raise_exception THEN
    spec_hash_frozen := SQLERRM = 'published vendor_connector_version facts are immutable';
  END;
  BEGIN
    UPDATE vendor_connector_version SET compiler_version = '1.2.0' WHERE id = published_id;
    RAISE EXCEPTION 'published compiler mutation unexpectedly succeeded';
  EXCEPTION WHEN raise_exception THEN
    compiler_frozen := SQLERRM = 'published vendor_connector_version facts are immutable';
  END;
  BEGIN
    UPDATE vendor_connector_version SET compile_hash = repeat('9', 64) WHERE id = published_id;
    RAISE EXCEPTION 'published compile hash mutation unexpectedly succeeded';
  EXCEPTION WHEN raise_exception THEN
    compile_hash_frozen := SQLERRM = 'published vendor_connector_version facts are immutable';
  END;
  BEGIN
    UPDATE vendor_connector_version SET pipeline_snapshot = '[{"changed":true}]' WHERE id = published_id;
    RAISE EXCEPTION 'published pipeline mutation unexpectedly succeeded';
  EXCEPTION WHEN raise_exception THEN
    pipeline_frozen := SQLERRM = 'published vendor_connector_version facts are immutable';
  END;

  INSERT INTO vendor_connector_test_fact (
      vendor_config_id, draft_version, snapshot_hash, authoring_mode, spec_hash,
      compile_hash, plugin_bindings, test_succeeded, result_digest
  ) VALUES (min_config, 2, repeat('a', 64), 'SIMPLE_CONNECTOR', repeat('b', 64),
            repeat('c', 64), '["v049-valid-plugin:2.0.0"]', TRUE, repeat('d', 64));

  BEGIN
    INSERT INTO vendor_connector_test_fact (
        vendor_config_id, draft_version, snapshot_hash, authoring_mode, spec_hash,
        compile_hash, plugin_bindings, test_succeeded, result_digest
    ) VALUES (max_config, 1, repeat('a', 64), 'SIMPLE_CONNECTOR', NULL,
              repeat('c', 64), '[]', TRUE, repeat('d', 64));
  EXCEPTION WHEN check_violation THEN
    simple_test_rejected := TRUE;
  END;
  BEGIN
    INSERT INTO vendor_connector_test_fact (
        vendor_config_id, draft_version, snapshot_hash, authoring_mode, spec_hash,
        compile_hash, plugin_bindings, test_succeeded, result_digest
    ) VALUES (max_config, 1, repeat('a', 64), 'ADVANCED_LEGACY', repeat('b', 64),
              NULL, '[]', TRUE, repeat('d', 64));
  EXCEPTION WHEN check_violation THEN
    legacy_test_rejected := TRUE;
  END;

  IF NOT (missing_projection_rejected AND drift_rejected AND v1_projection_rejected
      AND draft_missing_rejected AND compiler_rejected AND oversized_rejected
      AND empty_pipeline_rejected AND legacy_spec_rejected
      AND simple_test_rejected AND legacy_test_rejected
      AND plugin_frozen AND authoring_frozen AND spec_frozen AND spec_hash_frozen
      AND compiler_frozen AND compile_hash_frozen AND pipeline_frozen) THEN
    RAISE EXCEPTION 'V049 正负约束/冻结矩阵未完整命中: %,%,%,%,%,%,%,%,%,%,%,%,%,%,%,%,%',
      missing_projection_rejected, drift_rejected, v1_projection_rejected,
      draft_missing_rejected, compiler_rejected, oversized_rejected,
      empty_pipeline_rejected, legacy_spec_rejected, simple_test_rejected,
      legacy_test_rejected, plugin_frozen, authoring_frozen, spec_frozen,
      spec_hash_frozen, compiler_frozen, compile_hash_frozen, pipeline_frozen;
  END IF;
END $$;
SQL
}

create_database "$FRESH_DB"
run_v049_update "$FRESH_DB"
run_migration "$FRESH_DB" validate
assert_v049_schema "$FRESH_DB"
assert_v049_backfill "$FRESH_DB"
pass "V001-V049 fresh、Liquibase validate、V049 schema 与 Legacy backfill: $FRESH_DB"

FRESH_SNAPSHOT="$(snapshot_v049_state "$FRESH_DB")"
run_v049_update "$FRESH_DB"
FRESH_SNAPSHOT_AFTER="$(snapshot_v049_state "$FRESH_DB")"
[[ "$FRESH_SNAPSHOT" == "$FRESH_SNAPSHOT_AFTER" ]] || fail "重复 update 改变 V049 状态"
assert_v049_schema "$FRESH_DB"
pass "重复 update 无重复 changeset、对象或数据变化: $FRESH_DB"

clone_database "$FRESH_DB" "$V048_TEMPLATE_DB"
run_rollback "$V048_TEMPLATE_DB"
assert_v048_surface "$V048_TEMPLATE_DB"
psql_db "$V048_TEMPLATE_DB" <<'SQL'
INSERT INTO vendor_connector_test_fact (
    vendor_config_id, draft_version, snapshot_hash, plugin_bindings,
    test_succeeded, result_digest
)
SELECT min(vendor_config_id), 1, min(snapshot_hash), '["legacy-http:1.0.0"]',
       TRUE, repeat('e', 64)
FROM vendor_connector_version
WHERE status <> 'DRAFT';

CREATE TABLE v049_upgrade_probe AS
SELECT id, pipeline_snapshot::TEXT AS pipeline_text, snapshot_hash,
       hash_algorithm, integrity_hash
FROM vendor_connector_version
ORDER BY id;
SQL

clone_database "$V048_TEMPLATE_DB" "$UPGRADE_DB"
clone_database "$V048_TEMPLATE_DB" "$MISSING_DEPENDENCY_DB"
clone_database "$V048_TEMPLATE_DB" "$PREEXISTING_V2_DB"

run_v049_update "$UPGRADE_DB"
assert_v049_schema "$UPGRADE_DB"
assert_v049_backfill "$UPGRADE_DB"
psql_db "$UPGRADE_DB" <<'SQL'
DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM vendor_connector_version version
      FULL JOIN v049_upgrade_probe probe USING (id)
      WHERE version.id IS NULL OR probe.id IS NULL
         OR version.pipeline_snapshot::TEXT IS DISTINCT FROM probe.pipeline_text
         OR version.snapshot_hash IS DISTINCT FROM probe.snapshot_hash
         OR version.hash_algorithm IS DISTINCT FROM probe.hash_algorithm
         OR version.integrity_hash IS DISTINCT FROM probe.integrity_hash
  ) THEN
    RAISE EXCEPTION 'V048->V049 改写了 pipeline/hash/integrity 历史字节';
  END IF;
END $$;
SQL
pass "V048->V049 保持 pipeline_snapshot::text 与三类 digest 字段字节不变: $UPGRADE_DB"

psql_db "$MISSING_DEPENDENCY_DB" <<'SQL'
DROP TRIGGER trg_api_interface_vendor_routing_v048 ON api_interface;
SQL
expect_update_failure "$MISSING_DEPENDENCY_DB" "required V047/V048 history or objects are missing"
assert_v048_surface "$MISSING_DEPENDENCY_DB"
pass "V047/V048 历史或对象缺失时 V049 HALT 且无半迁移: $MISSING_DEPENDENCY_DB"

psql_db "$PREEXISTING_V2_DB" <<'SQL'
ALTER TABLE connector_plugin_version DISABLE TRIGGER trg_connector_plugin_version_immutable;
UPDATE connector_plugin_version
SET manifest_json = jsonb_set(manifest_json, '{manifestVersion}', '"2"'::jsonb)
WHERE plugin_id = 'legacy-http' AND version = '1.0.0';
ALTER TABLE connector_plugin_version ENABLE TRIGGER trg_connector_plugin_version_immutable;
SQL
expect_update_failure "$PREEXISTING_V2_DB" "plugin history integrity drift"
assert_v048_surface "$PREEXISTING_V2_DB"
pass "V049 前发现存量 Manifest v2 时拒绝伪回填且事务原子: $PREEXISTING_V2_DB"

clone_database "$FRESH_DB" "$SAFE_ROLLBACK_DB"
clone_database "$FRESH_DB" "$HALT_PLUGIN_DB"
clone_database "$FRESH_DB" "$HALT_CONNECTOR_DB"
clone_database "$FRESH_DB" "$HALT_TEST_DB"

run_rollback "$SAFE_ROLLBACK_DB"
assert_v048_surface "$SAFE_ROLLBACK_DB"
psql_db "$SAFE_ROLLBACK_DB" <<'SQL'
DO $$
DECLARE
  plugin_frozen BOOLEAN := FALSE;
  connector_frozen BOOLEAN := FALSE;
BEGIN
  BEGIN
    UPDATE connector_plugin_version SET artifact_uri = artifact_uri || '-changed'
    WHERE plugin_id = 'legacy-http' AND version = '1.0.0';
    RAISE EXCEPTION 'V047 plugin protection unexpectedly absent';
  EXCEPTION WHEN raise_exception THEN
    plugin_frozen := SQLERRM = 'connector_plugin_version artifact identity is immutable';
  END;
  BEGIN
    UPDATE vendor_connector_version SET pipeline_snapshot = '[]'
    WHERE id = (SELECT min(id) FROM vendor_connector_version WHERE status <> 'DRAFT');
    RAISE EXCEPTION 'V047 connector protection unexpectedly absent';
  EXCEPTION WHEN raise_exception THEN
    connector_frozen := SQLERRM = 'published vendor_connector_version facts are immutable';
  END;
  IF NOT plugin_frozen OR NOT connector_frozen THEN
    RAISE EXCEPTION 'U049 未恢复 V047 冻结语义';
  END IF;
END $$;
SQL
run_v049_update "$SAFE_ROLLBACK_DB"
assert_v049_schema "$SAFE_ROLLBACK_DB"
assert_v049_backfill "$SAFE_ROLLBACK_DB"
pass "无 SIMPLE/v2 引用时 U049 成功、V047 保护恢复且可重新应用 V049: $SAFE_ROLLBACK_DB"

seed_v2_plugin "$HALT_PLUGIN_DB"
expect_rollback_failure "$HALT_PLUGIN_DB" "U049 rollback HALT: connector product facts exist"
assert_halt_atomic "$HALT_PLUGIN_DB" \
  "SELECT count(*) FROM connector_plugin_version WHERE plugin_id = 'v049-halt-plugin' AND manifest_version = '2'"
pass "v2/SIMPLE 插件投影引用令 U049 HALT 且无半回滚: $HALT_PLUGIN_DB"

seed_simple_connector "$HALT_CONNECTOR_DB"
expect_rollback_failure "$HALT_CONNECTOR_DB" "U049 rollback HALT: connector product facts exist"
assert_halt_atomic "$HALT_CONNECTOR_DB" \
  "SELECT count(*) FROM vendor_connector_version WHERE authoring_mode = 'SIMPLE_CONNECTOR'"
pass "SIMPLE connector 引用令 U049 HALT 且无半回滚: $HALT_CONNECTOR_DB"

seed_simple_test_fact "$HALT_TEST_DB"
expect_rollback_failure "$HALT_TEST_DB" "U049 rollback HALT: connector product facts exist"
assert_halt_atomic "$HALT_TEST_DB" \
  "SELECT count(*) FROM vendor_connector_test_fact WHERE authoring_mode = 'SIMPLE_CONNECTOR'"
pass "SIMPLE test fact 引用令 U049 HALT 且无半回滚: $HALT_TEST_DB"

exercise_constraint_matrix "$FRESH_DB"
pass "插件精确投影、SIMPLE/Legacy、128KiB、digest、PostgreSQL compiler regex、DRAFT CAS 与 published freeze 矩阵: $FRESH_DB"

echo "V049/U049 隔离数据库验证完成。所有回归数据库将在退出时自动清理。"
