#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:?NAMESPACE is required}"
JOB="${JOB:?JOB is required}"
IMAGE="${IMAGE:?IMAGE is required}"
PULL_SECRET="${PULL_SECRET:-dmh-ghcr-pull}"
ENV_SECRET="${ENV_SECRET:-dmh-runtime}"
ENV_SECRETS="${ENV_SECRETS:-$ENV_SECRET}"
SERVICE_ACCOUNT="${SERVICE_ACCOUNT:-dmh-runtime}"
BACKOFF_LIMIT="${BACKOFF_LIMIT:-0}"
job_kind=''
case "$IMAGE" in
  ghcr.io/lixud/data-manager-hub-dbops@sha256:[0-9a-f][0-9a-f]*) job_kind=dbops ;;
  ghcr.io/lixud/data-manager-hub-acceptance@sha256:[0-9a-f][0-9a-f]*) job_kind=acceptance ;;
  *)
    echo 'private Jobs require a digest-qualified dbops or acceptance data-manager-hub GHCR image' >&2
    exit 64
    ;;
esac
[[ "$IMAGE" =~ ^ghcr\.io/lixud/data-manager-hub-(dbops|acceptance)@sha256:[0-9a-f]{64}$ ]] || {
  echo 'private Jobs require a complete digest-qualified dbops or acceptance data-manager-hub GHCR image' >&2
  exit 64
}
[[ "$BACKOFF_LIMIT" == 0 ]] || {
  echo 'private Jobs must use backoffLimit=0; retries are decided by the release operator' >&2
  exit 64
}
[[ "$PULL_SECRET" == dmh-ghcr-pull ]] || {
  echo 'private Jobs must use the pre-created dmh-ghcr-pull Secret' >&2
  exit 64
}
case "$job_kind,$ENV_SECRETS" in
  dbops,dmh-runtime|acceptance,dmh-runtime,dmh-acceptance) ;;
  *)
    echo 'private Jobs must match image-to-Secret contract: dbops=dmh-runtime; acceptance=dmh-runtime,dmh-acceptance' >&2
    exit 64
    ;;
esac
[[ "$SERVICE_ACCOUNT" == dmh-runtime ]] || {
  echo 'private Jobs must use the least-privilege dmh-runtime ServiceAccount' >&2
  exit 64
}

# The acceptance image deliberately runs Maven's integration-test lifecycle
# in the Job.  Maven and Failsafe write module-local target directories that
# cannot all be covered by the small dbops emptyDir mounts.  It is still a
# trusted, digest-pinned image and runs as UID 10001 with no token, but needs
# an ephemeral writable container layer; dbops migration/Nacos Jobs remain
# read-only-root.
read_only_root=true
if [[ "$IMAGE" == ghcr.io/lixud/data-manager-hub-acceptance@sha256:* ]]; then
  read_only_root=false
fi

args=()
env_pairs=()
while [[ "${1:-}" != "--" && "$#" -gt 0 ]]; do
  case "$1" in
    --env)
      [[ "$#" -ge 2 ]] || { echo '--env requires NAME=VALUE' >&2; exit 64; }
      [[ "$2" =~ ^[A-Za-z_][A-Za-z0-9_]*=.*$ ]] || { echo "invalid --env assignment: $2" >&2; exit 64; }
      case "${2%%=*}" in
        NACOS_PROFILE|NACOS_NAMESPACE|NACOS_GROUP|NACOS_MODE) ;;
        *) echo "private Job explicit env is not allowlisted: ${2%%=*}" >&2; exit 64 ;;
      esac
      env_pairs+=("$2")
      shift 2
      ;;
    *)
      echo "unknown option before --: $1" >&2
      exit 64
      ;;
  esac
done
if [[ "${1:-}" == "--" ]]; then
  shift
  args=("$@")
fi

if [[ "$job_kind" == dbops ]]; then
  for arg in "${args[@]}"; do
    case "$arg" in
      migrate|preflight|status|update-sql|nacos) ;;
      *)
        echo "dbops private Jobs may use only migrate|preflight|status|update-sql|nacos (got $arg)" >&2
        exit 64
        ;;
    esac
  done
elif [[ "${#args[@]}" -gt 0 ]]; then
  echo 'acceptance private Jobs must use the image entrypoint without arguments' >&2
  exit 64
fi

args_json='[]'
if [[ "${#args[@]}" -gt 0 ]]; then
  args_json="$(printf '%s\n' "${args[@]}" | jq -R . | jq -s .)"
fi

env_json='[]'
if [[ "${#env_pairs[@]}" -gt 0 ]]; then
  env_json="$(printf '%s\n' "${env_pairs[@]}" | jq -R 'split("=") | {name: .[0], value: (.[1:] | join("="))}' | jq -s .)"
fi

env_from_json="$(printf '%s' "$ENV_SECRETS" | tr ',' '\n' | awk 'NF {print}' | jq -R '{secretRef:{name:.}}' | jq -s .)"

kubectl -n "$NAMESPACE" create job "$JOB" --image="$IMAGE" --dry-run=client -o json \
  | jq --arg pull_secret "$PULL_SECRET" --arg service_account "$SERVICE_ACCOUNT" --argjson env_from "$env_from_json" --argjson args "$args_json" --argjson env "$env_json" --argjson backoff "$BACKOFF_LIMIT" --argjson read_only_root "$read_only_root" \
      '.spec.backoffLimit=$backoff
       | .spec.template.spec.serviceAccountName=$service_account
       | .spec.template.spec.automountServiceAccountToken=false
       | .spec.template.spec.securityContext={runAsNonRoot:true,runAsUser:10001,runAsGroup:10001,fsGroup:10001,seccompProfile:{type:"RuntimeDefault"}}
       | .spec.template.spec.containers[0].securityContext={allowPrivilegeEscalation:false,readOnlyRootFilesystem:$read_only_root,capabilities:{drop:["ALL"]}}
       | .spec.template.spec.imagePullSecrets=[{"name":$pull_secret}]
       | .spec.template.spec.volumes=[{"name":"tmp","emptyDir":{}},{"name":"workspace-target","emptyDir":{}},{"name":"runtime","emptyDir":{}}]
       | .spec.template.spec.containers[0].volumeMounts=[{"name":"tmp","mountPath":"/tmp"},{"name":"workspace-target","mountPath":"/workspace/target"},{"name":"runtime","mountPath":"/workspace/.runtime"}]
       | .spec.template.spec.containers[0].envFrom=$env_from
       | if ($env|length)>0 then .spec.template.spec.containers[0].env=$env else . end
       | if ($args|length)>0 then .spec.template.spec.containers[0].args=$args else . end' \
  | kubectl -n "$NAMESPACE" apply -f -
