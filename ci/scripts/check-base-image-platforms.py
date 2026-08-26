#!/usr/bin/env python3
"""Verify locked base-image digests are OCI indexes for both release platforms."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

import yaml


REQUIRED_PLATFORMS = {"linux/amd64", "linux/arm64"}


def inspect(reference: str, digest: str) -> set[str]:
    image = f"{reference}@{digest}"
    result = subprocess.run(
        ["docker", "buildx", "imagetools", "inspect", "--format", "{{json .}}", image],
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"cannot inspect {image}: {result.stderr.strip()}")
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"imagetools returned invalid JSON for {image}: {exc}") from exc
    manifests = payload.get("manifest", {}).get("manifests", [])
    platforms: set[str] = set()
    for item in manifests:
        platform = item.get("platform") or {}
        os_name = platform.get("os")
        architecture = platform.get("architecture")
        if os_name and architecture:
            platforms.add(f"{os_name}/{architecture}")
    return platforms


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lock", default="ci/toolchain.lock.yaml")
    args = parser.parse_args()
    lock = yaml.safe_load(Path(args.lock).read_text(encoding="utf-8")) or {}
    errors: list[str] = []
    for name, image in sorted((lock.get("images") or {}).items()):
        reference = str(image.get("reference", ""))
        digest = str(image.get("digest", ""))
        if not reference or not digest:
            errors.append(f"{name}: missing reference or digest")
            continue
        try:
            platforms = inspect(reference, digest)
        except RuntimeError as exc:
            errors.append(f"{name}: {exc}")
            continue
        missing = sorted(REQUIRED_PLATFORMS - platforms)
        if missing:
            errors.append(f"{name}: digest is missing platforms: {', '.join(missing)}")
        else:
            print(f"{name}: {reference}@{digest} -> {', '.join(sorted(REQUIRED_PLATFORMS))}")
    if errors:
        for error in errors:
            print(f"base image platform check failed: {error}", file=sys.stderr)
        return 2
    print("base image platform check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
