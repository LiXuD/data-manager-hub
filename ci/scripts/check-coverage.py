#!/usr/bin/env python3
"""Compare JaCoCo/V8 reports with a protected per-module coverage baseline."""

from __future__ import annotations

import argparse
import json
import math
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def percentage(value: object, label: str) -> float:
    """Parse a coverage percentage without allowing NaN/Infinity bypasses."""

    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{label} must be a number")
    parsed = float(value)
    if not math.isfinite(parsed) or not 0.0 <= parsed <= 100.0:
        raise ValueError(f"{label} must be finite and between 0 and 100")
    return parsed


def ratio_from_jacoco(path: Path) -> dict[str, float]:
    root = ET.parse(path).getroot()
    values: dict[str, float] = {}
    for counter in root.findall("counter"):
        kind = counter.attrib["type"].lower()
        missed = int(counter.attrib["missed"])
        covered = int(counter.attrib["covered"])
        values[kind] = round((covered / (missed + covered) * 100) if missed + covered else 100.0, 4)
    return {"line": values.get("line", 100.0), "branch": values.get("branch", 100.0)}


def ratio_from_v8(path: Path) -> dict[str, float]:
    data = json.loads(path.read_text(encoding="utf-8"))
    totals = data.get("total", data)
    return {
        "line": percentage(totals.get("lines", {}).get("pct", 0.0), f"{path}: lines.pct"),
        "branch": percentage(totals.get("branches", {}).get("pct", 0.0), f"{path}: branches.pct"),
    }


def discover(args: argparse.Namespace) -> dict[str, dict[str, float]]:
    result: dict[str, dict[str, float]] = {}
    if args.scope != "frontend":
        for path in Path(args.root).glob("**/target/site/jacoco/jacoco.xml"):
            module = str(path.parent.parent.parent.parent.relative_to(Path(args.root)))
            result[module] = ratio_from_jacoco(path)
    if args.scope != "backend":
        v8 = Path(args.web_summary)
        if v8.exists():
            result[args.web_module] = ratio_from_v8(v8)
    return result


def changed_in_range(path: Path, base_ref: str | None) -> bool:
    """Return whether *path* changed from the CI base to HEAD.

    Coverage jobs run with a full checkout.  When a caller explicitly asks
    for the changed-baseline guard, an unavailable base ref is a failure
    rather than an excuse to skip the guard; this prevents a malformed CI
    event from silently disabling the protection.
    """

    ref = (base_ref or "").strip() or "HEAD^"
    result = subprocess.run(
        ["git", "diff", "--name-only", f"{ref}...HEAD", "--", str(path)],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"cannot compare coverage baseline against {ref}: {result.stderr.strip()}")
    return bool(result.stdout.strip())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", default="ci/policy/coverage-baseline.json")
    parser.add_argument("--root", default=".")
    parser.add_argument("--web-summary", default="data-platform-web/coverage/coverage-summary.json")
    parser.add_argument("--web-module", default="data-platform-web")
    parser.add_argument("--scope", choices=("all", "backend", "frontend"), default="all")
    parser.add_argument("--write-baseline", action="store_true")
    parser.add_argument(
        "--enforce-changed-baseline",
        action="store_true",
        help="when the baseline changed in this revision, require every measured value to meet it exactly",
    )
    parser.add_argument("--base-ref", default=None, help="git base ref used by --enforce-changed-baseline")
    args = parser.parse_args()
    baseline_path = Path(args.baseline)
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    actual = discover(args)
    if not actual:
        print("coverage check failed: no JaCoCo or V8 report found", file=sys.stderr)
        return 2
    if args.write_baseline:
        baseline["generatedFrom"] = "verified-ci-run"
        baseline["modules"] = actual
        baseline_path.write_text(json.dumps(baseline, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"coverage baseline written for {len(actual)} modules")
        return 0
    try:
        threshold = float(baseline.get("decreaseThresholdPercentagePoints", 0.5))
    except (TypeError, ValueError):
        print("coverage check failed: decrease threshold must be numeric", file=sys.stderr)
        return 2
    if not math.isfinite(threshold) or not 0.0 <= threshold <= 0.5:
        print("coverage check failed: decrease threshold must be finite and between 0 and 0.5", file=sys.stderr)
        return 2
    expected = baseline.get("modules", {})
    if not isinstance(expected, dict) or not expected:
        print("coverage check failed: baseline modules must be a non-empty object", file=sys.stderr)
        return 2
    malformed_modules = [module for module, values in expected.items() if not isinstance(values, dict)]
    if malformed_modules:
        print(
            "coverage check failed: baseline module entries must be objects: "
            + ", ".join(sorted(map(str, malformed_modules))),
            file=sys.stderr,
        )
        return 2
    if args.scope == "backend":
        expected = {module: values for module, values in expected.items() if module != args.web_module}
    elif args.scope == "frontend":
        expected = {args.web_module: expected.get(args.web_module, {})}
    failures: list[str] = []
    for module, values in actual.items():
        if module not in expected:
            failures.append(f"{module}: missing protected baseline")
            continue
        for metric in ("line", "branch"):
            try:
                before = percentage(expected[module].get(metric), f"baseline {module} {metric}")
                after = percentage(values.get(metric), f"measured {module} {metric}")
            except ValueError as exc:
                failures.append(str(exc))
                continue
            if before - after > threshold:
                failures.append(f"{module} {metric}: {before:.2f}% -> {after:.2f}% exceeds {threshold:.2f}pp")
    if args.enforce_changed_baseline:
        try:
            baseline_changed = changed_in_range(baseline_path, args.base_ref)
        except RuntimeError as exc:
            print(f"coverage check failed: {exc}", file=sys.stderr)
            return 2
        if baseline_changed:
            for module, values in actual.items():
                if module not in expected:
                    continue
                for metric in ("line", "branch"):
                    try:
                        claimed = percentage(expected[module].get(metric), f"baseline {module} {metric}")
                        measured = percentage(values.get(metric), f"measured {module} {metric}")
                    except ValueError as exc:
                        failures.append(str(exc))
                        continue
                    if measured < claimed:
                        failures.append(
                            f"{module} {metric}: changed baseline {claimed:.4f}% exceeds measured {measured:.4f}%"
                        )
    for module in expected:
        if module not in actual:
            failures.append(f"{module}: baseline exists but report is missing")
    if failures:
        for failure in failures:
            print(f"coverage check failed: {failure}", file=sys.stderr)
        return 2
    print(f"coverage check passed for {len(actual)} modules")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
