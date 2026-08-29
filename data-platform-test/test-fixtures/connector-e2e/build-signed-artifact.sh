#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
OUTPUT_DIR="${1:-}"
KEY_ID="${E2E_SIGNING_KEY_ID:-connector-e2e-ed25519}"
TRUSTSTORE_PASSWORD="${E2E_TLS_TRUSTSTORE_PASSWORD:-changeit}"

[[ -n "$OUTPUT_DIR" ]] || { echo "用法: $0 <isolated-output-directory>" >&2; exit 2; }
OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
REPOSITORY_DIR="$OUTPUT_DIR/repository"
ARTIFACT_DIR="$REPOSITORY_DIR/e2e-signed-connector/1.1.0"
KEY_DIR="$OUTPUT_DIR/keys"
TLS_DIR="$OUTPUT_DIR/tls"
mkdir -p "$ARTIFACT_DIR" "$KEY_DIR" "$TLS_DIR"

for command_name in mvn java openssl keytool python3 jar; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少命令: $command_name" >&2
    exit 1
  }
done

(cd "$PROJECT_ROOT" && mvn -q -pl :data-platform-external-connector-fixture -am -DskipTests package)
SOURCE_JAR="$PROJECT_ROOT/data-platform-test/test-fixtures/external-connector-plugin/target/e2e-signed-connector-1.1.0.jar"
[[ -s "$SOURCE_JAR" ]] || { echo "插件JAR构建失败: $SOURCE_JAR" >&2; exit 1; }
install -m 0644 "$SOURCE_JAR" "$ARTIFACT_DIR/connector-plugin.jar"
ARTIFACT_JAR="$ARTIFACT_DIR/connector-plugin.jar"

if command -v sha256sum >/dev/null 2>&1; then
  ARTIFACT_SHA256="$(sha256sum "$ARTIFACT_JAR" | awk '{print $1}')"
else
  ARTIFACT_SHA256="$(shasum -a 256 "$ARTIFACT_JAR" | awk '{print $1}')"
fi

python3 "$SCRIPT_DIR/canonicalize_manifest.py" \
  "$ARTIFACT_JAR" "$ARTIFACT_SHA256" "$OUTPUT_DIR/signature-payload.bin"
java "$SCRIPT_DIR/Ed25519FixtureTool.java" generate \
  "$OUTPUT_DIR/signature-payload.bin" "$KEY_DIR" \
  "$OUTPUT_DIR/detached-signature.bin" "$OUTPUT_DIR/detached-signature.b64"
DETACHED_SIGNATURE="$(tr -d '\r\n' < "$OUTPUT_DIR/detached-signature.b64")"
printf '%s\n' "$DETACHED_SIGNATURE" > "$ARTIFACT_DIR/connector-plugin.jar.sig"
SIGNING_PUBLIC_KEY_BASE64="$(tr -d '\r\n' < "$KEY_DIR/ed25519-public.der.b64")"

openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 2 \
  -config "$SCRIPT_DIR/localhost-openssl.cnf" -extensions extensions \
  -keyout "$TLS_DIR/localhost-key.pem" -out "$TLS_DIR/localhost-cert.pem" >/dev/null 2>&1
rm -f -- "$TLS_DIR/localhost-truststore.p12"
keytool -importcert -noprompt -storetype PKCS12 \
  -alias connector-fixture-localhost -file "$TLS_DIR/localhost-cert.pem" \
  -keystore "$TLS_DIR/localhost-truststore.p12" -storepass "$TRUSTSTORE_PASSWORD" >/dev/null

if jar tf "$ARTIFACT_JAR" | grep -q '^com/dataplatform/plugin/spi/'; then
  echo "fixture JAR 不得打包宿主 SPI 类" >&2
  exit 1
fi
jar tf "$ARTIFACT_JAR" | grep -q '^META-INF/data-platform/plugin.json$'
jar tf "$ARTIFACT_JAR" | grep -q '^com/example/dataplatform/fixture/SignedE2eConnectorPlugin.class$'

STATE_FILE="$OUTPUT_DIR/fixture.env"
{
  printf 'FIXTURE_OUTPUT_DIR=%q\n' "$OUTPUT_DIR"
  printf 'FIXTURE_REPOSITORY_DIR=%q\n' "$REPOSITORY_DIR"
  printf 'FIXTURE_ARTIFACT_JAR=%q\n' "$ARTIFACT_JAR"
  printf 'FIXTURE_ARTIFACT_SHA256=%q\n' "$ARTIFACT_SHA256"
  printf 'FIXTURE_DETACHED_SIGNATURE=%q\n' "$DETACHED_SIGNATURE"
  printf 'FIXTURE_SIGNING_KEY_ID=%q\n' "$KEY_ID"
  printf 'FIXTURE_SIGNING_PUBLIC_KEY_BASE64=%q\n' "$SIGNING_PUBLIC_KEY_BASE64"
  printf 'FIXTURE_SIGNING_PUBLIC_KEY_PEM=%q\n' "$KEY_DIR/ed25519-public.pem"
  printf 'FIXTURE_ACCESS_SIGNING_KEY_RESOURCE=%q\n' "file:$KEY_DIR/ed25519-public.pem"
  printf 'FIXTURE_TLS_CERTIFICATE=%q\n' "$TLS_DIR/localhost-cert.pem"
  printf 'FIXTURE_TLS_PRIVATE_KEY=%q\n' "$TLS_DIR/localhost-key.pem"
  printf 'FIXTURE_TLS_TRUSTSTORE=%q\n' "$TLS_DIR/localhost-truststore.p12"
  printf 'FIXTURE_TLS_TRUSTSTORE_PASSWORD=%q\n' "$TRUSTSTORE_PASSWORD"
  printf 'FIXTURE_JAVA_TLS_OPTIONS=%q\n' \
    "-Djavax.net.ssl.trustStore=$TLS_DIR/localhost-truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=$TRUSTSTORE_PASSWORD"
} > "$STATE_FILE"
chmod 0600 "$STATE_FILE" "$KEY_DIR/ed25519-private.pem" "$TLS_DIR/localhost-key.pem"

echo "$STATE_FILE"
