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

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c 'SELECT count(*) FROM databasechangelog')" != "5" ]]; then
  echo "Liquibase 基线、清理、Flowable、接口权限审批与 RBAC 安全变更记录不完整" >&2
  exit 1
fi

bash ./migrate-db.sh rollback-dry-run 1 >"$DRY_RUN_FILE"
grep -q "禁止原地回滚角色合并" "$DRY_RUN_FILE"
if MIGRATION_CONFIRM_ROLLBACK="$VERIFY_DB_NAME" \
    bash ./migrate-db.sh rollback-count 1 >/dev/null 2>&1; then
  echo "V027 前向安全迁移不应允许原地回滚" >&2
  exit 1
fi

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c "
    SELECT count(*) = 5
       AND to_regclass('api_permission_application') IS NOT NULL
       AND to_regclass('workflow.act_ru_execution') IS NOT NULL
    FROM databasechangelog")" != "t" ]]; then
  echo "拒绝 V027 回滚后数据库状态发生变化" >&2
  exit 1
fi

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

if [[ "$("${PSQL[@]}" -Atq -d "$VERIFY_DB_NAME" -c 'SELECT count(*) FROM databasechangelog')" != "5" ]]; then
  echo "现有数据库基线登记失败" >&2
  exit 1
fi

echo "数据库迁移回归通过（dry-run/update/idempotency/V026+V027+Flowable/forward-recovery/backup/restore/baseline）: $VERIFY_DB_NAME"
