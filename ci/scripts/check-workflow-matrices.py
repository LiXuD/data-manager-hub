#!/usr/bin/env python3
"""Validate the production image matrices when the production pipeline is enabled.

The runtime contract is the single source of truth for deployable components.
The production no-push CI build and the GHCR release build must enumerate
exactly the same set.  This check is intentionally not called by the current
development CI, which only verifies Java/Web compilation and tests.
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "ci/contracts/runtime-contract.v1.yaml"
WORKFLOWS = {
    ROOT / ".github/workflows/ci.yml": "docker",
    ROOT / ".github/workflows/build-release.yml": "build",
}


def load_yaml(path: Path) -> dict[str, Any]:
    value = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if not isinstance(value, dict):
        raise ValueError(f"{path}: YAML root must be an object")
    return value


def matrix_components(path: Path, job_name: str) -> tuple[set[str], list[str]]:
    workflow = load_yaml(path)
    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict) or not isinstance(jobs.get(job_name), dict):
        raise ValueError(f"{path}: missing jobs.{job_name}")
    matrix = jobs[job_name].get("strategy", {}).get("matrix", {})
    includes = matrix.get("include") if isinstance(matrix, dict) else None
    if not isinstance(includes, list) or not includes:
        raise ValueError(f"{path}: jobs.{job_name}.strategy.matrix.include must be a non-empty list")
    components: set[str] = set()
    errors: list[str] = []
    for index, item in enumerate(includes):
        if not isinstance(item, dict) or not isinstance(item.get("component"), str):
            errors.append(f"{path}: matrix entry {index} must declare a component")
            continue
        component = item["component"]
        if component in components:
            errors.append(f"{path}: duplicate matrix component {component}")
        components.add(component)
        dockerfile = item.get("dockerfile")
        if not isinstance(dockerfile, str) or not dockerfile:
            errors.append(f"{path}: matrix component {component} must declare dockerfile")
        elif not (ROOT / dockerfile).is_file():
            errors.append(f"{path}: matrix component {component} references missing {dockerfile}")
    return components, errors


def main() -> int:
    errors: list[str] = []
    try:
        contract = load_yaml(CONTRACT)
    except (OSError, ValueError, yaml.YAMLError) as exc:
        print(f"workflow matrix validation failed: {exc}", file=sys.stderr)
        return 2
    components = contract.get("components")
    expected = set(components) if isinstance(components, dict) else set()
    if expected != {"gateway", "masterdata", "access", "billing", "identity", "governance", "web", "dbops", "acceptance"}:
        errors.append("runtime contract must define exactly the nine release components")
    for path, job_name in WORKFLOWS.items():
        try:
            actual, matrix_errors = matrix_components(path, job_name)
        except (OSError, ValueError, yaml.YAMLError) as exc:
            errors.append(str(exc))
            continue
        errors.extend(matrix_errors)
        if actual != expected:
            errors.append(
                f"{path}: jobs.{job_name} matrix mismatch; "
                f"missing={sorted(expected - actual)} unexpected={sorted(actual - expected)}"
            )
    if errors:
        for error in errors:
            print(f"workflow matrix validation failed: {error}", file=sys.stderr)
        return 2
    print(f"workflow image matrices match runtime contract: {len(expected)} components")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
