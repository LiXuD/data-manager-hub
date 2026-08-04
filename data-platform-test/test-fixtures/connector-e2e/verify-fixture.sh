#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_FILE="${1:-}"
[[ -f "$STATE_FILE" ]] || { echo "用法: $0 <fixture.env>" >&2; exit 2; }
# shellcheck disable=SC1090
source "$STATE_FILE"

if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL_SHA256="$(sha256sum "$FIXTURE_ARTIFACT_JAR" | awk '{print $1}')"
else
  ACTUAL_SHA256="$(shasum -a 256 "$FIXTURE_ARTIFACT_JAR" | awk '{print $1}')"
fi
[[ "$ACTUAL_SHA256" == "$FIXTURE_ARTIFACT_SHA256" ]] || {
  echo "本地 fixture JAR 哈希不匹配" >&2
  exit 1
}

python3 "$SCRIPT_DIR/canonicalize_manifest.py" \
  "$FIXTURE_ARTIFACT_JAR" "$FIXTURE_ARTIFACT_SHA256" "$FIXTURE_OUTPUT_DIR/verify-payload.bin"
python3 -c 'import base64,pathlib,sys; pathlib.Path(sys.argv[2]).write_bytes(base64.b64decode(sys.argv[1], validate=True))' \
  "$FIXTURE_DETACHED_SIGNATURE" "$FIXTURE_OUTPUT_DIR/verify-signature.bin"
java "$SCRIPT_DIR/Ed25519FixtureTool.java" verify \
  "$FIXTURE_OUTPUT_DIR/verify-payload.bin" "$FIXTURE_SIGNING_PUBLIC_KEY_PEM" \
  "$FIXTURE_OUTPUT_DIR/verify-signature.bin"
keytool -list -storetype PKCS12 -keystore "$FIXTURE_TLS_TRUSTSTORE" \
  -storepass "$FIXTURE_TLS_TRUSTSTORE_PASSWORD" \
  -alias connector-fixture-localhost >/dev/null

DOWNLOADED_JAR="$FIXTURE_OUTPUT_DIR/downloaded-connector-plugin.jar"
curl --silent --show-error --fail --cacert "$FIXTURE_TLS_CERTIFICATE" \
  "$FIXTURE_ARTIFACT_URI" --output "$DOWNLOADED_JAR"
if command -v sha256sum >/dev/null 2>&1; then
  DOWNLOADED_SHA256="$(sha256sum "$DOWNLOADED_JAR" | awk '{print $1}')"
else
  DOWNLOADED_SHA256="$(shasum -a 256 "$DOWNLOADED_JAR" | awk '{print $1}')"
fi
[[ "$DOWNLOADED_SHA256" == "$FIXTURE_ARTIFACT_SHA256" ]] || {
  echo "HTTPS 下载后的 fixture JAR 哈希不匹配" >&2
  exit 1
}

ECHO_RESPONSE="$(curl --silent --show-error --fail --cacert "$FIXTURE_TLS_CERTIFICATE" \
  -H 'Content-Type: application/json' -H 'X-Connector-Fixture: e2e-signed-connector' \
  --data '{"probe":"signed-fixture"}' "$FIXTURE_VENDOR_ENDPOINT")"
python3 -c 'import json,sys; value=json.loads(sys.argv[1]); assert value == {"success": True, "fixture": "e2e-signed-connector", "received": {"probe": "signed-fixture"}}' "$ECHO_RESPONSE"

echo "fixture验证通过: Maven JAR / Ed25519 / PKCS12 TrustStore / HTTPS下载 / HTTPS厂商端点"
