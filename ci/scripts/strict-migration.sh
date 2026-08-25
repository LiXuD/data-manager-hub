#!/usr/bin/env bash
set -euo pipefail

# Production-safe Liquibase wrapper. It intentionally does not source or call
# migrate-db.sh update because that command may repair inconsistent history.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:?DB_HOST is required}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:?DB_NAME is required}"
DB_USERNAME="${DB_USERNAME:?DB_USERNAME is required}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD is required}"
DB_CONNECT_ATTEMPTS="${DB_CONNECT_ATTEMPTS:-3}"
MAVEN_BIN="${MAVEN_BIN:-mvn}"
MAVEN_OFFLINE="${MAVEN_OFFLINE:-false}"
[[ "$DB_CONNECT_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || { echo "DB_CONNECT_ATTEMPTS must be a positive integer" >&2; exit 64; }
case "$MAVEN_OFFLINE" in
  true|false) ;;
  *) echo "MAVEN_OFFLINE must be true or false" >&2; exit 64 ;;
esac
export DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD

usage() {
  cat <<'EOF'
Usage: ci/scripts/strict-migration.sh <preflight|update|status|update-sql>

The strict commands are fail-closed and never repair history, baseline, clear
checksums, release locks, rollback, drop, recreate, or restore a database.
EOF
}

run_liquibase() {
  # The dbops image sets MAVEN_OFFLINE=true after prefetching its complete
  # Maven/Liquibase graph.  Hosted CI migration jobs keep the default false
  # so a cold, isolated Maven cache can resolve dependencies normally.
  maven_args=(-N -DskipTests)
  [[ "$MAVEN_OFFLINE" == true ]] && maven_args=(-o "${maven_args[@]}")
  "$MAVEN_BIN" "${maven_args[@]}" "$@"
}

require_psql() {
  command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 20; }
}

query_scalar() {
  local sql="$1"
  PGPASSWORD="$DB_PASSWORD" psql -X -v ON_ERROR_STOP=1 -Atq \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -c "$sql"
}

assert_database_state() {
  # This is intentionally read-only.  A database with application tables but
  # no Liquibase history was migrated outside the release contract and must
  # be baselined by the DBA; strict CD never guesses or repairs it.
  local has_application_schema has_history lock_state
  # A single sentinel table is not sufficient: a partially provisioned or
  # vendor-extended database may contain a different application table while
  # tenant_info is absent.  Reject every non-Liquibase table in public until
  # an audited baseline has installed DATABASECHANGELOG.
  has_application_schema="$(query_scalar "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name NOT IN ('databasechangelog', 'databasechangeloglock'))::text")"
  has_history="$(query_scalar "SELECT (to_regclass('public.databasechangelog') IS NOT NULL)::text")"
  if [[ "$has_application_schema" == true && "$has_history" != true ]]; then
    echo 'strict migration refused: application schema exists without DATABASECHANGELOG; perform an audited baseline first' >&2
    return 2
  fi
  if [[ "$has_history" == true ]]; then
    lock_state="$(query_scalar "SELECT CASE WHEN to_regclass('public.databasechangeloglock') IS NULL THEN 'missing' WHEN EXISTS (SELECT 1 FROM public.databasechangeloglock WHERE locked) THEN 'locked' ELSE 'unlocked' END")"
    case "$lock_state" in
      unlocked) ;;
      locked)
        echo 'strict migration refused: DATABASECHANGELOGLOCK is held; do not clear it automatically' >&2
        return 2
        ;;
      *)
        echo "strict migration refused: DATABASECHANGELOGLOCK state is $lock_state; DBA must inspect it" >&2
        return 2
        ;;
    esac
  fi
}

wait_for_database() {
  local attempt=1
  while [[ "$attempt" -le "$DB_CONNECT_ATTEMPTS" ]]; do
    if PGPASSWORD="$DB_PASSWORD" psql -X -v ON_ERROR_STOP=1 -Atq \
        -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
        -c "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi
    if [[ "$attempt" -lt "$DB_CONNECT_ATTEMPTS" ]]; then
      sleep_seconds=$((2 ** (attempt - 1)))
      echo "database connection attempt $attempt/$DB_CONNECT_ATTEMPTS failed; retrying in ${sleep_seconds}s" >&2
      sleep "$sleep_seconds"
    fi
    attempt=$((attempt + 1))
  done
  echo "database connection failed after $DB_CONNECT_ATTEMPTS attempts" >&2
  return 1
}

preflight() {
  "$ROOT_DIR/migrate-db.sh" check-numbering
  require_psql
  wait_for_database
  assert_database_state
  run_liquibase liquibase:validate
  run_liquibase -Dliquibase.verbose=true liquibase:status
}

case "${1:-}" in
  preflight) preflight ;;
  update)
    preflight
    run_liquibase liquibase:update
    run_liquibase -Dliquibase.verbose=true liquibase:status
    ;;
  status) run_liquibase -Dliquibase.verbose=true liquibase:status ;;
  update-sql)
    preflight
    run_liquibase liquibase:updateSQL
    ;;
  -h|--help|help) usage ;;
  *) usage >&2; exit 64 ;;
esac
