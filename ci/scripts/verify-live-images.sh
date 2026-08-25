#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:?NAMESPACE is required}"
MANIFEST="${MANIFEST:?MANIFEST is required}"
RELEASE="${RELEASE:-data-manager-hub}"

[[ -s "$MANIFEST" ]] || { echo "manifest not found: $MANIFEST" >&2; exit 2; }
failures=0
while IFS=$'\t' read -r component reference; do
  case "$component" in
    access) workload=statefulset ;;
    gateway|masterdata|billing|identity|governance|web) workload=deployment ;;
    *) continue ;;
  esac
  name="${RELEASE}-${component}"
  actual="$(kubectl -n "$NAMESPACE" get "$workload/$name" -o json | jq -r '[.spec.template.spec.containers[].image] | join("\n")' 2>/dev/null || true)"
  if ! grep -Fqx "$reference" <<< "$actual"; then
    echo "live image mismatch for $component: expected=$reference actual=${actual:-<missing>}" >&2
    failures=$((failures + 1))
  fi
done < <(jq -r '.spec.images | to_entries[] | [.key, .value.reference] | @tsv' "$MANIFEST")

if [[ "$failures" -ne 0 ]]; then
  exit 2
fi
echo "live workload images match Build Manifest"
