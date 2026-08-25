#!/usr/bin/env bash
set -euo pipefail

# Build manifests carry the migration mode so a promotion job can make a
# deterministic decision without re-interpreting a mutable branch. The CI
# immutability guard must pass before a BLOCKED result can ever be published.
base_ref="${BASE_REF:-origin/master}"
head_ref="${HEAD_REF:-HEAD}"

if [[ "$base_ref" == "HEAD^" ]]; then
  resolved_base_ref="$(git rev-parse --verify HEAD^ 2>/dev/null || true)"
else
  resolved_base_ref="$(git rev-parse --verify "${base_ref}^{commit}" 2>/dev/null || true)"
fi
if [[ -z "$resolved_base_ref" ]]; then
  resolved_base_ref="$(git rev-parse --verify HEAD^ 2>/dev/null || true)"
fi
if [[ -z "$resolved_base_ref" ]]; then
  echo 'unable to determine a valid commit base for migration mode; refusing to classify fail-open' >&2
  exit 2
fi
base_ref="$resolved_base_ref"

mode=NONE
while IFS= read -r -d '' status; do
  case "$status" in
    R*|C*)
      IFS= read -r -d '' old_path || true
      IFS= read -r -d '' new_path || true
      paths=("$old_path" "$new_path")
      ;;
    *)
      IFS= read -r -d '' path || true
      paths=("$path")
      ;;
  esac
  for path in "${paths[@]}"; do
    case "$path" in
      sql/migrations/V*.sql|sql/rollbacks/U*.sql)
        case "$status" in
          A) [[ "$mode" == NONE ]] && mode=FORWARD ;;
          *) mode=BLOCKED ;;
        esac
        ;;
    esac
  done
done < <(git diff --name-status -z "$base_ref...$head_ref" -- sql/migrations sql/rollbacks sql/changelog)

while IFS= read -r path; do
  [[ "$path" == *.xml ]] || continue
  if [[ ! -f "$path" ]]; then
    mode=BLOCKED
    continue
  fi
  if python3 "$(dirname "$0")/check-changelog-immutability.py" --base-ref "$base_ref" --path "$path" >/dev/null; then
    [[ "$mode" == NONE ]] && mode=FORWARD
  else
    mode=BLOCKED
  fi
done < <(git diff --name-only "$base_ref...$head_ref" -- sql/changelog)

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "migration_mode=$mode" >> "$GITHUB_OUTPUT"
fi
echo "$mode"

if [[ "$mode" == BLOCKED ]]; then
  echo "migration mode BLOCKED: a published changelog, migration, or rollback was modified" >&2
  exit 2
fi
