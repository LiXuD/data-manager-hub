#!/usr/bin/env bash

set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-dataplatform_bootstrap_regression}"

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
}
trap cleanup EXIT

cleanup
"${PSQL[@]}" -d postgres -c "CREATE DATABASE \"$VERIFY_DB_NAME\"" >/dev/null
"${PSQL[@]}" -d "$VERIFY_DB_NAME" -f sql/init.sql >/dev/null

for migration in sql/migrations/*.sql; do
  "${PSQL[@]}" -d "$VERIFY_DB_NAME" -f "$migration" >/dev/null
done

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

echo "数据库全新初始化回归通过: $VERIFY_DB_NAME"
