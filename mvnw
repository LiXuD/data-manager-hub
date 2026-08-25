#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES="$ROOT_DIR/.mvn/wrapper/maven-wrapper.properties"
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES")
DIST_SHA=$(sed -n 's/^distributionSha256Sum=//p' "$PROPERTIES")
MAVEN_VERSION=$(printf '%s' "$DIST_URL" | sed -n 's|.*/apache-maven-\([^/]*\)-bin.tar.gz|\1|p')

cache_root=${DMH_MAVEN_CACHE:-${MAVEN_USER_HOME:-${HOME:-/tmp}/.m2}/wrapper/dists}
archive="$cache_root/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
install_dir="$cache_root/apache-maven-${MAVEN_VERSION}"
mkdir -p "$cache_root"
if [ ! -s "$archive" ]; then
  command -v curl >/dev/null 2>&1 || { echo 'curl is required to bootstrap Maven' >&2; exit 1; }
  curl --fail --location --silent --show-error "$DIST_URL" -o "$archive"
fi
# Verify the cached archive on every invocation and re-extract it instead of
# trusting a pre-existing install directory on a persistent runner.  This
# keeps a poisoned Maven binary from bypassing the wrapper checksum contract.
if command -v shasum >/dev/null 2>&1; then
  actual_sha=$(shasum -a 256 "$archive" | awk '{print $1}')
else
  actual_sha=$(sha256sum "$archive" | awk '{print $1}')
fi
[ "$actual_sha" = "$DIST_SHA" ] || { echo 'Maven distribution checksum mismatch' >&2; exit 2; }
temp_dir="$(mktemp -d "$cache_root/.extract.XXXXXX")"
trap 'rm -rf "$temp_dir"' EXIT
tar -xzf "$archive" -C "$temp_dir"
rm -rf "$install_dir"
mv "$temp_dir/apache-maven-${MAVEN_VERSION}" "$install_dir"
trap - EXIT
rmdir "$temp_dir"
exec "$install_dir/bin/mvn" "$@"
