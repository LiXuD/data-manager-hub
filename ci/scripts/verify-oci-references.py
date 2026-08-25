#!/usr/bin/env python3
"""Verify that a deployment image set exactly matches a Build Manifest."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest")
    parser.add_argument("actual")
    args = parser.parse_args()
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    actual = json.loads(Path(args.actual).read_text(encoding="utf-8"))
    expected = {name: value["reference"] for name, value in manifest["spec"]["images"].items()}
    failures = []
    for name, reference in expected.items():
        if actual.get(name) != reference:
            failures.append(f"{name}: expected {reference}, got {actual.get(name)}")
    if set(actual) != set(expected):
        failures.append("actual image set does not equal manifest image set")
    if failures:
        for failure in failures:
            print(f"OCI reference error: {failure}", file=sys.stderr)
        return 2
    print("OCI references match manifest")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
