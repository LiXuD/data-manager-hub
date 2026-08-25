#!/usr/bin/env bash
set -euo pipefail

command_name="${1:-help}"
shift || true

# The image build prefetches Maven/Liquibase artifacts into a read-only seed.
# Job Pods get only an ephemeral /tmp, so copy the seed there before Maven is
# invoked; this avoids runtime Maven egress and avoids writing to /home/dmh or
# the read-only image layer.  Nacos plan/apply does not invoke Maven and must
# remain runnable with a read-only root even when no /tmp volume is mounted.
case "$command_name" in
  migrate|preflight|status|update-sql)
    if [[ -n "${MAVEN_REPO_SEED:-}" && -d "$MAVEN_REPO_SEED" && ! -d /tmp/maven-repo/org ]]; then
      mkdir -p /tmp/maven-repo
      # Copy the seed contents, not the seed directory itself.  The Maven
      # local-repository root is /tmp/maven-repo; nesting it one level deeper
      # silently turns every artifact into a cache miss.
      cp -a "$MAVEN_REPO_SEED"/. /tmp/maven-repo/
    fi
    ;;
esac

case "$command_name" in
  migrate)
    exec bash ci/scripts/strict-migration.sh update "$@"
    ;;
  preflight)
    exec bash ci/scripts/strict-migration.sh preflight "$@"
    ;;
  status)
    exec bash ci/scripts/strict-migration.sh status "$@"
    ;;
  update-sql)
    exec bash ci/scripts/strict-migration.sh update-sql "$@"
    ;;
  nacos)
    profile="${NACOS_PROFILE:-prod}"
    # Plan and explicit dry-run are offline render-only operations.  They must
    # not contact Nacos or require a routable endpoint; real apply/verify
    # remain fail-closed for staging/production and require a non-loopback
    # runtime address.
    if [[ "$profile" != dev && "${NACOS_MODE:-apply}" != plan && "${NACOS_CONFIG_DRY_RUN:-false}" != true ]]; then
      nacos_host="${NACOS_SERVER_ADDR:-}"
      nacos_host="${nacos_host#http://}"
      nacos_host="${nacos_host#https://}"
      case "$nacos_host" in
        ""|localhost|localhost:*|127.0.0.1|127.0.0.1:*|0.0.0.0|0.0.0.0:*|'[::1]'|'[::1]':*)
          echo 'staging/prod Nacos jobs require a non-loopback NACOS_SERVER_ADDR' >&2
          exit 78
          ;;
      esac
    fi
    export NACOS_GROUP="${NACOS_GROUP:?NACOS_GROUP is required}"
    exec bash publish-nacos-config.sh "$profile"
    ;;
  *)
    echo "usage: dbops-entrypoint.sh {migrate|preflight|status|update-sql|nacos}" >&2
    exit 64
    ;;
esac
