#!/usr/bin/env bash

set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-dataplatform_bootstrap_regression}"
DB_PASSWORD="${DB_PASSWORD:-${PGPASSWORD:-postgres}}"
export PGPASSWORD="$DB_PASSWORD"
DRY_RUN_FILE="$(mktemp)"
BACKUP_FILE="$(mktemp).sql"
BASELINE_BACKUP_DIR="$(mktemp -d)"

if [[ ! "$VERIFY_DB_NAME" =~ ^dataplatform_[a-z0-9_]*_regression$ ]]; then
  echo "VERIFY_DB_NAME 必须匹配 dataplatform_*_regression，避免误删业务数据库" >&2
  exit 2
fi

PSQL=(psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME")

cleanup() {
  (
    set +e
    "${PSQL[@]}" -d postgres -c "DROP DATABASE IF EXISTS \"$VERIFY_DB_NAME\" WITH (FORCE)" >/dev/null
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

bash ./migrate-db.sh update

bash ./migrate-db.sh backup "$BACKUP_FILE"
"${PSQL[@]}" -d "$VERIFY_DB_NAME" -c 'CREATE TABLE migration_restore_probe(id INTEGER)' >/dev/null
MIGRATION_CONFIRM_RESTORE="$VERIFY_DB_NAME" bash ./migrate-db.sh restore "$BACKUP_FILE"

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c "SELECT to_regclass('migration_restore_probe') IS NULL")" != "t" ]]; then
  echo "备份恢复后仍存在备份之后创建的探针表" >&2
  exit 1
fi
bash ./migrate-db.sh update

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c 'SELECT count(*) FROM databasechangelog')" != "4" ]]; then
  echo "Liquibase 基线、清理、Flowable 与接口权限审批变更记录不完整" >&2
  exit 1
fi

bash ./migrate-db.sh rollback-dry-run 1 >"$DRY_RUN_FILE"
grep -q "api_permission_application" "$DRY_RUN_FILE"
MIGRATION_CONFIRM_ROLLBACK="$VERIFY_DB_NAME" bash ./migrate-db.sh rollback-count 1

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c "
    SELECT to_regclass('api_permission_application') IS NULL
       AND to_regclass('workflow.act_ru_execution') IS NOT NULL")" != "t" ]]; then
  echo "V026 回滚后审批业务表仍存在，或错误删除了 Flowable 表" >&2
  exit 1
fi

bash ./migrate-db.sh update

bash ./migrate-db.sh rollback-dry-run 3 >"$DRY_RUN_FILE"
grep -q "sign_type" "$DRY_RUN_FILE"
MIGRATION_CONFIRM_ROLLBACK="$VERIFY_DB_NAME" bash ./migrate-db.sh rollback-count 3

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c "
    SELECT count(*) = 3
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND (
        (table_name = 'vendor_config' AND column_name IN ('sign_type', 'encrypt_type'))
        OR (table_name = 'interface_param' AND column_name = 'validation_rule')
      )")" != "t" ]]; then
  echo "V025 回滚后兼容列没有完整恢复" >&2
  exit 1
fi

bash ./migrate-db.sh update

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c "
    SELECT count(*) = 0
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND (
        (table_name = 'vendor_config' AND column_name IN ('sign_type', 'encrypt_type'))
        OR (table_name = 'interface_param' AND column_name = 'validation_rule')
      )")" != "t" ]]; then
  echo "V025 重放后仍存在已废弃兼容列" >&2
  exit 1
fi

bash ./migrate-db.sh rollback-dry-run 4 >"$DRY_RUN_FILE"
grep -q "DROP TABLE" "$DRY_RUN_FILE"
MIGRATION_CONFIRM_ROLLBACK="$VERIFY_DB_NAME" bash ./migrate-db.sh rollback-count 4

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c "
    SELECT to_regclass('tenant_info') IS NULL
       AND to_regclass('workflow.act_ru_execution') IS NULL")" != "t" ]]; then
  echo "Liquibase 全量回滚后仍存在业务表" >&2
  exit 1
fi

bash ./migrate-db.sh update

"${PSQL[@]}" -d "$VERIFY_DB_NAME" <<'SQL'
DO $$
BEGIN
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
END $$;
SQL

"${PSQL[@]}" -d "$VERIFY_DB_NAME" \
  -c 'DROP TABLE databasechangeloglock, databasechangelog' >/dev/null
DB_BACKUP_DIR="$BASELINE_BACKUP_DIR" MIGRATION_CONFIRM_BASELINE="$VERIFY_DB_NAME" \
  bash ./migrate-db.sh baseline

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c 'SELECT count(*) FROM databasechangelog')" != "4" ]]; then
  echo "现有数据库基线登记失败" >&2
  exit 1
fi

echo "数据库迁移回归通过（dry-run/update/idempotency/V026+Flowable/V025 rollback/reapply/full rollback/backup/restore/baseline）: $VERIFY_DB_NAME"
