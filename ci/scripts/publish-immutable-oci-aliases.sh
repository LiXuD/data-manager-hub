#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage: publish-immutable-oci-aliases.sh --manifest FILE --build-manifest-ref REF \
  --version vMAJOR.MINOR.PATCH --image-namespace ghcr.io/OWNER [--output FILE]
EOF
}

manifest_file=""
build_manifest_ref=""
version=""
image_namespace=""
output_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest) manifest_file="${2:?missing manifest file}"; shift 2 ;;
    --build-manifest-ref) build_manifest_ref="${2:?missing Build Manifest reference}"; shift 2 ;;
    --version) version="${2:?missing release version}"; shift 2 ;;
    --image-namespace) image_namespace="${2:?missing image namespace}"; shift 2 ;;
    --output) output_file="${2:?missing output file}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 64 ;;
  esac
done

[[ -s "$manifest_file" && -n "$build_manifest_ref" && -n "$version" && -n "$image_namespace" ]] || {
  usage
  exit 64
}
[[ "$version" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
  echo "invalid immutable release version: $version" >&2
  exit 2
}
[[ "$image_namespace" =~ ^ghcr\.io/[a-z0-9-]+$ ]] || {
  echo "image namespace must be ghcr.io/<lowercase-owner>" >&2
  exit 2
}
[[ "$build_manifest_ref" =~ ^${image_namespace}/data-manager-hub-build-manifest@sha256:[0-9a-f]{64}$ ]] || {
  echo "Build Manifest reference must be a digest in the expected GHCR repository" >&2
  exit 2
}
command -v jq >/dev/null 2>&1 || { echo 'jq is required' >&2; exit 20; }
command -v oras >/dev/null 2>&1 || { echo 'oras is required' >&2; exit 20; }

expected_components=(gateway masterdata access billing identity governance web dbops acceptance)
actual_components=()
while IFS= read -r component; do
  [[ -n "$component" ]] && actual_components+=("$component")
done < <(jq -r '.spec.images // {} | keys[]' "$manifest_file" | sort)
expected_sorted="$(printf '%s\n' "${expected_components[@]}" | sort)"
actual_sorted="$(printf '%s\n' "${actual_components[@]}")"
[[ "$actual_sorted" == "$expected_sorted" ]] || {
  echo 'Build Manifest image set is not the nine-component release set' >&2
  exit 2
}

digest_for_ref() {
  local reference="$1"
  local descriptor
  local error_file
  local error_message
  error_file="$(mktemp)"
  if descriptor="$(oras manifest fetch --descriptor "$reference" 2>"$error_file")"; then
    rm -f "$error_file"
    if ! jq -er '.digest | select(test("^sha256:[0-9a-f]{64}$"))' <<< "$descriptor"; then
      echo "OCI descriptor has no valid digest: $reference" >&2
      return 5
    fi
    return 0
  fi
  error_message="$(<"$error_file")"
  rm -f "$error_file"
  if grep -Eiq '(404|not[ _-]?found|manifest[ _-]?unknown|name[ _-]?unknown)' <<< "$error_message"; then
    return 4
  fi
  echo "unable to inspect OCI reference: $reference${error_message:+: $error_message}" >&2
  return 5
}

alias_tsv="$(mktemp)"
trap 'rm -f "$alias_tsv"' EXIT

publish_alias() {
  local kind="$1"
  local name="$2"
  local source_ref="$3"
  local expected_digest="$4"
  local target_ref="${source_ref%@*}:$version"
  local existing_digest=""

  [[ "$source_ref" == *@sha256:* ]] || {
    echo "$kind $name source must be digest-qualified: $source_ref" >&2
    exit 2
  }
  if existing_digest="$(digest_for_ref "$target_ref")"; then
    [[ "$existing_digest" == "$expected_digest" ]] || {
      echo "immutable OCI alias already points at a different digest: $target_ref" >&2
      exit 2
    }
    echo "reusing immutable OCI alias: $target_ref -> $expected_digest" >&2
  else
    lookup_status=$?
    [[ "$lookup_status" -eq 4 ]] || exit 2
    oras tag "$source_ref" "$version" >/dev/null
    existing_digest="$(digest_for_ref "$target_ref")" || {
      echo "OCI alias was not readable after tagging: $target_ref" >&2
      exit 2
    }
    [[ "$existing_digest" == "$expected_digest" ]] || {
      echo "OCI alias digest changed during publication: $target_ref" >&2
      exit 2
    }
    echo "published immutable OCI alias: $target_ref -> $expected_digest" >&2
  fi
  printf '%s\t%s\t%s\t%s\n' "$kind" "$name" "$target_ref" "$expected_digest" >> "$alias_tsv"
}

for component in "${expected_components[@]}"; do
  reference="$(jq -er --arg component "$component" '.spec.images[$component].reference' "$manifest_file")"
  [[ "$reference" =~ ^${image_namespace}/data-manager-hub-${component}@sha256:[0-9a-f]{64}$ ]] || {
    echo "image reference for $component is outside the expected GHCR namespace or is not digest-qualified" >&2
    exit 2
  }
  image_digest="${reference##*@}"
  publish_alias image "$component" "$reference" "$image_digest"
done

manifest_digest="${build_manifest_ref##*@}"
publish_alias build-manifest data-manager-hub-build-manifest "$build_manifest_ref" "$manifest_digest"

if [[ -n "$output_file" ]]; then
  mkdir -p "$(dirname "$output_file")"
  jq -Rn \
    --arg apiVersion 'cicd.data-manager-hub/v1' \
    --arg kind 'ReleaseAliases' \
    --arg version "$version" \
    --arg buildManifestDigest "$manifest_digest" \
    --argjson aliases "$(jq -Rn '[inputs | select(length > 0) | split("\t") | {kind: .[0], name: .[1],reference: .[2],digest: (.[3] | rtrimstr("\n"))}]' "$alias_tsv")" \
    '{apiVersion:$apiVersion,kind:$kind,version:$version,buildManifestDigest:$buildManifestDigest,aliases:$aliases}' \
    > "$output_file"
fi

echo "immutable OCI aliases verified for $version"
