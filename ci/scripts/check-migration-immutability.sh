#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
base_ref="${BASE_REF:-origin/master}"
if [[ "$base_ref" == "HEAD^" ]]; then
  resolved_base_ref="$(git rev-parse --verify HEAD^ 2>/dev/null || true)"
else
  resolved_base_ref="$(git rev-parse --verify "${base_ref}^{commit}" 2>/dev/null || true)"
fi
if [[ -z "$resolved_base_ref" ]]; then
  resolved_base_ref="$(git rev-parse --verify HEAD^ 2>/dev/null || true)"
fi
if [[ -z "$resolved_base_ref" ]]; then
  echo 'unable to determine a valid commit base for migration immutability; refusing to check fail-open' >&2
  exit 2
fi
base_ref="$resolved_base_ref"
errors=0
immutable_from="${IMMUTABLE_FROM_VERSION:-51}"

is_protected_path() {
  local path="$1"
  case "$path" in
    sql/migrations/V*.sql|sql/rollbacks/U*.sql)
      number="${path##*/}"
      number="${number#V}"
      number="${number#U}"
      number="${number%%__*}"
      [[ "$number" =~ ^[0-9]+$ && $((10#$number)) -ge "$immutable_from" ]]
      ;;
    sql/changelog/*)
      # The master XML must change when a new changeset is appended. Existing
      # changeset bodies are compared by the XML guard below instead.
      return 1
      ;;
    *) return 1 ;;
  esac
}

# Use NUL-delimited output so paths containing whitespace cannot evade the
# guard. For a rename/copy Git emits two paths; both the old and new path are
# checked because a published changeset cannot be moved to hide its checksum.
while IFS= read -r -d '' status; do
  case "$status" in
    R*|C*)
      IFS= read -r -d '' old_path || true
      IFS= read -r -d '' new_path || true
      for path in "$old_path" "$new_path"; do
        if is_protected_path "$path"; then
          echo "已发布迁移不可修改/删除/重命名: $status $path" >&2
          errors=$((errors + 1))
        fi
      done
      ;;
    M|D)
      IFS= read -r -d '' path || true
      if is_protected_path "$path"; then
        echo "已发布迁移不可修改/删除/重命名: $status $path" >&2
        errors=$((errors + 1))
      fi
      ;;
    *)
      IFS= read -r -d '' _path || true
      ;;
  esac
done < <(git diff --name-status -z "$base_ref...HEAD" -- sql/migrations sql/rollbacks sql/changelog)

python3 "$SCRIPT_DIR/check-changelog-immutability.py" --base-ref "$base_ref"
while IFS= read -r path; do
  [[ -n "$path" ]] || continue
  python3 "$SCRIPT_DIR/check-changelog-immutability.py" --base-ref "$base_ref" --path "$path"
done < <(git ls-tree -r --name-only "$base_ref" -- sql/changelog | grep -E '\.xml$' || true)
while IFS= read -r path; do
  [[ -n "$path" ]] || continue
  if [[ -f "$path" ]]; then
    python3 "$SCRIPT_DIR/check-changelog-immutability.py" --base-ref "$base_ref" --path "$path"
  else
    echo "changed changelog was removed: $path" >&2
    errors=$((errors + 1))
  fi
done < <(git diff --name-only "$base_ref...HEAD" -- sql/changelog | grep -E '\.xml$' || true)

./migrate-db.sh check-numbering
python3 "$SCRIPT_DIR/check-changelog-references.py" --base-ref "$base_ref"

if [[ "$errors" -ne 0 ]]; then
  exit 2
fi
echo "migration immutability check passed"
