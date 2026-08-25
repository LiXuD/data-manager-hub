#!/usr/bin/env bash
set -euo pipefail

PLUGIN_FILE="${PLUGIN_FILE:?PLUGIN_FILE is required}"
PLUGIN_ID="${PLUGIN_ID:?PLUGIN_ID is required}"
PLUGIN_VERSION="${PLUGIN_VERSION:?PLUGIN_VERSION is required}"
SIGNING_KEY="${PLUGIN_SIGNING_PRIVATE_KEY:?PLUGIN_SIGNING_PRIVATE_KEY must point to a protected key file}"
OUTPUT="${PLUGIN_RECEIPT_OUTPUT:-plugin-receipt.json}"
NEXUS_URL="${NEXUS_URL:-}"
PLUGIN_REPOSITORY="${PLUGIN_REPOSITORY:-}"
REMOTE_VERIFY_COMMAND="${PLUGIN_REMOTE_VERIFY_COMMAND:-}"

[[ -s "$PLUGIN_FILE" ]] || { echo "plugin file missing: $PLUGIN_FILE" >&2; exit 2; }
[[ -s "$SIGNING_KEY" ]] || { echo "signing key missing" >&2; exit 2; }
command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 2; }

sha="$(sha256sum "$PLUGIN_FILE" | awk '{print $1}')"
signature_file="$(mktemp)"
public_key="$(mktemp)"
trap 'rm -f "$signature_file" "$public_key"' EXIT
# Sign a digest of the artifact. `pkeyutl -rawin` would only accept a payload
# smaller than the RSA modulus and fails for normal JAR files.
openssl dgst -sha256 -sign "$SIGNING_KEY" -out "$signature_file" "$PLUGIN_FILE"
openssl pkey -in "$SIGNING_KEY" -pubout -out "$public_key" >/dev/null 2>&1
openssl dgst -sha256 -verify "$public_key" -signature "$signature_file" "$PLUGIN_FILE" >/dev/null
signature_sha="$(sha256sum "$signature_file" | awk '{print $1}')"
fingerprint="$(openssl pkey -in "$SIGNING_KEY" -pubout -outform DER 2>/dev/null | sha256sum | awk '{print $1}')"

if [[ -n "$NEXUS_URL" ]]; then
  command -v curl >/dev/null 2>&1 || { echo "curl is required for Nexus upload" >&2; exit 2; }
  : "${NEXUS_USERNAME:?NEXUS_USERNAME is required with NEXUS_URL}"
  : "${NEXUS_PASSWORD:?NEXUS_PASSWORD is required with NEXUS_URL}"
  base="${NEXUS_URL%/}/$PLUGIN_ID/$PLUGIN_VERSION"
  curl --fail --silent --show-error -u "$NEXUS_USERNAME:$NEXUS_PASSWORD" \
    --upload-file "$PLUGIN_FILE" "$base/plugin.jar"
  curl --fail --silent --show-error -u "$NEXUS_USERNAME:$NEXUS_PASSWORD" \
    --upload-file "$signature_file" "$base/plugin.jar.sig"
  PLUGIN_REPOSITORY="$base"
fi

[[ -n "$PLUGIN_REPOSITORY" ]] || {
  echo "PLUGIN_REPOSITORY is required when NEXUS_URL is not configured" >&2
  exit 2
}

# A receipt is only trustworthy after the repository adapter has proved that
# the bytes available at the published location equal the locally signed
# bytes. Nexus has a built-in read-back path; every other repository must
# provide an executable verifier owned by the protected publishing environment.
if [[ -n "$NEXUS_URL" ]]; then
  remote_plugin="$(mktemp)"
  remote_signature="$(mktemp)"
  trap 'rm -f "$signature_file" "$public_key" "$remote_plugin" "$remote_signature"' EXIT
  curl --fail --silent --show-error -u "$NEXUS_USERNAME:$NEXUS_PASSWORD" \
    -o "$remote_plugin" "$PLUGIN_REPOSITORY/plugin.jar"
  curl --fail --silent --show-error -u "$NEXUS_USERNAME:$NEXUS_PASSWORD" \
    -o "$remote_signature" "$PLUGIN_REPOSITORY/plugin.jar.sig"
  [[ "$(sha256sum "$remote_plugin" | awk '{print $1}')" == "$sha" ]] || {
    echo 'remote Nexus plugin digest does not match signed bytes' >&2
    exit 2
  }
  [[ "$(sha256sum "$remote_signature" | awk '{print $1}')" == "$signature_sha" ]] || {
    echo 'remote Nexus signature digest does not match signed bytes' >&2
    exit 2
  }
else
  [[ "$REMOTE_VERIFY_COMMAND" = /* && -x "$REMOTE_VERIFY_COMMAND" ]] || {
    echo 'non-Nexus plugin repositories require PLUGIN_REMOTE_VERIFY_COMMAND' >&2
    exit 2
  }
  remote_result="$("$REMOTE_VERIFY_COMMAND" \
    --plugin-file "$PLUGIN_FILE" \
    --signature-file "$signature_file" \
    --repository "$PLUGIN_REPOSITORY")"
  python3 - "$remote_result" "$sha" "$signature_sha" <<'PY'
import json, sys
remote = json.loads(sys.argv[1])
expected_plugin = "sha256:" + sys.argv[2]
expected_signature = "sha256:" + sys.argv[3]
if remote.get("sha256") != expected_plugin:
    raise SystemExit("remote adapter plugin digest does not match signed bytes")
if remote.get("signatureSha256") != expected_signature:
    raise SystemExit("remote adapter signature digest does not match signed bytes")
if not remote.get("repository"):
    raise SystemExit("remote adapter must return repository")
PY
fi

python3 - "$OUTPUT" "$PLUGIN_ID" "$PLUGIN_VERSION" "$sha" "$signature_sha" "$fingerprint" "$PLUGIN_REPOSITORY" <<'PY'
import json, sys
out, plugin_id, version, sha, signature_sha, fingerprint, repository = sys.argv[1:]
receipt = {
    "apiVersion": "cicd.data-manager-hub/v1",
    "kind": "PluginReceipt",
    "id": plugin_id,
    "version": version,
    "sha256": "sha256:" + sha,
    "signatureSha256": "sha256:" + signature_sha,
    "signatureFingerprint": "sha256:" + fingerprint,
    "repository": repository.rstrip("/"),
}
with open(out, "w", encoding="utf-8") as handle:
    json.dump(receipt, handle, ensure_ascii=False, sort_keys=True, indent=2)
    handle.write("\n")
PY
echo "plugin signed: id=$PLUGIN_ID version=$PLUGIN_VERSION sha256=$sha"
