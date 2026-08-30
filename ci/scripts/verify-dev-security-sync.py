#!/usr/bin/env python3
"""Verify the selective master-to-dev security sync and CI boundary."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONTRACT_PATH = ROOT / "ci/contracts/dev-security-sync.v1.json"
ACTION_RE = re.compile(r"uses:\s*([^\s@]+)@([0-9a-f]{40})")


def main() -> int:
    contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    errors: list[str] = []

    package = json.loads((ROOT / "data-platform-web/package.json").read_text(encoding="utf-8"))
    package_lock = json.loads((ROOT / "data-platform-web/package-lock.json").read_text(encoding="utf-8"))
    for name, expected in contract["dependencies"]["npmDev"].items():
        actual = package.get("devDependencies", {}).get(name)
        if actual != expected:
            errors.append(f"package.json {name}: expected {expected}, got {actual}")
        locked_root = package_lock.get("packages", {}).get("", {}).get("devDependencies", {}).get(name)
        if locked_root != expected:
            errors.append(f"package-lock root {name}: expected {expected}, got {locked_root}")

    for path, expected in contract["dependencies"]["npmLockPackages"].items():
        actual = package_lock.get("packages", {}).get(path, {}).get("version")
        if actual != expected:
            errors.append(f"package-lock {path}: expected {expected}, got {actual}")
    for path in contract["dependencies"].get("npmLockAbsent", []):
        if path in package_lock.get("packages", {}):
            errors.append(f"package-lock {path}: dependency should be absent after the final master graph update")

    root_pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
    runtime_pom = (ROOT / "data-platform-common-runtime/pom.xml").read_text(encoding="utf-8")
    expected_nimbus = contract["dependencies"]["maven"]["nimbus-jose-jwt"]
    if f"<nimbus-jose-jwt.version>{expected_nimbus}</nimbus-jose-jwt.version>" not in root_pom:
        errors.append(f"pom.xml nimbus-jose-jwt must be {expected_nimbus}")
    expected_spring_kafka = contract["dependencies"]["maven"]["spring-kafka"]
    if f"<spring-kafka.version>{expected_spring_kafka}</spring-kafka.version>" not in root_pom:
        errors.append(f"pom.xml spring-kafka must be {expected_spring_kafka}")
    if not re.search(
        r"<artifactId>spring-kafka</artifactId>\s*<version>\$\{spring-kafka\.version}</version>",
        root_pom,
    ):
        errors.append("pom.xml spring-kafka dependency must use the audited spring-kafka.version property")
    expected_bc = contract["dependencies"]["maven"]["bcprov-jdk18on"]
    bc_pattern = re.compile(
        r"<artifactId>bcprov-jdk18on</artifactId>\s*<version>" + re.escape(expected_bc) + r"</version>"
    )
    if not bc_pattern.search(runtime_pom):
        errors.append(f"data-platform-common-runtime bcprov-jdk18on must be {expected_bc}")

    action_refs: dict[str, list[tuple[Path, str]]] = {}
    for workflow in sorted((ROOT / ".github/workflows").glob("*.y*ml")):
        for action, ref in ACTION_RE.findall(workflow.read_text(encoding="utf-8")):
            action_refs.setdefault(action, []).append((workflow, ref))
    for action, expected in contract["actions"].items():
        occurrences = action_refs.get(action, [])
        if not occurrences:
            errors.append(f"GitHub Action {action} is not referenced")
            continue
        for workflow, actual in occurrences:
            if actual != expected:
                errors.append(f"{workflow.relative_to(ROOT)} {action}: expected {expected}, got {actual}")

    ci_text = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    jobs_section = ci_text.split("\njobs:\n", 1)
    actual_jobs = (
        re.findall(r"^  ([A-Za-z0-9_-]+):\s*$", jobs_section[1], re.MULTILINE)
        if len(jobs_section) == 2
        else []
    )
    expected_jobs = contract["ciBoundary"]["requiredJobs"]
    if actual_jobs != expected_jobs:
        errors.append(f"development CI jobs must be {expected_jobs}, got {actual_jobs}")
    if "needs: [backend, frontend]" not in ci_text:
        errors.append("CI / required-ci must require backend and frontend")
    if "branches: [dev, master]" not in ci_text:
        errors.append("development CI must run for dev and master")

    scheduled_path = ROOT / contract["ciBoundary"]["scheduledWorkflow"]
    if not scheduled_path.is_file():
        errors.append(f"scheduled E2E workflow missing: {scheduled_path.relative_to(ROOT)}")
    else:
        scheduled_text = scheduled_path.read_text(encoding="utf-8")
        if "schedule:" not in scheduled_text or "workflow_dispatch:" not in scheduled_text:
            errors.append("scheduled E2E must support schedule and workflow_dispatch")
        if re.search(r"^\s*(pull_request|push):", scheduled_text, re.MULTILINE):
            errors.append("scheduled E2E must remain non-required and cannot run on push or pull_request")
        if "ref: dev" not in scheduled_text:
            errors.append("scheduled E2E must check out dev because GitHub schedules run from the default branch")

    if errors:
        for error in errors:
            print(f"dev security sync failed: {error}", file=sys.stderr)
        return 2
    print(
        "dev security sync passed: Maven/npm/action pins match the master audit snapshot plus "
        "recorded dev remediations; required CI remains backend/frontend only and E2E is scheduled/manual"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
