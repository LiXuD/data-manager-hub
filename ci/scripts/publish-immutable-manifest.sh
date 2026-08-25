#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage: publish-immutable-manifest.sh --ref REF --file MANIFEST_JSON
EOF
}

manifest_ref=""
manifest_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref) manifest_ref="${2:?missing manifest ref}"; shift 2 ;;
    --file) manifest_file="${2:?missing manifest file}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 64 ;;
  esac
done

[[ -n "$manifest_ref" && -s "$manifest_file" ]] || { usage; exit 64; }
command -v oras >/dev/null 2>&1 || { echo "oras is required" >&2; exit 20; }
command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 20; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 20; }

expected_content_digest="sha256:$(sha256sum "$manifest_file" | awk '{print $1}')"
descriptor_json=""
descriptor_error_file="$(mktemp)"
if descriptor_json="$(oras manifest fetch --descriptor "$manifest_ref" 2>"$descriptor_error_file")"; then
  rm -f "$descriptor_error_file"
  existing_manifest="$(oras manifest fetch "$manifest_ref")"
  [[ "$(jq -r '.artifactType // empty' <<< "$existing_manifest")" == "application/vnd.dmh.build-manifest.v1+json" ]] || {
    echo "existing OCI artifact type is not a Build Manifest: $manifest_ref" >&2
    exit 2
  }
  [[ "$(jq '.layers | length' <<< "$existing_manifest")" == 1 ]] || {
    echo "existing Build Manifest must contain exactly one layer: $manifest_ref" >&2
    exit 2
  }
  existing_layer="$(jq -r '.layers[0].digest // empty' <<< "$existing_manifest")"
  [[ "$existing_layer" == "$expected_content_digest" ]] || {
    echo "immutable Build Manifest tag already points at different content: $manifest_ref" >&2
    exit 2
  }
  digest="$(jq -r .digest <<< "$descriptor_json")"
  echo "reusing immutable Build Manifest tag: $manifest_ref" >&2
else
  descriptor_error="$(<"$descriptor_error_file")"
  rm -f "$descriptor_error_file"
  if ! grep -Eiq '(404|not[ _-]?found|manifest[ _-]?unknown|name[ _-]?unknown)' <<< "$descriptor_error"; then
    echo "unable to determine immutable Build Manifest tag state: $manifest_ref${descriptor_error:+: $descriptor_error}" >&2
    exit 2
  fi
  oras push "$manifest_ref" \
    --artifact-type application/vnd.dmh.build-manifest.v1+json \
    "$manifest_file:application/json" >&2
  descriptor_json="$(oras manifest fetch --descriptor "$manifest_ref")"
  digest="$(jq -r .digest <<< "$descriptor_json")"
  committed_manifest="$(oras manifest fetch "$manifest_ref")"
  [[ "$(jq -r '.artifactType // empty' <<< "$committed_manifest")" == "application/vnd.dmh.build-manifest.v1+json" ]] || {
    echo 'pushed artifact type is not a Build Manifest' >&2
    exit 2
  }
  [[ "$(jq '.layers | length' <<< "$committed_manifest")" == 1 ]] || {
    echo 'pushed Build Manifest must contain exactly one layer' >&2
    exit 2
  }
  committed_layer="$(jq -r '.layers[0].digest // empty' <<< "$committed_manifest")"
  [[ "$committed_layer" == "$expected_content_digest" ]] || {
    echo 'pushed Build Manifest content digest does not match canonical file' >&2
    exit 2
  }
fi

[[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || {
  echo "invalid OCI manifest digest: $digest" >&2
  exit 2
}
printf '%s\n' "$digest"
