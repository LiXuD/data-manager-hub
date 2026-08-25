#!/usr/bin/env python3
"""Validate that a production release version advances SemVer history."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


SEMVER_RE = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")


def parse_version(value: str) -> tuple[int, int, int]:
    match = SEMVER_RE.fullmatch(value)
    if not match:
        raise ValueError(f"invalid SemVer: {value}")
    return tuple(int(part) for part in match.groups())


def latest_semver(tags: list[object]) -> tuple[str, tuple[int, int, int]] | None:
    candidates: list[tuple[str, tuple[int, int, int]]] = []
    for tag in tags:
        if not isinstance(tag, str):
            continue
        try:
            candidates.append((tag, parse_version(tag)))
        except ValueError:
            continue
    return max(candidates, key=lambda item: item[1]) if candidates else None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--previous")
    parser.add_argument("--tags-json", help="JSON array of existing tag names")
    parser.add_argument("--tags-file", type=Path)
    args = parser.parse_args()

    try:
        candidate = parse_version(args.candidate)
    except ValueError as exc:
        print(f"semver check failed: {exc}", file=sys.stderr)
        return 2
    if args.previous and (args.tags_json or args.tags_file):
        print("semver check failed: choose --previous or --tags-json/--tags-file", file=sys.stderr)
        return 64

    previous: tuple[str, tuple[int, int, int]] | None = None
    try:
        if args.previous:
            previous = (args.previous, parse_version(args.previous))
        elif args.tags_json or args.tags_file:
            raw = args.tags_json
            if args.tags_file:
                raw = args.tags_file.read_text(encoding="utf-8")
            tags = json.loads(raw or "[]")
            if not isinstance(tags, list):
                raise ValueError("tags JSON must be an array")
            previous = latest_semver(tags)
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"semver check failed: {exc}", file=sys.stderr)
        return 2

    if previous is not None and candidate <= previous[1]:
        print(
            f"semver check failed: {args.candidate} must be greater than existing {previous[0]}",
            file=sys.stderr,
        )
        return 2
    if previous is None:
        print(f"semver check passed: {args.candidate} is the first release")
    else:
        print(f"semver check passed: {previous[0]} -> {args.candidate}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
