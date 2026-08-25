#!/usr/bin/env python3
"""Validate the repository-owned reproducible toolchain contract."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml


HEX64 = re.compile(r"^[0-9a-f]{64}$")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", default="ci/toolchain.lock.yaml")
    args = parser.parse_args()
    data = yaml.safe_load(Path(args.path).read_text(encoding="utf-8")) or {}
    errors = []
    if data.get("schemaVersion") != 1:
        errors.append("schemaVersion must be 1")
    if data.get("java", {}).get("version") != "21":
        errors.append("Java 21 must remain locked")
    if data.get("postgres", {}).get("version") != "16":
        errors.append("PostgreSQL 16 must remain locked")
    if data.get("maven", {}).get("version") != "3.9.15":
        errors.append("Maven 3.9.15 must remain locked")
    if data.get("node", {}).get("version") != "22.19.0":
        errors.append("Node 22.19.0 must remain locked")
    if data.get("npm", {}).get("version") != "10.9.3":
        errors.append("npm 10.9.3 must remain locked")
    if data.get("helm", {}).get("version") != "3.19.0":
        errors.append("Helm 3.19.0 must remain locked")
    if data.get("kubectl", {}).get("version") != "1.33.0":
        errors.append("kubectl 1.33.0 must remain locked")
    actionlint = data.get("tools", {}).get("actionlint", {})
    if actionlint.get("version") != "1.7.12":
        errors.append("actionlint 1.7.12 must remain locked")
    if actionlint.get("image") != "rhysd/actionlint":
        errors.append("actionlint image must remain rhysd/actionlint")
    actionlint_digest = str(actionlint.get("digest", ""))
    if not actionlint_digest.startswith("sha256:") or not HEX64.fullmatch(actionlint_digest.removeprefix("sha256:")):
        errors.append("tools.actionlint.digest must be sha256:<64 hex>")
    promtool = data.get("tools", {}).get("promtool", {})
    if promtool.get("version") != "2.54.0":
        errors.append("promtool 2.54.0 must remain locked")
    if promtool.get("image") != "prom/prometheus":
        errors.append("promtool image must remain prom/prometheus")
    promtool_digest = str(promtool.get("digest", ""))
    if not promtool_digest.startswith("sha256:") or not HEX64.fullmatch(promtool_digest.removeprefix("sha256:")):
        errors.append("tools.promtool.digest must be sha256:<64 hex>")
    wrapper_sha = str(data.get("maven", {}).get("wrapperDistributionSha256", ""))
    if not HEX64.fullmatch(wrapper_sha):
        errors.append("maven.wrapperDistributionSha256 must be a 64-character SHA-256")
    wrapper_properties = Path(".mvn/wrapper/maven-wrapper.properties")
    if wrapper_properties.exists():
        properties = wrapper_properties.read_text(encoding="utf-8")
        match = re.search(r"^distributionSha256Sum=(\S+)$", properties, re.MULTILINE)
        if not match or match.group(1) != wrapper_sha:
            errors.append(".mvn/wrapper/maven-wrapper.properties checksum does not match toolchain lock")
        maven_version = str(data.get("maven", {}).get("version", ""))
        if maven_version and f"apache-maven-{maven_version}-bin.tar.gz" not in properties:
            errors.append(".mvn/wrapper/maven-wrapper.properties version does not match toolchain lock")
    for name, image in (data.get("images") or {}).items():
        reference = str(image.get("reference", ""))
        digest = str(image.get("digest", ""))
        if "@" in reference or not reference:
            errors.append(f"{name}.reference must be a registry reference without an embedded tag digest")
        if not digest.startswith("sha256:") or not HEX64.fullmatch(digest.removeprefix("sha256:")):
            errors.append(f"{name}.digest must be sha256:<64 hex>")
    if errors:
        for error in errors:
            print(f"toolchain lock error: {error}", file=sys.stderr)
        return 2
    print("toolchain lock passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
