#!/bin/sh
set -eu

if [ -n "${POD_NAME:-}" ] && [ -z "${CONNECTOR_INSTANCE_ID:-}" ]; then
  export CONNECTOR_INSTANCE_ID="${POD_NAME}:${SERVER_PORT:-8080}"
fi

case "${SPRING_PROFILES_ACTIVE:-dev}" in
  staging|prod|production)
    nacos_host="${NACOS_SERVER_ADDR:-}"
    nacos_host="${nacos_host#http://}"
    nacos_host="${nacos_host#https://}"
    case "$nacos_host" in
      ""|localhost|localhost:*|127.0.0.1|127.0.0.1:*|0.0.0.0|0.0.0.0:*|'[::1]'|'[::1]':*)
        echo "NACOS_SERVER_ADDR must point to the environment Nacos service" >&2
        exit 78
        ;;
    esac
    ;;
esac
exec java ${JAVA_OPTS:-} -jar /app/app.jar "$@"
