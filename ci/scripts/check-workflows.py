#!/usr/bin/env python3
"""Parse every workflow and syntax-check all inline shell blocks.

This is deliberately dependency-light so the required CI job can validate its
own workflow files before any deployment-capable job runs. The required CI job
also runs a digest-locked full actionlint container; this script covers the
repository invariants that must not depend on a downloaded binary.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path
from typing import Any

import yaml


SHA_RE = re.compile(r"^[0-9a-f]{40}$")


class UniqueKeyLoader(yaml.SafeLoader):
    """Reject duplicate YAML keys instead of silently keeping the last value.

    A duplicate ``permissions``/``if``/``env`` key can otherwise change the
    effective GitHub Actions contract while the review only sees the first
    occurrence.  Fail closed before actionlint or any trusted job is run.
    """


def construct_unique_mapping(loader: UniqueKeyLoader, node: yaml.MappingNode, deep: bool = False) -> dict:
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise yaml.constructor.ConstructorError(
                "while constructing a mapping",
                node.start_mark,
                f"found duplicate key: {key}",
                key_node.start_mark,
            )
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


UniqueKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
    construct_unique_mapping,
)


def walk(value: Any, path: Path, run_blocks: list[tuple[Path, str]], action_refs: list[tuple[Path, str]]) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "run":
                if isinstance(child, dict):
                    # Job-level defaults.run is a mapping, not a shell step.
                    pass
                elif not isinstance(child, str) or not child.strip():
                    raise ValueError(f"{path}: run must be a non-empty string")
                else:
                    run_blocks.append((path, child))
            elif key == "uses":
                if not isinstance(child, str):
                    raise ValueError(f"{path}: uses must be a string")
                action_refs.append((path, child))
            walk(child, path, run_blocks, action_refs)
    elif isinstance(value, list):
        for child in value:
            walk(child, path, run_blocks, action_refs)


def contains_self_hosted_runner(value: Any) -> bool:
    """Return whether a workflow schedules any job on a self-hosted runner."""

    if isinstance(value, dict):
        for key, child in value.items():
            if key == "runs-on":
                candidates = child if isinstance(child, list) else [child]
                for candidate in candidates:
                    if isinstance(candidate, str) and "self-hosted" in candidate.lower():
                        return True
            if contains_self_hosted_runner(child):
                return True
    elif isinstance(value, list):
        return any(contains_self_hosted_runner(child) for child in value)
    return False


def main() -> int:
    workflows = sorted(Path(".github/workflows").glob("*.y*ml"))
    errors: list[str] = []
    run_blocks: list[tuple[Path, str]] = []
    action_refs: list[tuple[Path, str]] = []
    if not workflows:
        errors.append("no workflow files found")
    for path in workflows:
        try:
            document = yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueKeyLoader) or {}
        except (OSError, yaml.YAMLError) as exc:
            errors.append(f"{path}: invalid YAML: {exc}")
            continue
        if not isinstance(document, dict):
            errors.append(f"{path}: workflow root must be a mapping")
            continue
        # PyYAML's YAML 1.1 loader represents the key `on` as True. Check the
        # remaining required keys without depending on that implementation
        # detail, while still rejecting malformed workflow roots.
        if "name" not in document:
            errors.append(f"{path}: name is required")
        if "on" not in document and True not in document:
            errors.append(f"{path}: on trigger is required")
        triggers = document.get("on", document.get(True, {}))
        if isinstance(triggers, dict) and "pull_request_target" in triggers:
            errors.append(f"{path}: pull_request_target is forbidden; fork PRs must never reach trusted context")
        if isinstance(triggers, dict) and any(
            trigger in triggers for trigger in ("pull_request", "pull_request_review", "pull_request_review_comment")
        ) and contains_self_hosted_runner(document.get("jobs", {})):
            errors.append(f"{path}: pull-request workflows must not schedule self-hosted runners")
        if "jobs" not in document or not isinstance(document["jobs"], dict):
            errors.append(f"{path}: jobs mapping is required")
        try:
            walk(document, path, run_blocks, action_refs)
        except ValueError as exc:
            errors.append(str(exc))

    for path, block in run_blocks:
        result = subprocess.run(["bash", "-n"], input=block, text=True, capture_output=True, check=False)
        if result.returncode:
            errors.append(f"{path}: inline shell syntax error: {result.stderr.strip()}")

    for path, reference in action_refs:
        if reference.startswith("./"):
            continue
        if "@" not in reference or not SHA_RE.fullmatch(reference.rsplit("@", 1)[1]):
            errors.append(f"{path}: action must be pinned to a full 40-character commit SHA: {reference}")

    if errors:
        for error in errors:
            print(f"workflow validation failed: {error}", file=sys.stderr)
        return 2
    print(f"workflows valid: {len(workflows)} files, {len(run_blocks)} run blocks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
