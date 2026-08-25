#!/usr/bin/env python3
"""Reject mutable GitHub Action refs in repository workflows."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ACTION_RE = re.compile(r"uses:\s*([^\s#]+)")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def main() -> int:
    failures: list[str] = []
    for path in sorted(Path(".github/workflows").glob("*.y*ml")):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            match = ACTION_RE.search(line)
            if not match:
                continue
            action = match.group(1)
            if action.startswith("./"):
                continue
            ref = action.split("@", 1)[-1]
            if not SHA_RE.fullmatch(ref):
                failures.append(f"{path}:{line_number}: action ref is not a full commit SHA")
    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 2
    print("all GitHub Actions are pinned to full commit SHAs")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
