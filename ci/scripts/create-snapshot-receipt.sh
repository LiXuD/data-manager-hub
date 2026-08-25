#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage: create-snapshot-receipt.sh --adapter /absolute/path --source-instance ID \
  --schema-version VERSION --changelog-digest sha256:... --output FILE \
  [--signature-verifier /absolute/path] [--not-before ISO-8601]

The adapter is an environment-owned executable. It must create and verify a
transaction-consistent PostgreSQL snapshot/PITR point, then print one JSON
SnapshotReceipt to stdout. The adapter, never GitHub Actions, owns cloud
credentials and signing keys.
EOF
}

adapter=""
source_instance=""
schema_version=""
changelog_digest=""
output=""
signature_verifier=""
not_before=""
max_age_hours="${MAX_SNAPSHOT_AGE_HOURS:-2}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --adapter) adapter="${2:?missing adapter}"; shift 2 ;;
    --source-instance) source_instance="${2:?missing source instance}"; shift 2 ;;
    --schema-version) schema_version="${2:?missing schema version}"; shift 2 ;;
    --changelog-digest) changelog_digest="${2:?missing changelog digest}"; shift 2 ;;
    --output) output="${2:?missing output}"; shift 2 ;;
    --signature-verifier) signature_verifier="${2:?missing signature verifier}"; shift 2 ;;
    --not-before) not_before="${2:?missing not-before timestamp}"; shift 2 ;;
    --max-age-hours) max_age_hours="${2:?missing max age}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 64 ;;
  esac
done

[[ "$adapter" = /* && -x "$adapter" ]] || { echo "snapshot adapter must be an executable absolute path" >&2; exit 2; }
if [[ -n "$signature_verifier" ]]; then
  [[ "$signature_verifier" = /* && -x "$signature_verifier" ]] || { echo "signature verifier must be an executable absolute path" >&2; exit 2; }
fi
[[ -n "$source_instance" && -n "$schema_version" && -n "$changelog_digest" && -n "$output" ]] || { usage; exit 64; }
[[ "$changelog_digest" =~ ^sha256:[0-9a-f]{64}$ ]] || { echo "invalid changelog digest" >&2; exit 2; }

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
"$adapter" \
  --source-instance "$source_instance" \
  --schema-version "$schema_version" \
  --changelog-digest "$changelog_digest" > "$tmp"

verify_args=()
if [[ -n "$signature_verifier" ]]; then
  verify_args=(--signature-verifier "$signature_verifier")
fi
if [[ -n "$not_before" ]]; then
  verify_args+=(--not-before "$not_before")
fi
python3 "$(dirname "$0")/verify-snapshot-receipt.py" "$tmp" \
  --source-instance "$source_instance" \
  --schema-version "$schema_version" \
  --changelog-digest "$changelog_digest" \
  --max-age-hours "$max_age_hours" "${verify_args[@]}"

mkdir -p "$(dirname "$output")"
install -m 0600 "$tmp" "$output"
echo "verified snapshot receipt written: $output"
