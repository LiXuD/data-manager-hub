#!/usr/bin/env python3
"""Fail closed if the repository retention contract is weakened."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", default="ci/policy/ghcr-retention.yaml")
    args = parser.parse_args()
    data = yaml.safe_load(Path(args.path).read_text(encoding="utf-8")) or {}
    errors = []
    if data.get("schemaVersion") != 1 or data.get("registry") != "ghcr.io":
        errors.append("schemaVersion and registry must be fixed")
    required_rules = {
        "never-delete-digest-referenced-by-release-record",
        "never-delete-build-manifest-referenced-by-deployment-receipt",
        "never-repoint-semver-tag",
        "audit-before-any-retention-change",
    }
    actual_rules = set(data.get("rules") or [])
    missing_rules = sorted(required_rules - actual_rules)
    if missing_rules:
        errors.append("retention rules are missing: " + ", ".join(missing_rules))
    artifacts = data.get("artifacts") or {}
    for name in (
        "releaseDigests",
        "productionBuildManifests",
        "productionSemverAliases",
        "candidateDigests",
        "sbomAndProvenance",
    ):
        policy = artifacts.get(name) or {}
        if policy.get("delete") is not False:
            errors.append(f"{name}.delete must remain false")
        retention = int(policy.get("retentionDays", -1))
        minimum = 0 if name in ("releaseDigests", "productionBuildManifests", "productionSemverAliases") else 365
        if retention < minimum:
            errors.append(f"{name}.retentionDays must be >= {minimum}")
    if errors:
        for error in errors:
            print(f"GHCR retention policy error: {error}", file=sys.stderr)
        return 2
    print("GHCR retention policy passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
