#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${NVD_API_KEY:-}" ]]; then
  if [[ "${ALLOW_UNKEYED_NVD:-false}" == "true" ]]; then
    echo 'NVD_API_KEY is unavailable in the Dependabot pull_request context; continuing with the public NVD endpoint'
    exit 0
  fi
  : "${NVD_API_KEY:?NVD_API_KEY must be provided by the GitHub Actions secret}"
fi

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

status="$(curl --silent --show-error --retry 3 --retry-all-errors --retry-delay 2 \
  --connect-timeout 10 --max-time 30 \
  -H "apiKey: ${NVD_API_KEY}" \
  -o "$response_file" \
  -w '%{http_code}' \
  'https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=1&startIndex=0')" || {
  echo 'NVD API preflight request failed' >&2
  exit 2
}

if [[ "$status" != 200 ]]; then
  echo "NVD API preflight returned HTTP ${status}" >&2
  exit 2
fi

python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)

if payload.get("format") != "NVD_CVE" or not isinstance(payload.get("totalResults"), int):
    raise SystemExit("NVD API preflight response is not a CVE 2.0 payload")

print("NVD API credential preflight passed")
PY
