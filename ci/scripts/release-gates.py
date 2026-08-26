#!/usr/bin/env python3
"""Evaluate Prometheus release-gate samples deterministically."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path

import yaml


def load_thresholds(path: str) -> dict[str, float]:
    policy = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    thresholds = policy.get("thresholds") or {}
    return {
        "minimumTraffic": float(thresholds.get("minimumTraffic", 100)),
        "errorRatio": float(thresholds.get("errorRatio", 0.01)),
        "maxRestarts": float(thresholds.get("maxRestarts", 1)),
        "p95AbsoluteSeconds": float(thresholds.get("p95AbsoluteSeconds", 1.0)),
        "p95Multiplier": float(thresholds.get("p95Multiplier", 1.2)),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("samples")
    parser.add_argument("--policy", default="observability/release-gates.yaml")
    parser.add_argument("--max-age-minutes", type=int, default=20)
    parser.add_argument("--environment", required=True, choices=("staging", "production"))
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--manifest-digest", required=True)
    args = parser.parse_args()
    if args.max_age_minutes < 1:
        parser.error("--max-age-minutes must be positive")
    thresholds = load_thresholds(args.policy)
    try:
        samples = json.loads(Path(args.samples).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"release gate sample is not valid JSON: {exc}", file=sys.stderr)
        return 2
    if not isinstance(samples, dict):
        print("release gate sample must be a JSON object", file=sys.stderr)
        return 2
    required = {"environment", "sourceSha", "manifestDigest", "traffic", "errorRatio", "p95Seconds", "p95LimitSeconds", "baselineP95Seconds", "readyReplicas", "desiredReplicas", "oomKills", "restarts", "connectorReadiness", "synthetic", "collectedAt"}
    missing = sorted(required - samples.keys())
    unknown = sorted(set(samples) - required)
    failures: list[str] = []
    if missing:
        failures.append("missing release gate fields: " + ", ".join(missing))
    if unknown:
        failures.append("release gate sample contains forbidden fields: " + ", ".join(unknown))
    if not missing:
        if samples["environment"] != args.environment:
            failures.append("release gate sample environment does not match deployment")
        if not isinstance(samples["sourceSha"], str) or not re.fullmatch(r"[0-9a-f]{40}", samples["sourceSha"]):
            failures.append("sourceSha is not a 40-character lowercase commit SHA")
        elif samples["sourceSha"] != args.source_sha:
            failures.append("release gate sample sourceSha does not match deployment")
        if not isinstance(samples["manifestDigest"], str) or not re.fullmatch(
            r"sha256:[0-9a-f]{64}", samples["manifestDigest"]
        ):
            failures.append("manifestDigest is not sha256:<64 lowercase hex>")
        elif samples["manifestDigest"] != args.manifest_digest:
            failures.append("release gate sample manifestDigest does not match deployment")
        if not isinstance(samples["connectorReadiness"], str):
            failures.append("connectorReadiness must be a string")
        if not isinstance(samples["synthetic"], str):
            failures.append("synthetic must be a string")
        try:
            collected_at = dt.datetime.fromisoformat(str(samples["collectedAt"]).replace("Z", "+00:00"))
            if collected_at.tzinfo is None:
                failures.append("collectedAt must include a timezone")
            else:
                age = dt.datetime.now(dt.timezone.utc) - collected_at.astimezone(dt.timezone.utc)
                if age < dt.timedelta(minutes=-5):
                    failures.append("collectedAt is in the future")
                if age > dt.timedelta(minutes=args.max_age_minutes):
                    failures.append(f"release gate sample is older than {args.max_age_minutes} minutes")
        except ValueError:
            failures.append("collectedAt must be an ISO-8601 timestamp")
        numeric = ("traffic", "errorRatio", "p95Seconds", "p95LimitSeconds", "baselineP95Seconds", "readyReplicas", "desiredReplicas", "oomKills", "restarts")
        for key in numeric:
            try:
                value = float(samples[key])
            except (TypeError, ValueError):
                failures.append(f"{key} is not numeric")
                continue
            if value != value or value in (float("inf"), float("-inf")):
                failures.append(f"{key} is not finite")
        for key in ("readyReplicas", "desiredReplicas", "oomKills", "restarts"):
            try:
                value = float(samples[key])
                if value != int(value):
                    failures.append(f"{key} must be an integer")
            except (TypeError, ValueError):
                pass
        if not failures:
            if float(samples["traffic"]) < 0:
                failures.append("traffic cannot be negative")
            if float(samples["errorRatio"]) < 0:
                failures.append("errorRatio cannot be negative")
            if float(samples["p95Seconds"]) < 0 or float(samples["p95LimitSeconds"]) <= 0 or float(samples["baselineP95Seconds"]) < 0:
                failures.append("p95 values are outside the allowed range")
            expected_limit = max(
                thresholds["p95AbsoluteSeconds"],
                float(samples["baselineP95Seconds"]) * thresholds["p95Multiplier"],
            )
            if abs(float(samples["p95LimitSeconds"]) - expected_limit) > 1e-9:
                failures.append("p95LimitSeconds is not bound to baselineP95Seconds")
            if float(samples["oomKills"]) < 0 or float(samples["restarts"]) < 0:
                failures.append("OOM/restart counts cannot be negative")
            if float(samples["desiredReplicas"]) < 1 or float(samples["readyReplicas"]) < 0:
                failures.append("replica counts are outside the allowed range")
            if float(samples["traffic"]) < thresholds["minimumTraffic"]:
                failures.append(f"traffic sample is below minimum {thresholds['minimumTraffic']:g} requests")
            if float(samples["errorRatio"]) > thresholds["errorRatio"]:
                failures.append(f"errorRatio exceeds {thresholds['errorRatio']:.2%}")
            if float(samples["p95Seconds"]) > float(samples["p95LimitSeconds"]):
                failures.append("p95 latency exceeds configured limit")
            if int(float(samples["readyReplicas"])) != int(float(samples["desiredReplicas"])):
                failures.append("ready replicas do not equal desired replicas")
            if int(float(samples["oomKills"])) > 0:
                failures.append("new OOMKilled event detected")
            if int(float(samples["restarts"])) > int(thresholds["maxRestarts"]):
                failures.append(f"restart count exceeds {int(thresholds['maxRestarts'])}")
            if str(samples["connectorReadiness"]).upper() != "UP":
                failures.append("connector runtime readiness is not UP")
            if str(samples["synthetic"]).lower() != "passed":
                failures.append("synthetic smoke did not pass")
    if failures:
        for failure in failures:
            print(f"release gate failed: {failure}", file=sys.stderr)
        return 2
    print("release gates passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
