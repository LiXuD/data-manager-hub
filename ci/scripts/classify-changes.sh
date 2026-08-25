#!/usr/bin/env bash
set -euo pipefail

base_ref="${BASE_REF:-origin/master}"
classification_file="${CLASSIFICATION_FILE:-}"

resolve_commit_ref() {
  local ref="$1"
  if [[ "$ref" == "HEAD^" ]]; then
    git rev-parse --verify HEAD^ 2>/dev/null || true
  else
    git rev-parse --verify "${ref}^{commit}" 2>/dev/null || true
  fi
}

if [[ -n "$classification_file" ]]; then
  [[ -d "$(dirname "$classification_file")" ]] || {
    echo "classification file parent directory does not exist: $classification_file" >&2
    exit 64
  }
  : > "$classification_file"
fi
if [[ "${GITHUB_EVENT_NAME:-}" == push && "${GITHUB_EVENT_BEFORE:-}" =~ ^[0-9a-f]{40}$ && "${GITHUB_EVENT_BEFORE}" != 0000000000000000000000000000000000000000 ]]; then
  base_ref="$GITHUB_EVENT_BEFORE"
fi
base_commit="$(resolve_commit_ref "$base_ref")"
if [[ -z "$base_commit" ]]; then
  base_ref="${GITHUB_BASE_REF:+origin/${GITHUB_BASE_REF}}"
  base_commit="$(resolve_commit_ref "$base_ref")"
fi
if [[ -z "$base_commit" ]]; then
  base_ref="HEAD^"
  base_commit="$(resolve_commit_ref "$base_ref")"
fi
if [[ -z "$base_commit" ]]; then
  echo 'unable to determine a valid commit base for change classification; refusing to classify fail-open' >&2
  exit 2
fi
head_commit="$(git rev-parse --verify HEAD^{commit})"
if [[ "$base_commit" == "$head_commit" ]]; then
  base_ref="HEAD^"
  base_commit="$(resolve_commit_ref "$base_ref")"
  if [[ -z "$base_commit" ]]; then
    echo 'HEAD has no parent commit; refusing to classify fail-open' >&2
    exit 2
  fi
fi

backend=false
frontend=false
migration=false
security=false
deployability=false
plugin=false
external_plugin=false
docs=false
full=false
source_change=false
unknown=false
while IFS= read -r path; do
  [[ -n "$path" ]] || continue
  path_is_doc=false
  case "$path" in
    data-platform-web/package.json|data-platform-web/package-lock.json)
      frontend=true; security=true; deployability=true ;;
    data-platform-web/*)
      frontend=true; security=true; deployability=true ;;
    sql/*|migrate-db.sh|verify-db-bootstrap.sh|verify-v048-routing.sh|verify-v049-connector-product-spec.sh|verify-v050-generic-http.sh)
      migration=true ;;
    data-platform-plugin-spi/*|data-platform-plugin-testkit/*|data-platform-common-runtime/*|data-platform-access/*|data-platform-masterdata/*)
      plugin=true; backend=true; security=true; deployability=true ;;
    plugins/*|plugin-artifacts/*|plugin-receipts/*|*.jar|*.sig)
      plugin=true; external_plugin=true; security=true; deployability=true ;;
    */pom.xml|pom.xml)
      backend=true; security=true; deployability=true ;;
    *.java|data-platform-*)
      backend=true; security=true; deployability=true ;;
    ci/policy/coverage-baseline.json)
      # A baseline change is a claim about both backend and frontend test
      # evidence.  Run both coverage jobs so a PR cannot raise the JSON
      # numbers while avoiding the reports that prove them.
      backend=true; frontend=true; security=true; deployability=true ;;
    docker/*|Dockerfile*|deploy/*|nacos-config/*|.github/*|ci/*|arch-scan.sh|publish-nacos-config.sh)
      security=true; deployability=true ;;
    docs/*|README.md|PENDING_TASKS.md|*.md)
      docs=true
      path_is_doc=true ;;
    *)
      unknown=true ;;
  esac
  if [[ "$path_is_doc" != true ]]; then
    source_change=true
  fi
done < <(git diff --name-only "$base_ref...HEAD")

if [[ "$unknown" == true ]]; then
  full=true
  backend=true; frontend=true; migration=true
  security=true; deployability=true; plugin=true
fi
if [[ "$backend" == true || "$frontend" == true || "$migration" == true || "$plugin" == true ]]; then
  deployability=true
fi

for key in backend frontend migration security deployability plugin external_plugin docs full source_change; do
  value="${!key}"
  echo "${key}=${value}"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "${key}=${value}" >> "$GITHUB_OUTPUT"
  fi
  if [[ -n "$classification_file" ]]; then
    printf '%s=%s\n' "$key" "$value" >> "$classification_file"
  fi
done
echo "base_ref=$base_ref"
source_sha="$(git rev-parse HEAD)"
echo "source_sha=$source_sha"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "base_ref=$base_ref" >> "$GITHUB_OUTPUT"
  echo "source_sha=$source_sha" >> "$GITHUB_OUTPUT"
fi
if [[ -n "$classification_file" ]]; then
  printf 'base_ref=%s\nsource_sha=%s\n' "$base_ref" "$source_sha" >> "$classification_file"
fi
