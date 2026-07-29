#!/usr/bin/env bash

set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-dataplatform_bootstrap_regression}"
LEGACY_VERIFY_DB_NAME="${LEGACY_VERIFY_DB_NAME:-dataplatform_legacy_baseline_regression}"
DB_PASSWORD="${DB_PASSWORD:-${PGPASSWORD:-postgres}}"
export PGPASSWORD="$DB_PASSWORD"
DRY_RUN_FILE="$(mktemp)"
BACKUP_FILE="$(mktemp).sql"
BASELINE_BACKUP_DIR="$(mktemp -d)"

if [[ ! "$VERIFY_DB_NAME" =~ ^dataplatform_[a-z0-9_]*_regression$ ]] \
    || [[ ! "$LEGACY_VERIFY_DB_NAME" =~ ^dataplatform_[a-z0-9_]*_regression$ ]]; then
  echo "回归数据库名必须匹配 dataplatform_*_regression，避免误删业务数据库" >&2
  exit 2
fi

PSQL=(psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME")

cleanup() {
  (
    set +e
    "${PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$VERIFY_DB_NAME\" WITH (FORCE)" >/dev/null
    "${PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$LEGACY_VERIFY_DB_NAME\" WITH (FORCE)" >/dev/null
  )
  rm -f "$DRY_RUN_FILE" "$BACKUP_FILE"
  rm -rf "$BASELINE_BACKUP_DIR"
}
trap cleanup EXIT

cleanup
"${PSQL[@]}" -d postgres -c "CREATE DATABASE \"$VERIFY_DB_NAME\"" >/dev/null

export DB_HOST DB_PORT DB_USERNAME
export DB_NAME="$VERIFY_DB_NAME"
export DB_PASSWORD

bash ./migrate-db.sh dry-run >"$DRY_RUN_FILE"
grep -q "CREATE TABLE" "$DRY_RUN_FILE"
grep -q "ACT_RU_EXECUTION" "$DRY_RUN_FILE"
grep -q "api_permission_application" "$DRY_RUN_FILE"
grep -q "system:admin" "$DRY_RUN_FILE"

bash ./migrate-db.sh update

bash ./migrate-db.sh backup "$BACKUP_FILE"
"${PSQL[@]}" -d "$VERIFY_DB_NAME" -c 'CREATE TABLE migration_restore_probe(id INTEGER)' >/dev/null
MIGRATION_CONFIRM_RESTORE="$VERIFY_DB_NAME" bash ./migrate-db.sh restore "$BACKUP_FILE"

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c "SELECT to_regclass('migration_restore_probe') IS NULL")" != "t" ]]; then
  echo "备份恢复后仍存在备份之后创建的探针表" >&2
  exit 1
fi
bash ./migrate-db.sh update

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c 'SELECT count(*) FROM databasechangelog')" != "14" ]]; then
  echo "Liquibase 基线、运行时结构修复、Flowable、接口权限审批与 RBAC 安全变更记录不完整" >&2
  exit 1
fi

bash ./migrate-db.sh rollback-dry-run 3 >"$DRY_RUN_FILE"
grep -q "禁止原地回滚角色合并" "$DRY_RUN_FILE"
if MIGRATION_CONFIRM_ROLLBACK="$VERIFY_DB_NAME" \
    bash ./migrate-db.sh rollback-count 3 >/dev/null 2>&1; then
  echo "V027 前向安全迁移不应允许原地回滚" >&2
  exit 1
fi

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c "
    SELECT count(*) = 14
       AND to_regclass('api_permission_application') IS NOT NULL
       AND to_regclass('workflow.act_ru_execution') IS NOT NULL
       AND to_regclass('tenant_budget') IS NOT NULL
    FROM databasechangelog")" != "t" ]]; then
  echo "拒绝 V027 回滚后数据库状态发生变化" >&2
  exit 1
fi

"${PSQL[@]}" -d "$VERIFY_DB_NAME" <<'SQL'
DO $$
BEGIN
  PERFORM create_monthly_partition((CURRENT_DATE + INTERVAL '2 months')::DATE);

  IF to_regclass('billing_rule') IS NOT NULL
      OR to_regclass('billing_rule_tier') IS NOT NULL
      OR to_regclass('monthly_billing_tier_usage') IS NOT NULL THEN
    RAISE EXCEPTION '检测到已退役的计费规则表';
  END IF;

  IF to_regclass('billing_template') IS NULL
      OR to_regclass('billing_plan') IS NULL
      OR to_regclass('billing_event') IS NULL
      OR to_regclass('billing_usage_balance') IS NULL THEN
    RAISE EXCEPTION '新版计费核心表不完整';
  END IF;

  IF to_regclass('tenant_budget') IS NULL
      OR to_regclass('encrypted_field') IS NULL
      OR to_regclass('masking_rule') IS NULL
      OR to_regclass('config_version') IS NULL
      OR to_regclass('vendor_params_mapping') IS NULL THEN
    RAISE EXCEPTION '运行时实体基础表不完整';
  END IF;

  IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'user_info'
        AND column_name = 'last_login_time'
  ) OR NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'operation_log'
        AND column_name = 'operation_module'
  ) THEN
    RAISE EXCEPTION '登录或操作日志运行时字段不完整';
  END IF;

  IF (SELECT count(*) FROM billing_template) <> 6 THEN
    RAISE EXCEPTION '计费模板数量不是 6';
  END IF;

  IF (SELECT count(*) FROM billing_plan
      WHERE plan_code = 'UAPI-PROGRAMMER-HISTORY-TODAY' AND version = 1) <> 1 THEN
    RAISE EXCEPTION 'UAPI 零元计费方案没有且仅有一条';
  END IF;

  IF (SELECT count(*) FROM permission
      WHERE permission_code IN (
        'billing:view', 'billing:manage', 'billing:reverse',
        'billing:reconcile', 'billing:view-all'
      )) <> 5 THEN
    RAISE EXCEPTION '计费权限集合不完整';
  END IF;

  IF EXISTS (SELECT 1 FROM role_info WHERE role_code <> LOWER(role_code)) THEN
    RAISE EXCEPTION '仍存在非规范化角色编码';
  END IF;

  IF (SELECT count(*) FROM permission
      WHERE permission_code = 'system:admin'
        AND status = 'active'
        AND deleted = FALSE) <> 1 THEN
    RAISE EXCEPTION '平台安全管理权限不完整';
  END IF;

  IF (SELECT count(*)
      FROM role_info role
      JOIN role_permission relation ON relation.role_id = role.id
      JOIN permission permission ON permission.id = relation.permission_id
      WHERE role.role_code = 'admin'
        AND permission.permission_code = 'call-scene:view'
        AND permission.status = 'active'
        AND permission.deleted = FALSE) <> 1 THEN
    RAISE EXCEPTION '管理员场景管理权限不完整';
  END IF;

  IF EXISTS (
      SELECT 1
      FROM pg_class relation
      JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
      WHERE relation.relkind IN ('r', 'p')
        AND namespace.nspname IN ('public', 'workflow')
        AND obj_description(relation.oid, 'pg_class') IS NULL
  ) THEN
    RAISE EXCEPTION '仍有 public 或 workflow 数据表缺少 COMMENT';
  END IF;

  IF (SELECT count(*)
      FROM role_info role
      JOIN role_permission relation ON relation.role_id = role.id
      JOIN permission permission ON permission.id = relation.permission_id
      WHERE role.role_code = 'tenant_admin'
        AND permission.permission_code LIKE 'api-permission:%') <> 7 THEN
    RAISE EXCEPTION '租户管理员审批权限矩阵不正确';
  END IF;

  IF EXISTS (
      SELECT 1
      FROM role_info role
      JOIN role_permission relation ON relation.role_id = role.id
      JOIN permission permission ON permission.id = relation.permission_id
      WHERE role.role_code = 'tenant_admin'
        AND permission.permission_code = 'api-permission:emergency-grant'
  ) THEN
    RAISE EXCEPTION '租户管理员不应拥有紧急授权';
  END IF;
END $$;
SQL

"${PSQL[@]}" -d "$VERIFY_DB_NAME" \
  -c 'DROP TABLE databasechangeloglock, databasechangelog' >/dev/null
DB_BACKUP_DIR="$BASELINE_BACKUP_DIR" MIGRATION_CONFIRM_BASELINE="$VERIFY_DB_NAME" \
  bash ./migrate-db.sh baseline

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c 'SELECT count(*) FROM databasechangelog')" != "14" ]]; then
  echo "现有数据库基线登记失败" >&2
  exit 1
fi

"${PSQL[@]}" -d postgres -c "CREATE DATABASE \"$LEGACY_VERIFY_DB_NAME\"" >/dev/null
"${PSQL[@]}" -d "$LEGACY_VERIFY_DB_NAME" -f sql/init.sql >/dev/null
"${PSQL[@]}" -d "$LEGACY_VERIFY_DB_NAME" -c '
  ALTER TABLE quality_score
    DROP COLUMN rule_id CASCADE,
    DROP COLUMN score_date CASCADE,
    DROP COLUMN score_value CASCADE;
  ALTER TABLE quality_rule
    DROP COLUMN rule_code CASCADE,
    DROP COLUMN target_table CASCADE;
  ALTER TABLE api_key RENAME COLUMN api_key TO key_value;
  ALTER TABLE api_key RENAME COLUMN expire_time TO expires_at;
  ALTER TABLE api_key
    DROP COLUMN api_secret,
    DROP COLUMN rate_limit,
    DROP COLUMN quota_limit,
    DROP COLUMN quota_used;
' >/dev/null
export DB_NAME="$LEGACY_VERIFY_DB_NAME"
DB_BACKUP_DIR="$BASELINE_BACKUP_DIR" MIGRATION_CONFIRM_BASELINE="$LEGACY_VERIFY_DB_NAME" \
  bash ./migrate-db.sh baseline

if [[ "$("${PSQL[@]}" -Atq -d "$LEGACY_VERIFY_DB_NAME" -c "
    SELECT count(*) = 14
       AND to_regclass('interface_param') IS NOT NULL
       AND to_regclass('workflow.act_ge_property') IS NOT NULL
       AND to_regclass('workflow.act_ru_execution') IS NOT NULL
       AND to_regclass('tenant_budget') IS NOT NULL
       AND EXISTS (
         SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'user_info'
           AND column_name = 'last_login_time'
       )
       AND EXISTS (
         SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'operation_log'
           AND column_name = 'operation_module'
       )
       AND (
         SELECT character_maximum_length
         FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'operation_log'
           AND column_name = 'method'
       ) = 200
       AND NOT EXISTS (
         SELECT 1
         FROM information_schema.columns
         WHERE table_schema = 'public'
           AND ((table_name = 'interface_param' AND column_name = 'validation_rule')
             OR (table_name = 'vendor_config' AND column_name IN ('sign_type', 'encrypt_type')))
       )
       AND (
         SELECT count(*)
         FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'api_key'
           AND column_name IN (
             'api_key', 'api_secret', 'rate_limit_enabled', 'rate_limit',
             'quota_limit', 'quota_used', 'expire_time'
           )
       ) = 7
       AND NOT EXISTS (
         SELECT 1
         FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'api_key'
           AND column_name IN ('key_value', 'expires_at')
       )
       AND (
         SELECT character_maximum_length = 64 AND is_nullable = 'NO'
         FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'api_key'
           AND column_name = 'api_key'
       )
       AND EXISTS (
         SELECT 1
         FROM pg_indexes
         WHERE schemaname = 'public'
           AND tablename = 'api_key'
           AND indexname = 'idx_api_key'
           AND indexdef LIKE '%USING btree (api_key)%'
       )
    FROM databasechangelog")" != "t" ]]; then
  echo "缺少 interface_param 的旧库基线接管失败" >&2
  exit 1
fi

"${PSQL[@]}" -d "$LEGACY_VERIFY_DB_NAME" <<'SQL'
INSERT INTO user_info (username, password, status, deleted)
VALUES ('admin', 'bootstrap-admin-probe', 'active', FALSE);

INSERT INTO caller_info (caller_code, caller_name, caller_type, status, deleted)
VALUES ('runtime-contract-probe', 'Runtime Contract Probe', 'system', 'active', FALSE);

INSERT INTO api_key (
  caller_id, key_name, api_key, api_secret, rate_limit_enabled,
  rate_limit, quota_limit, quota_used, status, expire_time, deleted
)
SELECT id, 'runtime-contract-probe', 'dk_runtime_contract_probe',
       'runtime-contract-secret', TRUE, 100, 100000, 0, 'active', NULL, FALSE
FROM caller_info
WHERE caller_code = 'runtime-contract-probe';

UPDATE api_key
SET rate_limit = 200,
    status = 'disabled'
WHERE api_key = 'dk_runtime_contract_probe';

DO $$
BEGIN
  IF (SELECT count(*) FROM api_key
      WHERE api_key = 'dk_runtime_contract_probe'
        AND rate_limit = 200
        AND status = 'disabled') <> 1 THEN
    RAISE EXCEPTION 'API Key 当前运行时写入契约验证失败';
  END IF;
END $$;

DELETE FROM api_key WHERE api_key = 'dk_runtime_contract_probe';
DELETE FROM caller_info WHERE caller_code = 'runtime-contract-probe';

\i sql/migrations/V032__assign_bootstrap_admin_role.sql
\i sql/migrations/V032__assign_bootstrap_admin_role.sql

DO $$
BEGIN
  IF (
    SELECT count(*)
    FROM user_role relation
    JOIN user_info user_info ON user_info.id = relation.user_id
    JOIN role_info role_info ON role_info.id = relation.role_id
    WHERE user_info.username = 'admin'
      AND role_info.role_code = 'admin'
      AND relation.deleted = FALSE
  ) <> 1 THEN
    RAISE EXCEPTION 'bootstrap admin 未被幂等绑定到 admin 角色';
  END IF;
END $$;
SQL

echo "数据库迁移回归通过（dry-run/update/idempotency/V026+V027+V030+V031+V032+V033+V034+V035+V036+Flowable/forward-recovery/backup/restore/baseline/legacy-baseline）: $VERIFY_DB_NAME"
