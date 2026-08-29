#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
STATE_FILE="${1:-}"
CONCURRENCY="${2:-8}"
REQUEST_COUNT="${3:-32}"

[[ -f "$STATE_FILE" ]] || {
  echo "用法: $0 <fixture.env> [concurrency] [request-count]" >&2
  exit 2
}
[[ "$CONCURRENCY" =~ ^[1-9][0-9]*$ && "$CONCURRENCY" -le 64 ]] || {
  echo "并发数必须是 1..64" >&2
  exit 2
}
[[ "$REQUEST_COUNT" =~ ^[1-9][0-9]*$ && "$REQUEST_COUNT" -le 1000 ]] || {
  echo "请求数必须是 1..1000" >&2
  exit 2
}

# shellcheck disable=SC1090
source "$STATE_FILE"
[[ "${E2E_PROJECT_ROOT:-}" == "$PROJECT_ROOT" ]] || {
  echo "fixture状态文件不属于当前项目" >&2
  exit 1
}
[[ "${E2E_DB_NAME:-}" =~ ^dataplatform_connector_e2e_[0-9]{14}_[0-9]+_regression$ ]] || {
  echo "拒绝使用非隔离E2E数据库名: ${E2E_DB_NAME:-}" >&2
  exit 1
}

for command_name in curl psql python3; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少命令: $command_name" >&2
    exit 1
  }
done

export PGPASSWORD="$E2E_DB_PASSWORD"
PSQL=(psql -X -v ON_ERROR_STOP=1 -h "$E2E_DB_HOST" -p "$E2E_DB_PORT" -U "$E2E_DB_USERNAME")
interface_code="$("${PSQL[@]}" -d "$E2E_DB_NAME" -Atq -c "SELECT interface_code FROM api_interface WHERE id = ${FIXTURE_INTERFACE_ID} AND status = 'active' AND deleted = false")"
scene_code="$("${PSQL[@]}" -d "$E2E_DB_NAME" -Atq -c "SELECT scene_code FROM call_scene WHERE status = 'active' AND deleted = false ORDER BY id LIMIT 1")"
api_key_row="$("${PSQL[@]}" -d "$E2E_DB_NAME" -Atq -F '|' -c "SELECT k.api_key, cp.product_code FROM api_key k JOIN api_key_interface aki ON aki.api_key_id = k.id JOIN api_key_product akp ON akp.api_key_id = k.id JOIN caller_product cp ON cp.id = akp.product_id WHERE aki.interface_id = ${FIXTURE_INTERFACE_ID} AND aki.status = 'ACTIVE' AND k.status = 'active' AND k.deleted = false AND cp.status = 'active' AND cp.deleted = false ORDER BY k.id LIMIT 1")"
IFS='|' read -r api_key product_code <<< "$api_key_row"
[[ -n "$interface_code" && -n "$scene_code" && -n "$api_key" && -n "$product_code" ]] || {
  echo "没有找到已授权的活动 API Key 或接口: ${FIXTURE_INTERFACE_ID}" >&2
  exit 1
}

gateway_url="${E2E_GATEWAY_URL:-http://127.0.0.1:8888}"
run_id="$(date -u +%Y%m%d%H%M%S)_$$"
request_prefix="connector-e2e-capacity-${run_id}"
probe_dir="$(mktemp -d)"
cleanup() { rm -rf -- "$probe_dir"; }
trap cleanup EXIT

start_epoch="$(python3 -c 'import time; print(time.time_ns())')"
next_request=1
while [[ "$next_request" -le "$REQUEST_COUNT" ]]; do
  batch_end=$((next_request + CONCURRENCY - 1))
  [[ "$batch_end" -le "$REQUEST_COUNT" ]] || batch_end="$REQUEST_COUNT"
  batch_pids=()
  for request_number in $(seq "$next_request" "$batch_end"); do
    request_id="${request_prefix}-${request_number}"
    response_file="$probe_dir/response-${request_number}.json"
    metrics_file="$probe_dir/metrics-${request_number}.txt"
    payload_file="$probe_dir/payload-${request_number}.json"
    printf '{"requestId":"%s","apiCode":"%s","apiVersion":"v1","productCode":"%s","sceneCode":"%s","useCache":false,"params":{"probe":"capacity","requestId":"%s"}}\n' "$request_id" "$interface_code" "$product_code" "$scene_code" "$request_id" >"$payload_file"
    (
      curl -skS --connect-timeout 5 --max-time 30 -H "Content-Type: application/json" -H "X-Api-Key: $api_key" -H "X-Trace-Id: $request_id" -X POST "$gateway_url/openapi/v1/query" --data-binary "@$payload_file" -o "$response_file" -w '%{http_code}|%{time_total}' >"$metrics_file" 2>"$probe_dir/curl-${request_number}.err" || printf '000|0\n' >"$metrics_file"
    ) &
    batch_pids+=("$!")
  done
  for batch_pid in "${batch_pids[@]}"; do
    wait "$batch_pid" || true
  done
  next_request=$((batch_end + 1))
done

end_epoch="$(python3 -c 'import time; print(time.time_ns())')"
REQUEST_PREFIX="$request_prefix" START_EPOCH="$start_epoch" END_EPOCH="$end_epoch" REQUEST_COUNT="$REQUEST_COUNT" CONCURRENCY="$CONCURRENCY" METRICS_DIR="$probe_dir" python3 - <<'PY'
import json
import os
import pathlib

metrics = []
application_successes = 0
for path in sorted(pathlib.Path(os.environ["METRICS_DIR"]).glob("metrics-*.txt")):
    values = path.read_text(encoding="utf-8").strip().split("|")
    if len(values) != 2:
        continue
    try:
        status = int(values[0])
        metrics.append((status, float(values[1]) * 1000))
    except ValueError:
        continue
    try:
        body = json.loads(path.with_name(path.name.replace("metrics-", "response-").replace(".txt", ".json")).read_text(encoding="utf-8"))
        if status == 200 and body.get("code") == 200 and body.get("data", {}).get("success") is True:
            application_successes += 1
    except (OSError, json.JSONDecodeError, AttributeError):
        pass

durations = sorted(value for _, value in metrics)
def percentile(values, fraction):
    if not values:
        return 0
    return values[min(len(values) - 1, int(round((len(values) - 1) * fraction)))]

print(json.dumps({
    "requestPrefix": os.environ["REQUEST_PREFIX"],
    "concurrency": int(os.environ["CONCURRENCY"]),
    "requested": int(os.environ["REQUEST_COUNT"]),
    "completed": len(metrics),
    "clientFailures": int(os.environ["REQUEST_COUNT"]) - len(metrics),
    "http200": sum(status == 200 for status, _ in metrics),
    "applicationSuccesses": application_successes,
    "elapsedMs": round((int(os.environ["END_EPOCH"]) - int(os.environ["START_EPOCH"])) / 1_000_000, 1),
    "p50Ms": round(percentile(durations, 0.50), 1),
    "p95Ms": round(percentile(durations, 0.95), 1),
    "maxMs": round(max(durations, default=0), 1),
}, ensure_ascii=False, sort_keys=True))
PY

# CallRecord is written asynchronously; report only this fixture run's
# persisted coverage and never print request payloads or credentials.
persisted=0
for _ in {1..20}; do
  persisted="$("${PSQL[@]}" -d "$E2E_DB_NAME" -Atq -c "SELECT count(*) FROM call_record WHERE trace_id LIKE '${request_prefix}-%'")"
  [[ "$persisted" -ge "$REQUEST_COUNT" ]] && break
  sleep 0.25
done
persisted_facts="$("${PSQL[@]}" -d "$E2E_DB_NAME" -Atq -F '|' -c "SELECT count(*), count(*) FILTER (WHERE interface_id = ${FIXTURE_INTERFACE_ID}), count(*) FILTER (WHERE plugin_id = 'e2e-signed-connector' AND plugin_version = '1.1.0' AND pipeline_version IS NOT NULL AND length(trim(snapshot_hash)) = 64), count(*) FILTER (WHERE error_code IS NOT NULL) FROM call_record WHERE trace_id LIKE '${request_prefix}-%'")"
IFS='|' read -r persisted persisted_interface_facts persisted_connector_facts error_records <<< "$persisted_facts"
[[ "$persisted" -ge "$REQUEST_COUNT" && "$persisted_interface_facts" -eq "$persisted" && "$persisted_connector_facts" -eq "$persisted" ]] || {
  echo "容量观察CallRecord身份或连接器事实不完整: records=$persisted interface=$persisted_interface_facts connector=$persisted_connector_facts/$REQUEST_COUNT" >&2
  exit 1
}
printf 'persistedCallRecords=%s\n' "$persisted"
printf 'persistedInterfaceFacts=%s persistedConnectorFacts=%s\n' "$persisted_interface_facts" "$persisted_connector_facts"
printf 'persistedErrorRecords=%s\n' "$error_records"
