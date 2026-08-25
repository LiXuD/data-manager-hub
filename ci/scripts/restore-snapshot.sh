#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage: restore-snapshot.sh --adapter /absolute/path --receipt FILE \
  --source-instance ID --target-instance ID --schema-version VERSION \
  --changelog-digest sha256:... [--signature-verifier /absolute/path] \
  [--max-age-hours HOURS] --output FILE

The adapter is environment-owned and must restore to a new instance. This
wrapper verifies the signed SnapshotReceipt first and rejects in-place restore.
The adapter owns cloud credentials and must print one verified JSON result.
EOF
}

adapter=""
receipt=""
source_instance=""
target_instance=""
schema_version=""
changelog_digest=""
signature_verifier=""
output=""
max_age_hours="${MAX_SNAPSHOT_AGE_HOURS:-2}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --adapter) adapter="${2:?missing adapter}"; shift 2 ;;
    --receipt) receipt="${2:?missing receipt}"; shift 2 ;;
    --source-instance) source_instance="${2:?missing source instance}"; shift 2 ;;
    --target-instance) target_instance="${2:?missing target instance}"; shift 2 ;;
    --schema-version) schema_version="${2:?missing schema version}"; shift 2 ;;
    --changelog-digest) changelog_digest="${2:?missing changelog digest}"; shift 2 ;;
    --signature-verifier) signature_verifier="${2:?missing signature verifier}"; shift 2 ;;
    --max-age-hours) max_age_hours="${2:?missing max age}"; shift 2 ;;
    --output) output="${2:?missing output}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 64 ;;
  esac
done

[[ "$adapter" = /* && -x "$adapter" ]] || { echo "snapshot restore adapter must be an executable absolute path" >&2; exit 2; }
[[ -f "$receipt" ]] || { echo "snapshot receipt is required: $receipt" >&2; exit 2; }
[[ -n "$source_instance" && -n "$target_instance" && "$source_instance" != "$target_instance" ]] || {
  echo "restore requires distinct source and target instances" >&2
  exit 2
}
[[ -n "$schema_version" && -n "$changelog_digest" && -n "$output" ]] || { usage; exit 64; }
[[ "$changelog_digest" =~ ^sha256:[0-9a-f]{64}$ ]] || { echo "invalid changelog digest" >&2; exit 2; }
[[ "$max_age_hours" =~ ^[1-9][0-9]*$ ]] || { echo "max age must be a positive integer" >&2; exit 64; }
if [[ -n "$signature_verifier" ]]; then
  [[ "$signature_verifier" = /* && -x "$signature_verifier" ]] || {
    echo "signature verifier must be an executable absolute path" >&2
    exit 2
  }
fi

recovery_position="$(jq -r '.recoveryPosition // .walLsn // .gtidExecuted // empty' "$receipt")"
[[ -n "$recovery_position" ]] || { echo "snapshot receipt has no recovery position" >&2; exit 2; }
verify_command=(
  python3 "$(dirname "$0")/verify-snapshot-receipt.py" "$receipt"
  --source-instance "$source_instance"
  --schema-version "$schema_version"
  --changelog-digest "$changelog_digest"
  --max-age-hours "$max_age_hours"
)
if [[ -n "$signature_verifier" ]]; then
  verify_command+=(--signature-verifier "$signature_verifier")
fi
"${verify_command[@]}"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
"$adapter" \
  --snapshot-receipt "$receipt" \
  --source-instance "$source_instance" \
  --target-instance "$target_instance" \
  --recovery-position "$recovery_position" > "$tmp"

jq -e \
  --arg source "$source_instance" \
  --arg target "$target_instance" \
  '(.status == "VERIFIED") and (.sourceInstanceId == $source) and (.targetInstanceId == $target) and (.sourceInstanceId != .targetInstanceId)' \
  "$tmp" >/dev/null || {
  echo "snapshot restore adapter did not return a verified new-instance result" >&2
  exit 2
}

mkdir -p "$(dirname "$output")"
install -m 0600 "$tmp" "$output"
echo "verified snapshot restore result written: $output"
