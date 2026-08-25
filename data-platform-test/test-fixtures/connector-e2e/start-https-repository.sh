#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_FILE="${1:-}"
PORT="${2:-}"
[[ -f "$STATE_FILE" && "$PORT" =~ ^[0-9]+$ ]] || {
  echo "用法: $0 <fixture.env> <https-port>" >&2
  exit 2
}
# shellcheck disable=SC1090
source "$STATE_FILE"
[[ -d "$FIXTURE_REPOSITORY_DIR" && -f "$FIXTURE_TLS_CERTIFICATE" \
    && -f "$FIXTURE_TLS_PRIVATE_KEY" ]] || {
  echo "fixture 制品或TLS材料不完整" >&2
  exit 1
}

if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "HTTPS端口已被占用: $PORT" >&2
  exit 1
fi

PID_FILE="$FIXTURE_OUTPUT_DIR/https-repository.pid"
python3 "$SCRIPT_DIR/fixture_https_server.py" \
  --root "$FIXTURE_REPOSITORY_DIR" --port "$PORT" \
  --certificate "$FIXTURE_TLS_CERTIFICATE" --private-key "$FIXTURE_TLS_PRIVATE_KEY" \
  --daemonize --pid-file "$PID_FILE" --log-file "$FIXTURE_OUTPUT_DIR/https-repository.log"
for attempt in {1..20}; do
  [[ -s "$PID_FILE" ]] && break
  sleep 0.1
done
[[ -s "$PID_FILE" ]] || { echo "HTTPS fixture 未写入PID文件" >&2; exit 1; }
SERVER_PID="$(tr -d '\r\n' < "$PID_FILE")"
[[ "$SERVER_PID" =~ ^[0-9]+$ ]] || { echo "HTTPS fixture PID无效" >&2; exit 1; }
printf 'FIXTURE_HTTPS_PORT=%q\n' "$PORT" >> "$STATE_FILE"
printf 'FIXTURE_HTTPS_PID=%q\n' "$SERVER_PID" >> "$STATE_FILE"
printf 'FIXTURE_ARTIFACT_URI=%q\n' \
  "https://localhost:$PORT/e2e-signed-connector/1.0.0/connector-plugin.jar" >> "$STATE_FILE"
printf 'FIXTURE_VENDOR_ENDPOINT=%q\n' "https://localhost:$PORT/vendor/echo" >> "$STATE_FILE"

for attempt in {1..30}; do
  if curl --silent --show-error --fail --cacert "$FIXTURE_TLS_CERTIFICATE" \
      "https://localhost:$PORT/health" >/dev/null 2>&1; then
    echo "$SERVER_PID"
    exit 0
  fi
  if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    echo "HTTPS fixture 进程提前退出，详见 $FIXTURE_OUTPUT_DIR/https-repository.log" >&2
    exit 1
  fi
  sleep 1
done

echo "HTTPS fixture 未在30秒内就绪" >&2
exit 1
