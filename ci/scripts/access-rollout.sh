#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:?NAMESPACE is required}"
STATEFULSET="${STATEFULSET:-data-manager-hub-access}"
SMOKE_COMMAND="${ACCESS_SMOKE_COMMAND:-}"

replicas="$(kubectl -n "$NAMESPACE" get statefulset "$STATEFULSET" -o jsonpath='{.spec.replicas}')"
[[ "$replicas" =~ ^[1-9][0-9]*$ ]] || { echo "invalid replica count: $replicas" >&2; exit 2; }

wait_pod() {
  local ordinal="$1"
  local pod="${STATEFULSET}-${ordinal}"
  local deadline=$(( $(date +%s) + 900 ))
  while (( $(date +%s) < deadline )); do
    local expected_revision actual_revision ready_status
    expected_revision="$(kubectl -n "$NAMESPACE" get statefulset "$STATEFULSET" -o jsonpath='{.status.updateRevision}' 2>/dev/null || true)"
    actual_revision="$(kubectl -n "$NAMESPACE" get pod "$pod" -o jsonpath='{.metadata.labels.controller-revision-hash}' 2>/dev/null || true)"
    ready_status="$(kubectl -n "$NAMESPACE" get pod "$pod" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true)"
    # A Ready old Pod is not sufficient: require the StatefulSet update
    # revision label so partition rollout cannot pass before the new image runs.
    if [[ -n "$expected_revision" && "$actual_revision" == "$expected_revision" && "$ready_status" == True ]]; then
      break
    fi
    sleep 5
  done
  if (( $(date +%s) >= deadline )); then
    echo "timed out waiting for updated Ready Pod: $pod" >&2
    kubectl -n "$NAMESPACE" describe pod "$pod" >&2 || true
    return 2
  fi
  if [[ -n "$SMOKE_COMMAND" ]]; then
    POD_NAME="$pod" NAMESPACE="$NAMESPACE" bash -c "$SMOKE_COMMAND"
  fi
}

# Kubernetes StatefulSet RollingUpdate proceeds in descending ordinal order.
# Keep the partition at replicas-1 and lower it one ordinal at a time so an
# Access Pod that has not completed plugin synchronization stops the rollout.
partition=$((replicas - 1))
kubectl -n "$NAMESPACE" patch statefulset "$STATEFULSET" --type merge \
  -p "{\"spec\":{\"updateStrategy\":{\"type\":\"RollingUpdate\",\"rollingUpdate\":{\"partition\":${partition}}}}}"
# Process each ordinal exactly once, then stop after ordinal 0 has completed;
# partition remains 0, which is the StatefulSet setting that permits all
# replicas to follow the controller revision. A condition based on
# `partition < replicas` would invoke wait_pod with ordinal -1 after all real
# Pods had already passed.
while (( partition >= 0 )); do
  wait_pod "$partition"
  partition=$((partition - 1))
  if (( partition >= 0 )); then
    kubectl -n "$NAMESPACE" patch statefulset "$STATEFULSET" --type merge \
      -p "{\"spec\":{\"updateStrategy\":{\"rollingUpdate\":{\"partition\":${partition}}}}}"
  fi
done

echo "Access StatefulSet rollout passed: ${STATEFULSET} replicas=${replicas}"
