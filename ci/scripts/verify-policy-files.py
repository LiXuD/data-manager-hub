#!/usr/bin/env python3
"""Validate protected CI policy files before any expensive job runs."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import subprocess
import sys
from pathlib import Path

import yaml


REQUIRED_CODEOWNER_PATTERNS = {
    "/.github/",
    "/.mvn/",
    "/pom.xml",
    "/mvnw",
    "/ci/",
    "/deploy/",
    "/docker/",
    "/nacos-config/",
    "/observability/",
    "/sql/changelog/",
    "/sql/migrations/",
    "/sql/rollbacks/",
    "/migrate-db.sh",
    "/publish-nacos-config.sh",
    "/data-platform-web/package.json",
    "/data-platform-web/package-lock.json",
}


def load_yaml(path: Path) -> dict:
    value = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a mapping")
    return value


def parse_date(value: object, field: str) -> dt.date:
    if isinstance(value, dt.datetime):
        return value.date()
    if isinstance(value, dt.date):
        return value
    if not isinstance(value, str):
        raise ValueError(f"{field} must be an ISO date")
    try:
        return dt.date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError(f"{field} must be an ISO date") from exc


def parse_percentage(value: object, field: str) -> float:
    """Reject non-finite or out-of-range coverage claims."""

    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{field} must be a number")
    parsed = float(value)
    if not math.isfinite(parsed) or not 0.0 <= parsed <= 100.0:
        raise ValueError(f"{field} must be finite and between 0 and 100")
    return parsed


def verify_waivers(path: Path) -> list[str]:
    errors: list[str] = []
    data = load_yaml(path)
    if data.get("schemaVersion") != 1:
        errors.append(f"{path}: schemaVersion must be 1")
    waivers = data.get("waivers", [])
    if not isinstance(waivers, list):
        return [f"{path}: waivers must be a list"]
    today = dt.date.today()
    for index, waiver in enumerate(waivers):
        prefix = f"{path} waivers[{index}]"
        if not isinstance(waiver, dict):
            errors.append(f"{prefix} must be an object")
            continue
        for field in ("id", "owner", "reason", "createdAt", "expiresAt"):
            if not waiver.get(field):
                errors.append(f"{prefix}: missing {field}")
        try:
            created = parse_date(waiver.get("createdAt"), f"{prefix}.createdAt")
            expires = parse_date(waiver.get("expiresAt"), f"{prefix}.expiresAt")
            if created > today:
                errors.append(f"{prefix}: createdAt cannot be in the future")
            if expires <= today:
                errors.append(f"{prefix}: waiver is expired")
            if expires > created + dt.timedelta(days=30):
                errors.append(f"{prefix}: expiry exceeds the 30-day maximum")
        except ValueError as exc:
            errors.append(str(exc))
    return errors


def git_show(ref: str, path: str) -> dict | None:
    result = subprocess.run(
        ["git", "show", f"{ref}:{path}"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError:
        return None


def verify_coverage_baseline(path: Path, base_ref: str) -> list[str]:
    errors: list[str] = []
    try:
        current = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"{path}: invalid JSON: {exc}"]
    if current.get("schemaVersion") != 1:
        errors.append(f"{path}: schemaVersion must be 1")
    modules = current.get("modules")
    if not isinstance(modules, dict) or not modules:
        errors.append(f"{path}: modules must be a non-empty object")
        return errors
    try:
        threshold = float(current.get("decreaseThresholdPercentagePoints", 0.5))
        if not math.isfinite(threshold) or threshold < 0.0 or threshold > 0.5:
            raise ValueError
    except (TypeError, ValueError):
        errors.append(f"{path}: decrease threshold must be finite and between 0 and 0.5 percentage points")
        return errors
    for module, values in modules.items():
        if not isinstance(values, dict):
            errors.append(f"{path}: {module} baseline must be an object")
            continue
        for metric in ("line", "branch"):
            try:
                parse_percentage(values.get(metric), f"{path}: {module} {metric}")
            except ValueError as exc:
                errors.append(str(exc))

    previous = git_show(base_ref, str(path))
    if previous is None:
        # First protected baseline commit: the actual coverage job still has
        # to compare all reports before this file can be used for promotion.
        return errors
    previous_modules = previous.get("modules", {})
    if not isinstance(previous_modules, dict) or not previous_modules:
        errors.append(f"{path}: base coverage modules must be a non-empty object")
        return errors
    try:
        previous_threshold = float(previous.get("decreaseThresholdPercentagePoints", 0.5))
        if not math.isfinite(previous_threshold) or previous_threshold < 0.0 or previous_threshold > 0.5:
            raise ValueError
    except (TypeError, ValueError):
        errors.append(f"{path}: base decrease threshold must be finite and between 0 and 0.5 percentage points")
        return errors
    if threshold > previous_threshold:
        errors.append(f"{path}: threshold was relaxed from {previous_threshold} to {threshold}")
    for module, old_values in previous_modules.items():
        new_values = modules.get(module)
        if new_values is None:
            errors.append(f"{path}: existing module removed: {module}")
            continue
        if not isinstance(old_values, dict):
            errors.append(f"{path}: base module {module} baseline must be an object")
            continue
        for metric in ("line", "branch"):
            try:
                new_value = parse_percentage(new_values.get(metric), f"{path}: {module} {metric}")
                old_value = parse_percentage(old_values.get(metric), f"{path}: base {module} {metric}")
            except ValueError as exc:
                errors.append(str(exc))
                continue
            if new_value < old_value:
                errors.append(f"{path}: {module} {metric} baseline decreased")
    return errors


def verify_codeowners(path: Path) -> list[str]:
    """Require owners for every repository-controlled CI/CD input."""

    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        return [f"{path}: cannot read CODEOWNERS: {exc}"]
    entries: dict[str, list[str]] = {}
    errors: list[str] = []
    for line_number, line in enumerate(lines, start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        fields = stripped.split()
        if len(fields) < 2:
            errors.append(f"{path}:{line_number}: CODEOWNERS entry must have an owner")
            continue
        entries[fields[0]] = fields[1:]
    for pattern in sorted(REQUIRED_CODEOWNER_PATTERNS):
        owners = entries.get(pattern, [])
        if not owners:
            errors.append(f"{path}: missing owner for {pattern}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-ref", default="origin/master")
    parser.add_argument("--coverage-baseline", default="ci/policy/coverage-baseline.json")
    parser.add_argument("--waiver", action="append", default=[])
    parser.add_argument("--codeowners", default=".github/CODEOWNERS")
    args = parser.parse_args()
    base_ref = args.base_ref or "origin/master"
    errors = verify_coverage_baseline(Path(args.coverage_baseline), base_ref)
    for waiver in args.waiver:
        errors.extend(verify_waivers(Path(waiver)))
    errors.extend(verify_codeowners(Path(args.codeowners)))
    if errors:
        for error in errors:
            print(f"policy validation failed: {error}", file=sys.stderr)
        return 2
    print("policy files passed protected baseline and waiver checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
