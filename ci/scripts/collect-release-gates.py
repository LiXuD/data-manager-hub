#!/usr/bin/env python3
"""Collect and validate release metrics from Prometheus HTTP API."""

from __future__ import annotations

import argparse
import datetime as dt
import ipaddress
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

import yaml


def policy_window_minutes(policy: dict, name: str, default: int) -> int:
    value = str((policy.get("window") or {}).get(name, f"{default}m"))
    match = re.fullmatch(r"([1-9][0-9]*)m", value)
    if not match:
        raise ValueError(f"observability window {name} must be a positive number of minutes")
    return int(match.group(1))


def query(base: str, expression: str, token: str | None) -> float:
    url = base.rstrip("/") + "/api/v1/query?" + urllib.parse.urlencode({"query": expression})
    request = urllib.request.Request(url)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(request, timeout=10) as response:
        payload = json.load(response)
    if payload.get("status") != "success" or not payload.get("data", {}).get("result"):
        raise RuntimeError(f"Prometheus returned no data for query: {expression}")
    value = payload["data"]["result"][0]["value"][1]
    return float(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--namespace", required=True)
    parser.add_argument("--environment", required=True, choices=("staging", "production"))
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--manifest-digest", required=True)
    parser.add_argument("--service-regex", default="data-manager-hub-.*")
    parser.add_argument("--deployment-regex", default="data-manager-hub-.*")
    parser.add_argument("--synthetic-status", required=True)
    parser.add_argument("--baseline-file")
    parser.add_argument(
        "--window",
        choices=("acute", "baseline"),
        default="acute",
        help="collect the acute gate window or the policy-defined pre-release baseline window",
    )
    parser.add_argument("--policy", default="observability/release-gates.yaml")
    parser.add_argument("--p95-absolute-seconds", type=float)
    parser.add_argument("--p95-multiplier", type=float)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        parser.error("--source-sha must be a 40-character lowercase commit SHA")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", args.manifest_digest):
        parser.error("--manifest-digest must be sha256:<64 lowercase hex>")
    parsed_url = urllib.parse.urlparse(args.url)
    if parsed_url.scheme not in {"http", "https"} or not parsed_url.netloc or parsed_url.username or parsed_url.password:
        print("release gate collection requires an HTTP(S) Prometheus URL", file=sys.stderr)
        return 2
    hostname = parsed_url.hostname or ""
    try:
        loopback_endpoint = ipaddress.ip_address(hostname).is_loopback
    except ValueError:
        loopback_endpoint = hostname in {"localhost", "localhost.localdomain"}
    token = os.environ.get("PROMETHEUS_BEARER_TOKEN", "")
    if parsed_url.scheme == "http" and not loopback_endpoint:
        print("release gate collection refuses bearer-token transport over non-loopback HTTP", file=sys.stderr)
        return 2
    if parsed_url.scheme == "https" and not loopback_endpoint and not token:
        print("release gate collection requires PROMETHEUS_BEARER_TOKEN for non-loopback HTTPS", file=sys.stderr)
        return 2
    policy = yaml.safe_load(Path(args.policy).read_text(encoding="utf-8")) or {}
    policy_thresholds = policy.get("thresholds") or {}
    try:
        acute_window_minutes = policy_window_minutes(policy, "acute", 15)
        baseline_window_minutes = policy_window_minutes(policy, "baseline", 30)
    except ValueError as exc:
        print(f"release gate collection failed: {exc}", file=sys.stderr)
        return 2
    p95_absolute = float(args.p95_absolute_seconds if args.p95_absolute_seconds is not None else policy_thresholds.get("p95AbsoluteSeconds", 1.0))
    p95_multiplier = float(args.p95_multiplier if args.p95_multiplier is not None else policy_thresholds.get("p95Multiplier", 1.2))
    ns = args.namespace.replace('"', '\\"')
    service = args.service_regex.replace('"', '\\"')
    deployment = args.deployment_regex.replace('"', '\\"')
    p95_expression = (
        f'histogram_quantile(0.95,sum(increase(http_server_requests_seconds_bucket{{namespace="{ns}",service=~"{service}"}}[{baseline_window_minutes}m])) by (le))'
        if args.window == "baseline"
        else f'histogram_quantile(0.95,sum(rate(http_server_requests_seconds_bucket{{namespace="{ns}",service=~"{service}"}}[5m])) by (le))'
    )
    expressions = {
        # Traffic is a request count over the policy-defined acute gate window,
        # not a requests/second rate.  This keeps minimumTraffic meaningful
        # for low-volume staging environments and still fails closed when
        # Prometheus has no sample.
        "traffic": f'sum(increase(http_server_requests_seconds_count{{namespace="{ns}",service=~"{service}"}}[{acute_window_minutes}m]))',
        "errorRatio": f'sum(rate(http_server_requests_seconds_count{{namespace="{ns}",service=~"{service}",status=~"5.."}}[5m])) / sum(rate(http_server_requests_seconds_count{{namespace="{ns}",service=~"{service}"}}[5m]))',
        "p95Seconds": p95_expression,
        "readyReplicas": f'sum(kube_deployment_status_replicas_available{{namespace="{ns}",deployment=~"{deployment}"}}) + sum(kube_statefulset_status_replicas_ready{{namespace="{ns}",statefulset=~"{deployment}"}})',
        "desiredReplicas": f'sum(kube_deployment_spec_replicas{{namespace="{ns}",deployment=~"{deployment}"}}) + sum(kube_statefulset_replicas{{namespace="{ns}",statefulset=~"{deployment}"}})',
        "oomKills": f'sum(max_over_time(kube_pod_container_status_last_terminated_reason{{namespace="{ns}",reason="OOMKilled"}}[{acute_window_minutes}m]))',
        "restarts": f'sum(increase(kube_pod_container_status_restarts_total{{namespace="{ns}"}}[{acute_window_minutes}m]))',
        # Every workload must report connector readiness.  max() would let a
        # single healthy pod hide a failed pod during a release gate.
        "connectorReadiness": f'min(dm_connector_runtime_readiness{{namespace="{ns}"}})',
    }
    try:
        values = {key: query(args.url, expression, token) for key, expression in expressions.items()}
    except Exception as exc:
        print(f"release gate collection failed: {exc}", file=sys.stderr)
        return 2
    baseline_p95 = 0.0
    if args.baseline_file:
        baseline = json.loads(Path(args.baseline_file).read_text(encoding="utf-8"))
        baseline_p95 = float(baseline.get("p95Seconds", 0.0))
    values["connectorReadiness"] = "UP" if values["connectorReadiness"] >= 0.5 else "DOWN"
    values["synthetic"] = args.synthetic_status
    values["environment"] = args.environment
    values["sourceSha"] = args.source_sha
    values["manifestDigest"] = args.manifest_digest
    values["baselineP95Seconds"] = baseline_p95
    values["p95LimitSeconds"] = max(p95_absolute, baseline_p95 * p95_multiplier)
    values["collectedAt"] = dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")
    Path(args.output).write_text(json.dumps(values, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"release gate samples written: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
