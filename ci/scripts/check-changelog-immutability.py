#!/usr/bin/env python3
"""Compare Liquibase changeset bodies between base and HEAD.

Adding a new changeset necessarily edits the master XML. Existing changesets,
however, are immutable even when the surrounding XML is reformatted.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import xml.etree.ElementTree as ET


def file_at(ref: str, path: str) -> bytes | None:
    result = subprocess.run(["git", "show", f"{ref}:{path}"], capture_output=True, check=False)
    return result.stdout if result.returncode == 0 else None


def changesets(content: bytes, path: str) -> dict[tuple[str, str], bytes]:
    root = ET.fromstring(content)
    result = {}
    for node in root.iter():
        if not node.tag.endswith("changeSet"):
            continue
        key = (node.attrib.get("id", ""), node.attrib.get("author", ""))
        if not all(key):
            raise ValueError(f"{path}: changeset requires id and author")
        # Appending a new sibling changes the previous changeset's indentation
        # tail. That surrounding whitespace is not part of the published
        # changeset body and must not make an immutable-history check fail.
        original_tail = node.tail
        node.tail = None
        result[key] = ET.tostring(node, encoding="utf-8", short_empty_elements=True)
        node.tail = original_tail
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-ref", default="origin/master")
    parser.add_argument("--path", default="sql/changelog/db.changelog-master.xml")
    args = parser.parse_args()
    base = file_at(args.base_ref, args.path)
    if base is None:
        print("base changelog is not present; treating all changesets as new")
        return 0
    try:
        previous = changesets(base, args.path)
        current = changesets(open(args.path, "rb").read(), args.path)
    except (OSError, ET.ParseError, ValueError) as exc:
        print(f"changelog validation error: {exc}", file=sys.stderr)
        return 2
    failures = []
    for key, old_body in previous.items():
        if key not in current:
            failures.append(f"removed changeset {key[0]} by {key[1]}")
        elif current[key] != old_body:
            failures.append(f"modified changeset {key[0]} by {key[1]}")
    if failures:
        for failure in failures:
            print(f"published Liquibase changeset is immutable: {failure}", file=sys.stderr)
        return 2
    print(f"changelog immutability passed: {len(previous)} existing changesets unchanged")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
