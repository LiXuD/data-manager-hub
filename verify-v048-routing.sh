#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-123456}"
export DB_HOST DB_PORT DB_USERNAME DB_PASSWORD PGPASSWORD="$DB_PASSWORD"

DB_NAME_PATTERN='^dataplatform_[a-z0-9_]+_regression$'
RUN_ID="$(date -u +%Y%m%d%H%M%S)"
FRESH_DB="dataplatform_v048_${RUN_ID}_fresh_regression"
UPGRADE_TEMPLATE_DB="dataplatform_v048_${RUN_ID}_v047template_regression"
UPGRADE_DB="dataplatform_v048_${RUN_ID}_upgrade_regression"
DUPLICATE_DB="dataplatform_v048_${RUN_ID}_duplicate_regression"
AMBIGUOUS_DB="dataplatform_v048_${RUN_ID}_ambiguous_regression"
ROLLBACK_DB="dataplatform_v048_${RUN_ID}_rollback_regression"
REGRESSION_DATABASES=(
  "$FRESH_DB"
  "$UPGRADE_TEMPLATE_DB"
  "$UPGRADE_DB"
  "$DUPLICATE_DB"
  "$AMBIGUOUS_DB"
  "$ROLLBACK_DB"
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
  [[ "$db_name" =~ $DB_NAME_PATTERN ]] ||
    fail "拒绝非隔离数据库名: $db_name"
  [[ "$db_name" != "dataplatform" ]] ||
    fail "拒绝生产数据库名: $db_name"
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
    sed -n '1,160p' "$log_file" >&2
    return 1
  fi
}

expect_migration_failure() {
  local db_name="$1"
  local expected_text="$2"
  assert_regression_db_name "$db_name"
  local log_file="$LOG_DIR/${db_name}_expected_failure.log"
  if DB_NAME="$db_name" bash ./migrate-db.sh update >"$log_file" 2>&1; then
    echo "预期失败但迁移成功: $db_name" >&2
    sed -n '1,160p' "$log_file" >&2
    return 1
  fi
  grep -Fq "$expected_text" "$log_file" || {
    echo "迁移失败原因不匹配，未找到: $expected_text" >&2
    sed -n '1,160p' "$log_file" >&2
    return 1
  }
}

assert_v048_schema() {
  local db_name="$1"
  psql_db "$db_name" <<'SQL'
DO $$
BEGIN
  IF (SELECT count(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'api_interface'
        AND column_name IN ('primary_vendor_config_id', 'fallback_vendor_config_id')) <> 2 THEN
    RAISE EXCEPTION 'V048 路由列不完整';
  END IF;

  IF to_regclass('public.connector_plugin_activation') IS NULL THEN
    RAISE EXCEPTION 'connector_plugin_activation 不存在';
  END IF;

  IF to_regclass('public.ux_vendor_config_interface_vendor_active_v048') IS NULL THEN
    RAISE EXCEPTION 'V048 同接口厂商唯一索引不存在';
  END IF;

  IF to_regclass('public.vendor_config_vendor_id_data_type_id_key') IS NOT NULL
     OR EXISTS (
       SELECT 1 FROM pg_constraint
       WHERE conrelid = 'public.vendor_config'::regclass
         AND conname = 'vendor_config_vendor_id_data_type_id_key'
     ) THEN
    RAISE EXCEPTION '旧厂商/数据类型唯一约束仍存在';
  END IF;

  IF NOT EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conrelid = 'public.vendor_config'::regclass
        AND conname = 'fk_vendor_config_interface_v048'
  ) OR NOT EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conrelid = 'public.vendor_config'::regclass
        AND conname = 'uq_vendor_config_id_interface_v048'
  ) THEN
    RAISE EXCEPTION 'vendor_config 接口 FK/复合唯一约束不完整';
  END IF;

  IF (SELECT count(*)
      FROM pg_constraint
      WHERE conrelid = 'public.api_interface'::regclass
        AND conname IN (
          'fk_api_interface_primary_vendor_config_v048',
          'fk_api_interface_fallback_vendor_config_v048'
        )) <> 2 THEN
    RAISE EXCEPTION '主/备用同接口复合 FK 不完整';
  END IF;

  IF NOT EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conrelid = 'public.api_interface'::regclass
        AND conname = 'ck_api_interface_vendor_routing_distinct_v048'
        AND contype = 'c'
  ) THEN
    RAISE EXCEPTION '主备不同 CHECK 不存在';
  END IF;

  IF NOT EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_protect_referenced_vendor_config_routing_v048'
        AND NOT tgisinternal
  ) THEN
    RAISE EXCEPTION '路由引用删除保护 trigger 不存在';
  END IF;

  IF NOT EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_api_interface_vendor_routing_v048'
        AND NOT tgisinternal
  ) THEN
    RAISE EXCEPTION '路由归属保护 trigger 不存在';
  END IF;
END $$;
SQL
}

seed_test_interface() {
  local db_name="$1"
  local interface_code="v048_${RUN_ID}_fresh"
  local primary_code="v048_${RUN_ID}_fresh_primary"
  local fallback_code="v048_${RUN_ID}_fresh_fallback"
  local other_code="v048_${RUN_ID}_fresh_other"
  local data_type_code="v048_${RUN_ID}_fresh_type"
  local alt_data_type_code="v048_${RUN_ID}_fresh_alt"

  psql_db "$db_name" <<SQL
INSERT INTO data_type (data_type_code, data_type_name, data_category, status, deleted)
VALUES ('$data_type_code', 'V048 回归类型', 'regression', 'active', FALSE),
       ('$alt_data_type_code', 'V048 回归备用类型', 'regression', 'active', FALSE);

INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, deleted)
VALUES ('$primary_code', 'V048 主厂商', 'regression', 'active', FALSE),
       ('$fallback_code', 'V048 备用厂商', 'regression', 'active', FALSE),
       ('$other_code', 'V048 其他厂商', 'regression', 'active', FALSE);

INSERT INTO api_interface (
    interface_code, interface_name, data_type_id, path, description, status, deleted
)
SELECT '$interface_code', 'V048 路由回归接口', id, NULL, 'V048 regression', 'inactive', FALSE
FROM data_type WHERE data_type_code = '$data_type_code';

INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
    circuit_threshold, circuit_timeout, fallback_vendor_id, security_version,
    status, runtime_mode, connector_version, deleted
)
SELECT vendor.id, data_type.id, data_type.data_type_code, interface.id, 10000, 0,
       10, 30000, fallback.id, 0, 'inactive', 'PLUGIN', 0, FALSE
FROM vendor_info vendor
JOIN data_type ON data_type.data_type_code = '$data_type_code'
JOIN api_interface interface ON interface.interface_code = '$interface_code'
JOIN vendor_info fallback ON fallback.vendor_code = '$fallback_code'
WHERE vendor.vendor_code = '$primary_code';

INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
    circuit_threshold, circuit_timeout, fallback_vendor_id, security_version,
    status, runtime_mode, connector_version, deleted
)
SELECT vendor.id, data_type.id, data_type.data_type_code, interface.id, 10000, 0,
       10, 30000, NULL, 0, 'inactive', 'PLUGIN', 0, FALSE
FROM vendor_info vendor
JOIN data_type ON data_type.data_type_code = '$data_type_code'
JOIN api_interface interface ON interface.interface_code = '$interface_code'
WHERE vendor.vendor_code = '$fallback_code';

UPDATE api_interface interface
SET vendor_id = primary_vendor.id
FROM vendor_info primary_vendor
WHERE interface.interface_code = '$interface_code'
  AND primary_vendor.vendor_code = '$primary_code';
SQL
}

make_v047_equivalent() {
  local db_name="$1"
  create_database "$db_name"
  run_migration "$db_name" update
  psql_db "$db_name" <<'SQL'
DROP TRIGGER IF EXISTS trg_protect_referenced_vendor_config_routing_v048 ON vendor_config;
DROP TRIGGER IF EXISTS trg_api_interface_vendor_routing_v048 ON api_interface;
DROP FUNCTION IF EXISTS protect_referenced_vendor_config_routing_v048();
DROP FUNCTION IF EXISTS enforce_api_interface_vendor_routing_v048();
ALTER TABLE api_interface
    DROP CONSTRAINT IF EXISTS fk_api_interface_primary_vendor_config_v048,
    DROP CONSTRAINT IF EXISTS fk_api_interface_fallback_vendor_config_v048,
    DROP CONSTRAINT IF EXISTS ck_api_interface_vendor_routing_distinct_v048;
ALTER TABLE vendor_config
    DROP CONSTRAINT IF EXISTS fk_vendor_config_interface_v048,
    DROP CONSTRAINT IF EXISTS uq_vendor_config_id_interface_v048;
DROP INDEX IF EXISTS ux_vendor_config_interface_vendor_active_v048;
DROP INDEX IF EXISTS idx_api_interface_primary_vendor_config_v048;
DROP INDEX IF EXISTS idx_api_interface_fallback_vendor_config_v048;
ALTER TABLE api_interface
    DROP COLUMN IF EXISTS primary_vendor_config_id,
    DROP COLUMN IF EXISTS fallback_vendor_config_id;
ALTER TABLE vendor_config ALTER COLUMN interface_id DROP NOT NULL;
ALTER TABLE vendor_config
    ADD CONSTRAINT vendor_config_vendor_id_data_type_id_key UNIQUE (vendor_id, data_type_id);
DELETE FROM databasechangelog
WHERE id = 'enforce-interface-vendor-routing-2026-08-11'
  AND author = 'data-platform';
SQL
}

clone_database() {
  local template_db="$1"
  local db_name="$2"
  assert_regression_db_name "$template_db"
  assert_regression_db_name "$db_name"
  "${PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$db_name\" WITH (FORCE)" >/dev/null
  "${PSQL[@]}" -d postgres -c "CREATE DATABASE \"$db_name\" OWNER \"$DB_USERNAME\" TEMPLATE \"$template_db\"" >/dev/null
}

seed_valid_legacy_route() {
  local db_name="$1"
  local suffix="$2"
  local interface_code="v048_${RUN_ID}_${suffix}"
  local primary_code="v048_${RUN_ID}_${suffix}_primary"
  local fallback_code="v048_${RUN_ID}_${suffix}_fallback"
  local other_code="v048_${RUN_ID}_${suffix}_other"
  local data_type_code="v048_${RUN_ID}_${suffix}_type"

  psql_db "$db_name" <<SQL
INSERT INTO data_type (data_type_code, data_type_name, data_category, status, deleted)
VALUES ('$data_type_code', 'V048 $suffix 类型', 'regression', 'active', FALSE);
INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, deleted)
VALUES ('$primary_code', 'V048 $suffix 主厂商', 'regression', 'active', FALSE),
       ('$fallback_code', 'V048 $suffix 备用厂商', 'regression', 'active', FALSE),
       ('$other_code', 'V048 $suffix 其他厂商', 'regression', 'active', FALSE);
INSERT INTO api_interface (
    interface_code, interface_name, data_type_id, vendor_id, path, status, deleted
)
SELECT '$interface_code', 'V048 $suffix 接口', data_type.id, primary_vendor.id,
       '/legacy/$suffix', 'inactive', FALSE
FROM data_type
JOIN vendor_info primary_vendor ON primary_vendor.vendor_code = '$primary_code'
WHERE data_type.data_type_code = '$data_type_code';
INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
    circuit_threshold, circuit_timeout, fallback_vendor_id, security_version,
    status, runtime_mode, connector_version, deleted
)
SELECT primary_vendor.id, data_type.id, data_type.data_type_code, interface.id,
       10000, 0, 10, 30000, fallback_vendor.id, 0, 'inactive', 'PLUGIN', 0, FALSE
FROM vendor_info primary_vendor
JOIN data_type ON data_type.data_type_code = '$data_type_code'
JOIN api_interface interface ON interface.interface_code = '$interface_code'
JOIN vendor_info fallback_vendor ON fallback_vendor.vendor_code = '$fallback_code'
WHERE primary_vendor.vendor_code = '$primary_code';
INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
    circuit_threshold, circuit_timeout, fallback_vendor_id, security_version,
    status, runtime_mode, connector_version, deleted
)
SELECT fallback_vendor.id, data_type.id, data_type.data_type_code, interface.id,
       10000, 0, 10, 30000, fallback_vendor.id, 0, 'inactive', 'PLUGIN', 0, FALSE
FROM vendor_info fallback_vendor
JOIN data_type ON data_type.data_type_code = '$data_type_code'
JOIN api_interface interface ON interface.interface_code = '$interface_code'
WHERE fallback_vendor.vendor_code = '$fallback_code';
SQL
}

create_database "$FRESH_DB"
run_migration "$FRESH_DB" update
assert_v048_schema "$FRESH_DB"
psql_db "$FRESH_DB" -c "SELECT 1 FROM databasechangelog WHERE id = 'enforce-interface-vendor-routing-2026-08-11' AND author = 'data-platform'" >/dev/null
seed_test_interface "$FRESH_DB"
psql_db "$FRESH_DB" <<SQL
DO \$\$
DECLARE
  v_interface_id BIGINT;
  v_primary_id BIGINT;
  v_fallback_id BIGINT;
  v_other_vendor_id BIGINT;
  v_other_config_id BIGINT;
  duplicate_rejected BOOLEAN := FALSE;
  delete_rejected BOOLEAN := FALSE;
BEGIN
  SELECT ai.id INTO v_interface_id
  FROM api_interface ai
  WHERE ai.interface_code = 'v048_${RUN_ID}_fresh';
  SELECT vc.id INTO v_primary_id
  FROM vendor_config vc
  WHERE vc.interface_id = v_interface_id
  ORDER BY vc.id LIMIT 1;
  SELECT vc.id INTO v_fallback_id
  FROM vendor_config vc
  WHERE vc.interface_id = v_interface_id
  ORDER BY vc.id OFFSET 1 LIMIT 1;
  SELECT vi.id INTO v_other_vendor_id
  FROM vendor_info vi
  WHERE vi.vendor_code = 'v048_${RUN_ID}_fresh_other';

  IF v_interface_id IS NULL OR v_primary_id IS NULL OR v_fallback_id IS NULL
     OR v_primary_id = v_fallback_id OR v_other_vendor_id IS NULL THEN
    RAISE EXCEPTION 'V048 回归探针未找到预期主/备用/其他配置';
  END IF;

  INSERT INTO vendor_config (
      vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
      circuit_threshold, circuit_timeout, status, runtime_mode, connector_version, deleted
  )
  SELECT v_other_vendor_id, data_type_id, data_type_code, interface_id, 10000, 0,
         10, 30000, 'inactive', 'PLUGIN', 0, FALSE
  FROM vendor_config WHERE id = v_primary_id
  RETURNING id INTO v_other_config_id;

  UPDATE api_interface
  SET primary_vendor_config_id = v_primary_id,
      fallback_vendor_config_id = v_fallback_id
  WHERE api_interface.id = v_interface_id;

  BEGIN
    UPDATE api_interface
    SET primary_vendor_config_id = v_fallback_id
    WHERE api_interface.id = v_interface_id;
    RAISE EXCEPTION '主备相同 CHECK 未拒绝';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO vendor_config (
        vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
        circuit_threshold, circuit_timeout, status, runtime_mode, connector_version, deleted
    )
    SELECT vendor_id, data_type_id, data_type_code, interface_id, 10000, 0,
           10, 30000, 'inactive', 'PLUGIN', 0, FALSE
    FROM vendor_config WHERE id = v_primary_id;
    RAISE EXCEPTION '同接口同厂商唯一索引未拒绝重复绑定';
  EXCEPTION WHEN unique_violation THEN
    duplicate_rejected := TRUE;
  END;

  BEGIN
    UPDATE vendor_config SET deleted = TRUE WHERE vendor_config.id = v_fallback_id;
    RAISE EXCEPTION '路由引用删除保护未拒绝删除';
  EXCEPTION WHEN raise_exception THEN
    delete_rejected := SQLERRM LIKE 'V048 vendor_config %';
  END;

  IF v_other_config_id IS NULL OR NOT duplicate_rejected OR NOT delete_rejected THEN
    RAISE EXCEPTION 'V048 主备、不同厂商绑定或删除保护验证失败';
  END IF;
END \$\$;
SQL
pass "fresh 安装、V048 schema、同接口不同厂商绑定、主备 CHECK 和删除保护: $FRESH_DB"

make_v047_equivalent "$UPGRADE_TEMPLATE_DB"
seed_valid_legacy_route "$UPGRADE_TEMPLATE_DB" upgrade
clone_database "$UPGRADE_TEMPLATE_DB" "$UPGRADE_DB"
run_migration "$UPGRADE_DB" update
assert_v048_schema "$UPGRADE_DB"
if [[ "$(psql_db "$UPGRADE_DB" -Atq -c "
  SELECT count(*) = 1
     AND (SELECT count(*) FROM databasechangelog
          WHERE id = 'enforce-interface-vendor-routing-2026-08-11') = 1
     AND (SELECT ai.primary_vendor_config_id = primary_config.id
          FROM api_interface ai
          JOIN vendor_info primary_vendor ON primary_vendor.id = ai.vendor_id
          JOIN vendor_config primary_config
            ON primary_config.interface_id = ai.id
           AND primary_config.vendor_id = primary_vendor.id
          WHERE ai.interface_code = 'v048_${RUN_ID}_upgrade')
     AND (SELECT ai.fallback_vendor_config_id = fallback_config.id
          FROM api_interface ai
          JOIN vendor_config primary_config ON primary_config.id = ai.primary_vendor_config_id
          JOIN vendor_config fallback_config
            ON fallback_config.interface_id = ai.id
           AND fallback_config.vendor_id = primary_config.fallback_vendor_id
          WHERE ai.interface_code = 'v048_${RUN_ID}_upgrade')
  FROM api_interface
  WHERE interface_code = 'v048_${RUN_ID}_upgrade'")" != "t" ]]; then
  fail "存量升级主/备用 ID 回填不正确: $UPGRADE_DB"
fi
psql_db "$UPGRADE_DB" -c "
INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
    circuit_threshold, circuit_timeout, status, runtime_mode, connector_version, deleted
)
SELECT other_vendor.id, primary_config.data_type_id, primary_config.data_type_code,
       primary_config.interface_id, 10000, 0, 10, 30000, 'inactive', 'PLUGIN', 0, FALSE
FROM vendor_info other_vendor
JOIN vendor_config primary_config ON primary_config.id = (
  SELECT primary_vendor_config_id FROM api_interface
  WHERE interface_code = 'v048_${RUN_ID}_upgrade'
)
WHERE other_vendor.vendor_code = 'v048_${RUN_ID}_upgrade_other';" >/dev/null
pass "V047 等价存量升级、主备回填、旧唯一约束替换、同接口不同厂商绑定: $UPGRADE_DB"

snapshot_routing_state() {
  local db_name="$1"
  psql_db "$db_name" -Atq -c "
    SELECT md5(
      COALESCE((SELECT string_agg(
          id || ':' || author || ':' || exectype,
          '|' ORDER BY orderexecuted)
        FROM databasechangelog), '')
      || '|' || COALESCE((SELECT string_agg(
          id || ':' || COALESCE(primary_vendor_config_id::TEXT, '') || ':' ||
          COALESCE(fallback_vendor_config_id::TEXT, ''),
          '|' ORDER BY id)
        FROM api_interface), '')
      || '|' || COALESCE((SELECT string_agg(
          id || ':' || interface_id || ':' || vendor_id || ':' || COALESCE(deleted::TEXT, ''),
          '|' ORDER BY id)
        FROM vendor_config), '')
      || '|' || (SELECT count(*)::TEXT FROM pg_class
                 WHERE relnamespace = 'public'::regnamespace))"
}

psql_db "$FRESH_DB" -c "CREATE TABLE IF NOT EXISTS v048_repeat_probe(id INTEGER)" >/dev/null
FRESH_SNAPSHOT="$(snapshot_routing_state "$FRESH_DB")"
run_migration "$FRESH_DB" update
FRESH_SNAPSHOT_AFTER="$(snapshot_routing_state "$FRESH_DB")"
if [[ "$FRESH_SNAPSHOT" != "$FRESH_SNAPSHOT_AFTER" ]]; then
  fail "重复 update 改变了隔离库数据或结构: $FRESH_DB"
fi
if [[ "$(psql_db "$FRESH_DB" -Atq -c "SELECT count(*) FROM databasechangelog WHERE id = 'enforce-interface-vendor-routing-2026-08-11'")" != "1" ]]; then
  fail "重复 update 产生重复 V048 changeset: $FRESH_DB"
fi
pass "重复 update 无新增 changeset、无重复对象、数据快照不变: $FRESH_DB"

make_v047_equivalent "$DUPLICATE_DB"
psql_db "$DUPLICATE_DB" <<SQL
INSERT INTO data_type (data_type_code, data_type_name, data_category, status, deleted)
VALUES ('v048_${RUN_ID}_duplicate_type_1', 'V048 duplicate 1', 'regression', 'active', FALSE),
       ('v048_${RUN_ID}_duplicate_type_2', 'V048 duplicate 2', 'regression', 'active', FALSE);
INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, deleted)
VALUES ('v048_${RUN_ID}_duplicate_vendor', 'V048 duplicate vendor', 'regression', 'active', FALSE);
INSERT INTO api_interface (interface_code, interface_name, data_type_id, vendor_id, status, deleted)
SELECT 'v048_${RUN_ID}_duplicate', 'V048 duplicate interface', type_1.id, vendor.id, 'inactive', FALSE
FROM data_type type_1
JOIN vendor_info vendor ON vendor.vendor_code = 'v048_${RUN_ID}_duplicate_vendor'
WHERE type_1.data_type_code = 'v048_${RUN_ID}_duplicate_type_1';
INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
    circuit_threshold, circuit_timeout, status, runtime_mode, connector_version, deleted
)
SELECT vendor.id, type.id, type.data_type_code, interface.id, 10000, 0, 10, 30000,
       'inactive', 'PLUGIN', 0, FALSE
FROM vendor_info vendor
JOIN api_interface interface ON interface.interface_code = 'v048_${RUN_ID}_duplicate'
JOIN data_type type ON type.data_type_code IN (
  'v048_${RUN_ID}_duplicate_type_1', 'v048_${RUN_ID}_duplicate_type_2'
)
WHERE vendor.vendor_code = 'v048_${RUN_ID}_duplicate_vendor';
SQL
expect_migration_failure "$DUPLICATE_DB" "V048 blocked: duplicate active interface/vendor bindings"
if [[ "$(psql_db "$DUPLICATE_DB" -Atq -c "
  SELECT to_regclass('public.api_interface') IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                     WHERE table_name = 'api_interface'
                       AND column_name = 'primary_vendor_config_id')
     AND to_regclass('public.ux_vendor_config_interface_vendor_active_v048') IS NULL
     AND NOT EXISTS (SELECT 1 FROM databasechangelog
                     WHERE id = 'enforce-interface-vendor-routing-2026-08-11')")" != "t" ]]; then
  fail "重复绑定失败后留下 V048 半迁移对象: $DUPLICATE_DB"
fi
pass "重复同接口厂商绑定 HALT 且事务回滚: $DUPLICATE_DB"

make_v047_equivalent "$AMBIGUOUS_DB"
psql_db "$AMBIGUOUS_DB" <<SQL
INSERT INTO data_type (data_type_code, data_type_name, data_category, status, deleted)
VALUES ('v048_${RUN_ID}_ambiguous_type', 'V048 ambiguous type', 'regression', 'active', FALSE);
INSERT INTO vendor_info (vendor_code, vendor_name, vendor_type, status, deleted)
VALUES ('v048_${RUN_ID}_ambiguous_primary', 'V048 ambiguous primary', 'regression', 'active', FALSE),
       ('v048_${RUN_ID}_ambiguous_fallback', 'V048 ambiguous fallback', 'regression', 'active', FALSE);
INSERT INTO api_interface (interface_code, interface_name, data_type_id, vendor_id, status, deleted)
SELECT 'v048_${RUN_ID}_ambiguous', 'V048 ambiguous interface', type.id, primary_vendor.id, 'inactive', FALSE
FROM data_type type
JOIN vendor_info primary_vendor ON primary_vendor.vendor_code = 'v048_${RUN_ID}_ambiguous_primary'
WHERE type.data_type_code = 'v048_${RUN_ID}_ambiguous_type';
INSERT INTO vendor_config (
    vendor_id, data_type_id, data_type_code, interface_id, timeout, retry_count,
    circuit_threshold, circuit_timeout, status, runtime_mode, connector_version, deleted
)
SELECT fallback_vendor.id, type.id, type.data_type_code, interface.id, 10000, 0, 10, 30000,
       'inactive', 'PLUGIN', 0, FALSE
FROM vendor_info fallback_vendor
JOIN data_type type ON type.data_type_code = 'v048_${RUN_ID}_ambiguous_type'
JOIN api_interface interface ON interface.interface_code = 'v048_${RUN_ID}_ambiguous'
WHERE fallback_vendor.vendor_code = 'v048_${RUN_ID}_ambiguous_fallback';
UPDATE api_interface
SET vendor_id = (SELECT id FROM vendor_info WHERE vendor_code = 'v048_${RUN_ID}_ambiguous_primary')
WHERE interface_code = 'v048_${RUN_ID}_ambiguous';
SQL
expect_migration_failure "$AMBIGUOUS_DB" "V048 blocked: legacy interface vendor does not resolve uniquely"
if [[ "$(psql_db "$AMBIGUOUS_DB" -Atq -c "
  SELECT NOT EXISTS (SELECT 1 FROM information_schema.columns
                     WHERE table_name = 'api_interface'
                       AND column_name = 'primary_vendor_config_id')
     AND to_regclass('public.ux_vendor_config_interface_vendor_active_v048') IS NULL
     AND NOT EXISTS (SELECT 1 FROM databasechangelog
                     WHERE id = 'enforce-interface-vendor-routing-2026-08-11')")" != "t" ]]; then
  fail "legacy 主厂商无法唯一解析失败后留下半迁移对象: $AMBIGUOUS_DB"
fi
pass "legacy 主厂商无法唯一解析 HALT 且事务回滚: $AMBIGUOUS_DB"

make_v047_equivalent "$ROLLBACK_DB"
run_migration "$ROLLBACK_DB" update
ROLLBACK_SNAPSHOT="$(snapshot_routing_state "$ROLLBACK_DB")"
if DB_NAME="$ROLLBACK_DB" MIGRATION_CONFIRM_ROLLBACK="$ROLLBACK_DB" \
    bash ./migrate-db.sh rollback-count 1 >"$LOG_DIR/${ROLLBACK_DB}_rollback.log" 2>&1; then
  fail "V048 rollback 不应允许原地回滚: $ROLLBACK_DB"
fi
ROLLBACK_SNAPSHOT_AFTER="$(snapshot_routing_state "$ROLLBACK_DB")"
if [[ "$ROLLBACK_SNAPSHOT" != "$ROLLBACK_SNAPSHOT_AFTER" ]]; then
  fail "V048 拒绝 rollback 后数据库状态发生变化: $ROLLBACK_DB"
fi
if [[ "$(psql_db "$ROLLBACK_DB" -Atq -c "
  SELECT (SELECT count(*) FROM databasechangelog
          WHERE id = 'enforce-interface-vendor-routing-2026-08-11'
            AND author = 'data-platform') = 1
     AND to_regclass('public.ux_vendor_config_interface_vendor_active_v048') IS NOT NULL")" != "t" ]]; then
  fail "V048 changeset 或唯一索引状态不正确: $ROLLBACK_DB"
fi
pass "V048 rollback 按预期拒绝原地回滚并保持数据库不变: $ROLLBACK_DB"

echo "V048 隔离数据库迁移验证完成。数据库将在退出时清理。"
