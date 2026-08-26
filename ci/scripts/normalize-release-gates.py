#!/usr/bin/env python3
"""Validate and redact a release-gate sample before it becomes release evidence.

Gate samples may be supplied by a protected runner path during a staging or
production promotion. The path is an input, so the file must not be copied
verbatim into a Deployment Receipt: arbitrary extra JSON fields could leak
runner-local material into GitHub artifacts or deployment payloads. The sample
also carries the target environment, source SHA, and Build Manifest digest so
the release gate cannot be replayed for a different promotion.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from pathlib import Path


REQUIRED_FIELDS = {
    "environment",
    "manifestDigest",
    "sourceSha",
    "traffic",
    "errorRatio",
    "p95Seconds",
    "p95LimitSeconds",
    "baselineP95Seconds",
    "readyReplicas",
    "desiredReplicas",
    "oomKills",
    "restarts",
    "connectorReadiness",
    "synthetic",
    "collectedAt",
}
ENVIRONMENTS = {"staging", "production"}
NUMERIC_FIELDS = {
    "traffic",
    "errorRatio",
    "p95Seconds",
    "p95LimitSeconds",
    "baselineP95Seconds",
    "readyReplicas",
    "desiredReplicas",
    "oomKills",
    "restarts",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input")
    parser.add_argument("output")
    args = parser.parse_args()
    try:
        value = json.loads(Path(args.input).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"release gate sample is not valid JSON: {exc}", file=sys.stderr)
        return 2
    if not isinstance(value, dict):
        print("release gate sample must be a JSON object", file=sys.stderr)
        return 2
    actual = set(value)
    missing = sorted(REQUIRED_FIELDS - actual)
    unknown = sorted(actual - REQUIRED_FIELDS)
    if missing or unknown:
        if missing:
            print("release gate sample missing: " + ", ".join(missing), file=sys.stderr)
        if unknown:
            print("release gate sample contains forbidden fields: " + ", ".join(unknown), file=sys.stderr)
        return 2
    for field in NUMERIC_FIELDS:
        item = value[field]
        if isinstance(item, bool) or not isinstance(item, (int, float)) or not math.isfinite(float(item)):
            print(f"release gate sample field is not finite numeric: {field}", file=sys.stderr)
            return 2
    if value["environment"] not in ENVIRONMENTS:
        print("release gate sample environment must be staging or production", file=sys.stderr)
        return 2
    if not isinstance(value["sourceSha"], str) or not re.fullmatch(r"[0-9a-f]{40}", value["sourceSha"]):
        print("release gate sample sourceSha must be a 40-character lowercase commit SHA", file=sys.stderr)
        return 2
    if not isinstance(value["manifestDigest"], str) or not re.fullmatch(
        r"sha256:[0-9a-f]{64}", value["manifestDigest"]
    ):
        print("release gate sample manifestDigest must be sha256:<64 lowercase hex>", file=sys.stderr)
        return 2
    if not isinstance(value["connectorReadiness"], str) or not isinstance(value["synthetic"], str):
        print("connectorReadiness and synthetic must be strings", file=sys.stderr)
        return 2
    if not isinstance(value["collectedAt"], str):
        print("collectedAt must be a timestamp string", file=sys.stderr)
        return 2
    Path(args.output).write_text(
        json.dumps({field: value[field] for field in sorted(REQUIRED_FIELDS)}, sort_keys=True, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"release gate sample normalized: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
