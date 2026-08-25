#!/usr/bin/env bash
set -euo pipefail

# Wait for a one-shot release Job while distinguishing Complete from Failed.
# `kubectl wait --for=condition=complete` can otherwise sleep until its full
# timeout when Kubernetes has already marked the Job Failed, delaying the
# failure receipt and rollback path.
NAMESPACE="${NAMESPACE:?NAMESPACE is required}"
JOB="${JOB:?JOB is required}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-1800}"
[[ "$TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || {
  echo 'TIMEOUT_SECONDS must be a positive integer' >&2
  exit 64
}
command -v jq >/dev/null 2>&1 || { echo 'jq is required' >&2; exit 20; }

deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
while (( $(date +%s) < deadline )); do
  job_json="$(kubectl -n "$NAMESPACE" get job "$JOB" -o json 2>/dev/null || true)"
  if [[ -n "$job_json" ]]; then
    if jq -e '.status.conditions[]? | select(.type == "Complete" and .status == "True")' <<< "$job_json" >/dev/null; then
      echo "Job completed: $JOB"
      exit 0
    fi
    if jq -e '.status.conditions[]? | select(.type == "Failed" and .status == "True")' <<< "$job_json" >/dev/null; then
      reason="$(jq -r '[.status.conditions[]? | select(.type == "Failed") | (.reason // .message // "unknown")] | first // "unknown"' <<< "$job_json")"
      echo "Job failed: $JOB (${reason})" >&2
      exit 2
    fi
  fi
  sleep 5
done

echo "timed out waiting for Job: $JOB" >&2
exit 2
