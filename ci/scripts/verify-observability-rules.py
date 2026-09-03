#!/usr/bin/env python3
"""Validate the checked-in Prometheus release SLO rule contract."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml


EXPECTED_ALERTS = {
    "DmhCallRecordConsumerDltPublished",
    "DmhCallRecordConsumerFailures",
    "DmhReleaseErrorBudgetBurnCritical",
    "DmhReleaseErrorBudgetBurnWarning",
    "DmhReleaseLatencyP95High",
    "DmhReleaseOomKilled",
    "DmhReleaseRestartSpike",
    "DmhConnectorRuntimeNotReady",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", default="observability/prometheus-rules.yaml")
    args = parser.parse_args()
    path = Path(args.path)
    errors: list[str] = []
    try:
        document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except (OSError, yaml.YAMLError) as exc:
        print(f"observability rule validation failed: {exc}", file=sys.stderr)
        return 2
    found: set[str] = set()
    expressions: dict[str, str] = {}
    for group in document.get("groups", []):
        for rule in group.get("rules", []):
            name = rule.get("alert")
            if not name:
                continue
            if name in found:
                errors.append(f"duplicate alert: {name}")
            found.add(name)
            if not rule.get("expr"):
                errors.append(f"{name}: expr is required")
            else:
                expressions[name] = str(rule["expr"])
            if not rule.get("for"):
                errors.append(f"{name}: for is required")
            labels = rule.get("labels") or {}
            if labels.get("service") != "data-manager-hub":
                errors.append(f"{name}: service label must be data-manager-hub")
            runbook = (rule.get("annotations") or {}).get("runbook")
            if not isinstance(runbook, str) or not runbook.startswith("docs/runbooks/"):
                errors.append(f"{name}: runbook must point under docs/runbooks")
            elif not Path(runbook).is_file():
                errors.append(f"{name}: runbook does not exist: {runbook}")
    if found != EXPECTED_ALERTS:
        errors.append(f"alerts must equal {sorted(EXPECTED_ALERTS)}, got {sorted(found)}")
    burn_windows = {
        "DmhReleaseErrorBudgetBurnCritical": ("[5m]", "[1h]", "14.4", "and"),
        "DmhReleaseErrorBudgetBurnWarning": ("[30m]", "[6h]", "6", "and"),
    }
    for alert, required_fragments in burn_windows.items():
        expression = expressions.get(alert, "")
        for fragment in required_fragments:
            if fragment not in expression:
                errors.append(f"{alert}: multi-window burn-rate expression is missing {fragment}")
    if errors:
        for error in errors:
            print(f"observability rule validation failed: {error}", file=sys.stderr)
        return 2
    print(f"observability rules valid: {len(found)} alerts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
