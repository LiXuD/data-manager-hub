#!/usr/bin/env python3
"""Ensure newly added SQL migration files are indexed by a Liquibase changelog."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def added_sql_files(root: Path, base_ref: str) -> list[Path]:
    result = subprocess.run(
        [
            "git",
            "diff",
            "--name-only",
            "--diff-filter=ACR",
            f"{base_ref}...HEAD",
            "--",
            "sql/migrations",
            "sql/rollbacks",
        ],
        cwd=root,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"cannot inspect changes against {base_ref}")
    return [root / line for line in result.stdout.splitlines() if line.endswith(".sql")]


def referenced_basenames(root: Path) -> set[str]:
    references: set[str] = set()
    changelog_root = root / "sql" / "changelog"
    for path in sorted(changelog_root.rglob("*.xml")):
        try:
            document = ET.parse(path)
        except (OSError, ET.ParseError) as exc:
            raise RuntimeError(f"invalid changelog {path}: {exc}") from exc
        for element in document.getroot().iter():
            for attribute in ("file", "path"):
                value = element.attrib.get(attribute)
                if value:
                    references.add(Path(value).name)
    return references


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def migration_changesets(root: Path) -> dict[str, ET.Element]:
    """Map each SQL migration basename to its containing changeset."""

    result: dict[str, ET.Element] = {}
    changelog_root = root / "sql" / "changelog"
    for path in sorted(changelog_root.rglob("*.xml")):
        try:
            document = ET.parse(path)
        except (OSError, ET.ParseError) as exc:
            raise RuntimeError(f"invalid changelog {path}: {exc}") from exc
        for changeset in document.getroot().iter():
            if local_name(changeset.tag) != "changeSet":
                continue
            for element in changeset.iter():
                if local_name(element.tag) != "sqlFile":
                    continue
                value = element.attrib.get("path")
                if value:
                    basename = Path(value).name
                    if re.fullmatch(r"V[0-9]+__.+\.sql", basename):
                        result[basename] = changeset
    return result


def verify_new_migration_contract(root: Path, changed: list[Path]) -> list[str]:
    """Require every new V migration to have an indexed, explicit rollback."""

    errors: list[str] = []
    changesets = migration_changesets(root)
    for path in changed:
        match = re.fullmatch(r"V([0-9]+)__.+\.sql", path.name)
        if not match:
            continue
        relative = path.relative_to(root)
        rollback_candidates = sorted((root / "sql" / "rollbacks").glob(f"U{match.group(1)}__*.sql"))
        if not rollback_candidates:
            errors.append(f"new migration {relative} requires sql/rollbacks/U{match.group(1)}__*.sql")
            continue
        changeset = changesets.get(path.name)
        if changeset is None:
            # The indexing error is emitted by the existing basename check.
            continue
        rollback_nodes = [node for node in changeset if local_name(node.tag) == "rollback"]
        rollback_paths = {
            Path(element.attrib["path"]).name
            for node in rollback_nodes
            for element in node.iter()
            if local_name(element.tag) == "sqlFile" and element.attrib.get("path")
        }
        candidate_names = {candidate.name for candidate in rollback_candidates}
        if not rollback_nodes or not rollback_paths.intersection(candidate_names):
            expected = ", ".join(sorted(candidate_names))
            errors.append(
                f"new migration {relative} must have an explicit <rollback> referencing one of: {expected}"
            )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-ref", default="origin/master")
    parser.add_argument("--root", default=".")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    try:
        changed = added_sql_files(root, args.base_ref)
        references = referenced_basenames(root)
        migration_contract_errors = verify_new_migration_contract(
            root, [path for path in changed if path.parent.name == "migrations"]
        )
    except (OSError, RuntimeError) as exc:
        print(f"changelog reference validation failed: {exc}", file=sys.stderr)
        return 2
    failures = []
    for path in changed:
        name = path.name
        if not path.is_file():
            failures.append(f"new migration file is missing from the worktree: {path.relative_to(root)}")
        elif name not in references:
            failures.append(f"new SQL file is not indexed by a Liquibase changelog: {path.relative_to(root)}")
    failures.extend(migration_contract_errors)
    if failures:
        for failure in failures:
            print(f"changelog reference validation failed: {failure}", file=sys.stderr)
        return 2
    print(f"changelog reference validation passed: {len(changed)} new SQL files indexed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
