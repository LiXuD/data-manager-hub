#!/usr/bin/env bash
set -euo pipefail

# Validate only resource existence and the Runner's effective permissions. This
# command never reads Secret data; it is safe to run before a deployment Job.
NAMESPACE="${NAMESPACE:?NAMESPACE is required}"
ENVIRONMENT="${ENVIRONMENT:?ENVIRONMENT is required}"
SERVICE_ACCOUNT="${SERVICE_ACCOUNT:-dmh-deployer}"
command -v jq >/dev/null 2>&1 || { echo "jq is required for runner identity verification" >&2; exit 20; }
[[ "$SERVICE_ACCOUNT" == dmh-deployer ]] || {
  echo "deployment runner must use the pre-created dmh-deployer ServiceAccount" >&2
  exit 64
}

case "$ENVIRONMENT" in
  dev|staging|production) ;;
  *) echo "unsupported environment: $ENVIRONMENT" >&2; exit 64 ;;
esac

expected_namespace="dmh-${ENVIRONMENT}"
[[ "$ENVIRONMENT" == production ]] && expected_namespace=dmh-prod
[[ "$NAMESPACE" == "$expected_namespace" ]] || {
  echo "namespace mismatch: environment=$ENVIRONMENT expected=$expected_namespace actual=$NAMESPACE" >&2
  exit 2
}

# Namespace-scoped access is intentional: do not require cluster-wide
# `get namespaces` permission just to validate the deployment target.
kubectl -n "$NAMESPACE" get serviceaccount "$SERVICE_ACCOUNT" -o name >/dev/null
kubectl -n "$NAMESPACE" get serviceaccount dmh-runtime -o name >/dev/null

# `kubectl auth can-i` is evaluated for the current kubeconfig identity.  Do
# not accept a cluster-admin kubeconfig merely because it can perform the
# required operations: the protected runner must actually authenticate as the
# namespace-local dmh-deployer ServiceAccount.
expected_identity="system:serviceaccount:${NAMESPACE}:${SERVICE_ACCOUNT}"
actual_identity="$(kubectl auth whoami -o json | jq -r '.status.userInfo.username // empty')"
[[ "$actual_identity" == "$expected_identity" ]] || {
  echo "runner identity mismatch: expected $expected_identity, got ${actual_identity:-<unknown>}" >&2
  exit 2
}

required_secrets=(dmh-runtime dmh-internal-auth dmh-connector-truststore dmh-ghcr-pull)
if [[ "$ENVIRONMENT" != dev ]]; then
  required_secrets+=(dmh-acceptance)
fi
if [[ "$ENVIRONMENT" == production ]]; then
  required_secrets+=(dmh-snapshot-verifier)
fi
for secret in "${required_secrets[@]}"; do
  # `-o name` deliberately proves existence without reading or logging values.
  kubectl -n "$NAMESPACE" get secret "$secret" -o name >/dev/null
done

# Helm is configured with HELM_DRIVER=configmap, so release revisions are
# namespace-scoped ConfigMaps. The runner gets only named Secret reads for
# preflight; it cannot list or mutate application Secret values.
for resource in deployments statefulsets jobs pods services configmaps serviceaccounts persistentvolumeclaims; do
  if [[ "$(kubectl auth can-i get "$resource" -n "$NAMESPACE")" != yes ]]; then
    echo "Runner lacks get permission for $resource in $NAMESPACE" >&2
    exit 2
  fi
done
# Explicitly check the subresources used for Pod listing and Job log
# collection.  A parent-resource permission does not imply access to these
# Kubernetes subresources in a namespaced Role.
for request in "get pods/log" "list pods"; do
  read -r verb resource <<< "$request"
  if [[ "$(kubectl auth can-i "$verb" "$resource" -n "$NAMESPACE")" != yes ]]; then
    echo "Runner lacks permission: $request in $NAMESPACE" >&2
    exit 2
  fi
done
for request in \
  "create deployments" "create statefulsets" "create jobs" "create services" "create configmaps" \
  "create networkpolicies.networking.k8s.io" "create poddisruptionbudgets.policy" \
  "patch deployments" "patch statefulsets" "patch services" "patch configmaps" \
  "patch networkpolicies.networking.k8s.io" "patch poddisruptionbudgets.policy" \
  "patch persistentvolumeclaims" \
  "delete deployments" "delete statefulsets" "delete services" "delete configmaps" \
  "delete networkpolicies.networking.k8s.io" "delete poddisruptionbudgets.policy" "delete jobs" \
  ; do
  read -r verb resource <<< "$request"
  if [[ "$(kubectl auth can-i "$verb" "$resource" -n "$NAMESPACE")" != yes ]]; then
    echo "Runner lacks permission: $request in $NAMESPACE" >&2
    exit 2
  fi
done
for secret in "${required_secrets[@]}"; do
  if [[ "$(kubectl auth can-i get "secret/$secret" -n "$NAMESPACE")" != yes ]]; then
    echo "Runner lacks named Secret get permission: $secret in $NAMESPACE" >&2
    exit 2
  fi
done

# A named `resourceNames` rule is not enough when another RoleBinding grants a
# broader Secret permission.  Verify the effective identity is unable to list
# or mutate the namespace Secret collection before any release Job is created.
# This is a live check in addition to the repository-owned RBAC static policy.
for request in \
  "list secrets" "watch secrets" "create secrets" "update secrets" \
  "patch secrets" "delete secrets" "deletecollection secrets"; do
  read -r verb resource <<< "$request"
  if [[ "$(kubectl auth can-i "$verb" "$resource" -n "$NAMESPACE")" != no ]]; then
    echo "Runner must not have $request permission in $NAMESPACE" >&2
    exit 2
  fi
done

# A newly applied ValidatingAdmissionPolicy is not necessarily type-checked by
# the API server at the instant its YAML apply returns.  Do not rely on the
# policy object's existence alone: perform a server-side dry-run of a valid
# private Job with a deliberately overridden command and require the exact
# entrypoint-boundary denial.  This uses the runner's existing namespaced Job
# create permission and does not persist a Job or read any Secret value.
probe_output=""
probe_status=0
set +e
probe_output="$(
  kubectl create job dmh-admission-probe -n "$NAMESPACE" \
    --image=ghcr.io/lixud/data-manager-hub-dbops@sha256:0000000000000000000000000000000000000000000000000000000000000000 \
    --dry-run=client -o json \
    | jq '.spec.backoffLimit=0
      | .spec.template.spec.serviceAccountName="dmh-runtime"
      | .spec.template.spec.automountServiceAccountToken=false
      | .spec.template.spec.securityContext={runAsNonRoot:true,runAsUser:10001,runAsGroup:10001,seccompProfile:{type:"RuntimeDefault"}}
      | .spec.template.spec.containers[0].securityContext={allowPrivilegeEscalation:false,readOnlyRootFilesystem:true,capabilities:{drop:["ALL"]}}
      | .spec.template.spec.imagePullSecrets=[{"name":"dmh-ghcr-pull"}]
      | .spec.template.spec.volumes=[{"name":"tmp","emptyDir":{}},{"name":"workspace-target","emptyDir":{}},{"name":"runtime","emptyDir":{}}]
      | .spec.template.spec.containers[0].volumeMounts=[{"name":"tmp","mountPath":"/tmp"},{"name":"workspace-target","mountPath":"/workspace/target"},{"name":"runtime","mountPath":"/workspace/.runtime"}]
      | .spec.template.spec.containers[0].command=["sh","-c","exit 0"]' \
    | kubectl create --dry-run=server -f - 2>&1
)"
probe_status=$?
set -e
if [[ "$probe_status" -eq 0 ]]; then
  echo 'cluster admission preflight failed: private Job command override was accepted' >&2
  exit 2
fi
if [[ "$probe_output" != *'private data-manager-hub Jobs must use the image entrypoint'* ]]; then
  echo 'cluster admission preflight failed: ValidatingAdmissionPolicy is missing, not ready, or has an unexpected contract' >&2
  printf '%s\n' "$probe_output" >&2
  exit 2
fi

echo "cluster preflight passed: environment=$ENVIRONMENT namespace=$NAMESPACE serviceAccount=$SERVICE_ACCOUNT"
