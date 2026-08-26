#!/usr/bin/env python3
"""Static Dockerfile policy used before a build runner is trusted."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml


FROM = re.compile(r"^\s*FROM\s+(\S+)")
ARG_IMAGE = re.compile(r"^\s*ARG\s+([A-Z0-9_]+)=(\S+)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", nargs="?", default="docker")
    args = parser.parse_args()
    errors = []
    files = sorted(Path(args.directory).glob("*.Dockerfile"))
    if not files:
        errors.append("no Dockerfiles found")
    lock_path = Path("ci/toolchain.lock.yaml")
    lock_images = {}
    if lock_path.exists():
        lock = yaml.safe_load(lock_path.read_text(encoding="utf-8")) or {}
        lock_images = {
            str(image.get("reference")): str(image.get("digest"))
            for image in (lock.get("images") or {}).values()
            if image.get("reference") and image.get("digest")
        }
    for path in files:
        content = path.read_text(encoding="utf-8")
        image_args = {name: value for name, value in ARG_IMAGE.findall(content)}
        for match in FROM.finditer(content):
            image = match.group(1)
            if image.startswith("${") and image.endswith("}"):
                image = image_args.get(image[2:-1], image)
            if "@sha256:" not in image:
                errors.append(f"{path}: base image is not digest pinned: {image}")
            if ":latest" in image:
                errors.append(f"{path}: latest tag is forbidden")
            if "@" in image:
                reference, digest = image.rsplit("@", 1)
                expected = lock_images.get(reference)
                if expected and digest != expected:
                    errors.append(f"{path}: {reference} digest differs from ci/toolchain.lock.yaml")
        if path.name == "java-service.Dockerfile" and "USER 10001:10001" not in content:
            errors.append(f"{path}: Java runtime must run as UID/GID 10001")
        if path.name in {"dbops.Dockerfile", "acceptance.Dockerfile"} and "USER 10001:10001" not in content:
            errors.append(f"{path}: job image must run as UID/GID 10001")
        if path.name == "web.Dockerfile" and "nginx-unprivileged" not in content:
            errors.append(f"{path}: web runtime must be nginx-unprivileged")
    if not Path(".dockerignore").exists():
        errors.append(".dockerignore is required")
    if errors:
        for error in errors:
            print(f"Docker policy error: {error}", file=sys.stderr)
        return 2
    print(f"Dockerfiles passed static policy: {len(files)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
