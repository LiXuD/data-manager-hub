#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-dataplatform}"
export DB_USERNAME="${DB_USERNAME:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-postgres}"
export PGPASSWORD="$DB_PASSWORD"

MAVEN_BIN="${MAVEN_BIN:-mvn}"
BACKUP_DIR="${DB_BACKUP_DIR:-$SCRIPT_DIR/.runtime/db-backups}"
LEGACY_MIGRATIONS=(
    sql/rollbacks/U025__restore_obsolete_compatibility_fields.sql
    sql/migrations/V001__add_data_type_code.sql
    sql/migrations/V002__create_api_interface.sql
    sql/migrations/V003__add_billing_and_vendor_fields.sql
    sql/migrations/V005__add_auth_config.sql
    sql/migrations/V006__add_vendor_id_to_interface.sql
    sql/migrations/V007__add_permission_tables.sql
    sql/migrations/V007__create_interface_param.sql
    sql/migrations/V008__create_current_call_record_partitions.sql
    sql/migrations/V009__seed_openapi_demo_flow.sql
    sql/migrations/V010__add_api_key_name.sql
    sql/migrations/V012__replace_placeholder_business_logic.sql
    sql/migrations/V013__add_vendor_security_pipeline.sql
    sql/migrations/V014__add_interface_contract_fields.sql
    sql/migrations/V015__add_interface_array_item_type.sql
    sql/migrations/V016__add_api_key_rate_limit_policy.sql
    sql/migrations/V017__seed_uapi_programmer_history_provider.sql
    sql/migrations/V021__create_billing_plan_and_event_ledger.sql
    sql/migrations/V022__add_billing_operation_permissions.sql
    sql/migrations/V024__seed_uapi_billing_plan.sql
    sql/migrations/V025__remove_obsolete_compatibility_fields.sql
)

usage() {
    cat <<'EOF'
用法: ./migrate-db.sh <command> [argument]

命令:
  update                    校验并应用尚未执行的变更
  dry-run                   输出待执行 SQL，不修改数据库
  status                    显示数据库迁移状态
  validate                  校验变更日志及已执行校验和
  baseline                  备份、补齐并接管手工迁移的现有数据库
  rollback-dry-run <count>  输出回滚最近 count 个变更的 SQL
  rollback-count <count>    回滚最近 count 个变更（需显式确认）
  backup [file]             创建 PostgreSQL SQL-format 备份
  restore <file>            从备份重建当前数据库（需显式确认）

破坏性操作确认:
  MIGRATION_CONFIRM_BASELINE=<DB_NAME> ./migrate-db.sh baseline
  MIGRATION_CONFIRM_ROLLBACK=<DB_NAME> ./migrate-db.sh rollback-count 1
  MIGRATION_CONFIRM_RESTORE=<DB_NAME> ./migrate-db.sh restore backup.sql
EOF
}

fail() {
    echo "错误: $*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "缺少命令: $1"
}

validate_connection_values() {
    [[ "$DB_PORT" =~ ^[0-9]+$ ]] || fail "DB_PORT 必须是数字"
    [[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail "DB_NAME 只能包含字母、数字和下划线"
    [[ "$DB_USERNAME" =~ ^[A-Za-z0-9_.-]+$ ]] || fail "DB_USERNAME 包含不支持的字符"
}

run_liquibase() {
    "$MAVEN_BIN" -N -DskipTests "$@"
}

run_and_print_sql() {
    local sql_output="$SCRIPT_DIR/target/liquibase/migrate.sql"
    rm -f "$sql_output"
    run_liquibase "$@"
    [[ -s "$sql_output" ]] || fail "Liquibase 未生成预演 SQL: $sql_output"
    cat "$sql_output"
}

query_scalar() {
    psql -X -v ON_ERROR_STOP=1 -Atq \
        -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
        -c "$1"
}

database_has_application_schema() {
    [[ "$(query_scalar "SELECT to_regclass('public.tenant_info') IS NOT NULL")" == "t" ]]
}

database_has_liquibase_history() {
    [[ "$(query_scalar "SELECT to_regclass('public.databasechangelog') IS NOT NULL")" == "t" ]]
}

reconcile_legacy_database() {
    local psql_args
    local migration
    psql_args=(
        psql -X -v ON_ERROR_STOP=1 --single-transaction
        -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME"
    )
    for migration in "${LEGACY_MIGRATIONS[@]}"; do
        psql_args+=( -f "$migration" )
    done
    "${psql_args[@]}"
}

guard_rollback() {
    local requested_count="$1"
    local applied_count
    local baseline_execution_type

    require_command psql
    applied_count="$(query_scalar 'SELECT count(*) FROM databasechangelog')"
    baseline_execution_type="$(query_scalar "SELECT exectype FROM databasechangelog WHERE id = 'baseline-2026-07-22' AND author = 'data-platform'")"
    if [[ "$baseline_execution_type" == "MARK_RAN" && "$requested_count" -ge "$applied_count" ]]; then
        fail "旧库接管基线不可用 Liquibase 删除；请使用迁移前备份执行 restore"
    fi
}

preflight_update() {
    require_command psql
    if database_has_application_schema && ! database_has_liquibase_history; then
        fail "检测到未纳入 Liquibase 的现有数据库。先备份并执行 MIGRATION_CONFIRM_BASELINE=$DB_NAME ./migrate-db.sh baseline"
    fi
}

validate_positive_count() {
    [[ "${1:-}" =~ ^[1-9][0-9]*$ ]] || fail "count 必须是正整数"
}

backup_database() {
    local output="${1:-$BACKUP_DIR/${DB_NAME}_$(date -u +%Y%m%dT%H%M%SZ).sql}"
    require_command pg_dump
    mkdir -p "$(dirname "$output")"
    pg_dump --format=plain --no-owner --no-privileges \
        -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
        -f "$output"
    echo "数据库备份完成: $output"
}

restore_database() {
    local input="${1:-}"
    [[ -n "$input" && -f "$input" ]] || fail "备份文件不存在: ${input:-<empty>}"
    [[ "${MIGRATION_CONFIRM_RESTORE:-}" == "$DB_NAME" ]] || \
        fail "恢复会重建数据库；请设置 MIGRATION_CONFIRM_RESTORE=$DB_NAME"
    require_command psql
    require_command sed

    psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
        -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid()" >/dev/null
    psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
        -c "DROP DATABASE IF EXISTS \"$DB_NAME\"" >/dev/null
    psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
        -c "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USERNAME\"" >/dev/null
    # PostgreSQL 17+ clients emit transaction_timeout even when dumping a 16
    # server. Removing only that unsupported SET keeps cross-major restores
    # deterministic while all actual schema/data errors remain fatal.
    sed '/^SET transaction_timeout = 0;$/d' "$input" | \
        psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" \
            -U "$DB_USERNAME" -d "$DB_NAME" >/dev/null
    echo "数据库恢复完成: $DB_NAME <- $input"
}

validate_connection_values
command_name="${1:-}"

case "$command_name" in
    update)
        preflight_update
        run_liquibase liquibase:validate
        run_liquibase liquibase:update
        ;;
    dry-run)
        preflight_update
        run_and_print_sql liquibase:updateSQL
        ;;
    status)
        run_liquibase -Dliquibase.verbose=true liquibase:status
        ;;
    validate)
        run_liquibase liquibase:validate
        ;;
    baseline)
        require_command psql
        [[ "${MIGRATION_CONFIRM_BASELINE:-}" == "$DB_NAME" ]] || \
            fail "基线操作需设置 MIGRATION_CONFIRM_BASELINE=$DB_NAME"
        if database_has_liquibase_history; then
            fail "DATABASECHANGELOG 已存在，不能重复执行 baseline"
        fi
        database_has_application_schema || fail "未检测到旧版基础表；全新数据库请执行 update"
        backup_database
        reconcile_legacy_database
        psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" \
            -U "$DB_USERNAME" -d "$DB_NAME" -f sql/validate-existing-baseline.sql
        run_liquibase liquibase:changelogSync
        psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" \
            -U "$DB_USERNAME" -d "$DB_NAME" \
            -c "UPDATE databasechangelog SET exectype = 'MARK_RAN' WHERE id = 'baseline-2026-07-22' AND author = 'data-platform'" >/dev/null
        ;;
    rollback-dry-run)
        validate_positive_count "${2:-}"
        run_and_print_sql -Dliquibase.rollbackCount="$2" liquibase:rollbackSQL
        ;;
    rollback-count)
        validate_positive_count "${2:-}"
        [[ "${MIGRATION_CONFIRM_ROLLBACK:-}" == "$DB_NAME" ]] || \
            fail "回滚会修改数据库；请设置 MIGRATION_CONFIRM_ROLLBACK=$DB_NAME"
        guard_rollback "$2"
        run_liquibase -Dliquibase.rollbackCount="$2" liquibase:rollback
        ;;
    backup)
        backup_database "${2:-}"
        ;;
    restore)
        restore_database "${2:-}"
        ;;
    -h|--help|help|"")
        usage
        ;;
    *)
        usage >&2
        fail "未知命令: $command_name"
        ;;
esac
