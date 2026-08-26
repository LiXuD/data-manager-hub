#!/usr/bin/env python3
"""Reject weakened or malformed release-gate policy files."""

from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path
from typing import Any

import yaml


EXPECTED_WINDOWS = {
    "baseline": "30m",
    "acute": "15m",
    "blocking": "60m",
    "enhanced": "24h",
}
EXPECTED_QUERIES = {
    "traffic",
    "errorRatio",
    "p95Seconds",
    "readyReplicas",
    "desiredReplicas",
    "oomKills",
    "restarts",
    "connectorReadiness",
}
EXPECTED_TOP_LEVEL = {"schemaVersion", "window", "thresholds", "queries", "continuousSlo"}


def as_finite_number(value: Any, name: str, errors: list[str]) -> float | None:
    if isinstance(value, bool):
        errors.append(f"{name} must be numeric")
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        errors.append(f"{name} must be numeric")
        return None
    if not math.isfinite(number):
        errors.append(f"{name} must be finite")
        return None
    return number


def validate_policy(value: Any) -> list[str]:
    errors: list[str] = []
    if not isinstance(value, dict):
        return ["release-gates policy root must be an object"]
    unknown = sorted(set(value) - EXPECTED_TOP_LEVEL)
    if unknown:
        errors.append("release-gates policy contains forbidden top-level fields: " + ", ".join(unknown))
    if value.get("schemaVersion") != 1:
        errors.append("schemaVersion must be 1")

    windows = value.get("window")
    if not isinstance(windows, dict):
        errors.append("window must be an object")
    else:
        extra_windows = sorted(set(windows) - set(EXPECTED_WINDOWS))
        if extra_windows:
            errors.append("window contains forbidden fields: " + ", ".join(extra_windows))
        for name, expected in EXPECTED_WINDOWS.items():
            if windows.get(name) != expected:
                errors.append(f"window.{name} must remain {expected}")

    thresholds = value.get("thresholds")
    if not isinstance(thresholds, dict):
        errors.append("thresholds must be an object")
    else:
        expected_thresholds = {
            "minimumTraffic": (100.0, None),
            "errorRatio": (0.0, 0.01),
            "p95AbsoluteSeconds": (0.0, 1.0),
            "p95Multiplier": (0.0, 1.2),
            "maxRestarts": (0.0, 1.0),
        }
        extra_thresholds = sorted(set(thresholds) - set(expected_thresholds))
        if extra_thresholds:
            errors.append("thresholds contain forbidden fields: " + ", ".join(extra_thresholds))
        for name, (minimum, maximum) in expected_thresholds.items():
            number = as_finite_number(thresholds.get(name), f"thresholds.{name}", errors)
            if number is None:
                continue
            if number < minimum or (maximum is not None and number > maximum):
                upper = "infinity" if maximum is None else f"{maximum:g}"
                errors.append(f"thresholds.{name} must be in [{minimum:g}, {upper}]")

    queries = value.get("queries")
    if not isinstance(queries, dict):
        errors.append("queries must be an object")
    else:
        missing = sorted(EXPECTED_QUERIES - set(queries))
        extra = sorted(set(queries) - EXPECTED_QUERIES)
        if missing:
            errors.append("queries missing: " + ", ".join(missing))
        if extra:
            errors.append("queries contain forbidden fields: " + ", ".join(extra))
        for name in EXPECTED_QUERIES & set(queries):
            if not isinstance(queries[name], str) or not queries[name].strip():
                errors.append(f"queries.{name} must be a non-empty string")

    slo = value.get("continuousSlo")
    if not isinstance(slo, dict):
        errors.append("continuousSlo must be an object")
    else:
        if set(slo) - {"availability", "burnRates"}:
            errors.append(
                "continuousSlo contains forbidden fields: "
                + ", ".join(sorted(set(slo) - {"availability", "burnRates"}))
            )
        availability = as_finite_number(slo.get("availability"), "continuousSlo.availability", errors)
        if availability is not None and availability < 0.999:
            errors.append("continuousSlo.availability must be at least 0.999")
        burn_rates = slo.get("burnRates")
        expected_burn_rates = {
            "critical": (14.4, "5m", "1h"),
            "warning": (6.0, "30m", "6h"),
        }
        if not isinstance(burn_rates, dict):
            errors.append("continuousSlo.burnRates must be an object")
        else:
            extra_burn_rates = sorted(set(burn_rates) - set(expected_burn_rates))
            if extra_burn_rates:
                errors.append("continuousSlo.burnRates contains forbidden fields: " + ", ".join(extra_burn_rates))
            for name, expected in expected_burn_rates.items():
                actual = burn_rates.get(name)
                if not isinstance(actual, list) or len(actual) != 3:
                    errors.append(f"continuousSlo.burnRates.{name} must be [rate, shortWindow, longWindow]")
                    continue
                rate = as_finite_number(actual[0], f"continuousSlo.burnRates.{name}[0]", errors)
                if rate != expected[0] or actual[1:] != list(expected[1:]):
                    errors.append(
                        f"continuousSlo.burnRates.{name} must be [{expected[0]:g}, {expected[1]}, {expected[2]}]"
                    )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", default="observability/release-gates.yaml")
    args = parser.parse_args()
    path = Path(args.path)
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as exc:
        print(f"release-gates policy validation failed: {exc}", file=sys.stderr)
        return 2
    errors = validate_policy(value)
    if errors:
        for error in errors:
            print(f"release-gates policy validation failed: {error}", file=sys.stderr)
        return 2
    print("release-gates policy passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
