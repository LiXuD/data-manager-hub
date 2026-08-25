#!/usr/bin/env python3
"""Create a deterministic SHA-256 for a sorted set of files."""

from __future__ import annotations

import argparse
import hashlib
import subprocess
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", help="files or directories")
    parser.add_argument("--git-tracked", action="store_true", help="hash tracked files instead of filesystem metadata")
    args = parser.parse_args()
    files: list[Path] = []
    if args.git_tracked:
        tracked = subprocess.run(
            ["git", "ls-files", "-z"], check=True, capture_output=True
        ).stdout.split(b"\0")
        files.extend(
            Path(item.decode("utf-8"))
            for item in tracked
            if item and Path(item.decode("utf-8")).is_file()
        )
    if not args.git_tracked:
        for raw in args.paths:
            path = Path(raw)
            if path.is_dir():
                files.extend(item for item in path.rglob("*") if item.is_file())
            elif path.is_file():
                files.append(path)
    digest = hashlib.sha256()
    for path in sorted(set(files)):
        relative = path.as_posix().encode("utf-8")
        content = path.read_bytes()
        digest.update(len(relative).to_bytes(8, "big"))
        digest.update(relative)
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    print("sha256:" + digest.hexdigest())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
