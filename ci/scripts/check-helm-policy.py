#!/usr/bin/env python3
"""Policy-check rendered Helm resources without contacting a cluster."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml

IMAGE_PREFIX = "ghcr.io/lixud/data-manager-hub-"
IMAGE_RE = re.compile(r"^ghcr\.io/lixud/data-manager-hub-[a-z0-9-]+@sha256:[0-9a-f]{64}$")


def documents(path: Path):
    yield from yaml.safe_load_all(path.read_text(encoding="utf-8"))


def secret_like_keys(value, path=""):
    if isinstance(value, dict):
        for key, child in value.items():
            key_path = f"{path}.{key}" if path else str(key)
            lowered = str(key).lower()
            if any(marker in lowered for marker in ("password", "privatekey", "token")) and not lowered.endswith("secretref") and not lowered.startswith("automount"):
                yield key_path
            yield from secret_like_keys(child, key_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from secret_like_keys(child, f"{path}[{index}]")


def contains_key(value, wanted: str) -> bool:
    if isinstance(value, dict):
        return wanted in value or any(contains_key(child, wanted) for child in value.values())
    if isinstance(value, list):
        return any(contains_key(child, wanted) for child in value)
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rendered")
    args = parser.parse_args()
    errors: list[str] = []
    release_contract = None
    for resource in documents(Path(args.rendered)):
        if not resource or not resource.get("kind"):
            continue
        metadata = resource.get("metadata") or {}
        name = f"{resource['kind']}/{metadata.get('name', '<unnamed>')}"
        if resource["kind"] == "ConfigMap" and str(metadata.get("name", "")).endswith("-release"):
            release_contract = resource.get("data") or {}
        spec = resource.get("spec") or {}
        pod_spec = spec.get("template", {}).get("spec", spec.get("jobTemplate", {}).get("spec", {}).get("template", {}).get("spec", {}))
        if resource["kind"] in {"Deployment", "StatefulSet", "Job"}:
            pod_security = pod_spec.get("securityContext", {})
            if pod_security.get("runAsNonRoot") is not True:
                errors.append(f"{name}: runAsNonRoot must be true")
            if pod_security.get("runAsUser") != 10001 or pod_security.get("runAsGroup") != 10001:
                errors.append(f"{name}: pod must run as UID/GID 10001")
            if (pod_security.get("seccompProfile") or {}).get("type") != "RuntimeDefault":
                errors.append(f"{name}: RuntimeDefault seccomp profile is required")
            for container in pod_spec.get("containers", []):
                image = str(container.get("image", ""))
                if not image or not IMAGE_RE.fullmatch(image):
                    errors.append(f"{name}: container image must use {IMAGE_PREFIX}<component>@sha256:<64 hex>")
                security = container.get("securityContext", {})
                if security.get("allowPrivilegeEscalation") is not False:
                    errors.append(f"{name}: allowPrivilegeEscalation must be false")
                if security.get("readOnlyRootFilesystem") is not True:
                    errors.append(f"{name}: readOnlyRootFilesystem must be true")
                if security.get("privileged") is True:
                    errors.append(f"{name}: privileged containers are forbidden")
                dropped = set((security.get("capabilities") or {}).get("drop", []))
                if "ALL" not in dropped:
                    errors.append(f"{name}: container must drop ALL capabilities")
            if resource["kind"] in {"Deployment", "StatefulSet"}:
                if pod_spec.get("serviceAccountName") != "dmh-runtime":
                    errors.append(f"{name}: workloads must use the dedicated dmh-runtime ServiceAccount")
                if pod_spec.get("automountServiceAccountToken") is not False:
                    errors.append(f"{name}: workload ServiceAccount token automount must be false")
                for container in pod_spec.get("containers", []):
                    if not container.get("readinessProbe") or not container.get("livenessProbe"):
                        errors.append(f"{name}: readinessProbe and livenessProbe are required")
                    resources = container.get("resources", {})
                    if not resources.get("requests") or not resources.get("limits"):
                        errors.append(f"{name}: requests and limits are required")
        if resource["kind"] == "StatefulSet" and str(metadata.get("name", "")).endswith("-access"):
            replicas = spec.get("replicas")
            partition = ((spec.get("updateStrategy") or {}).get("rollingUpdate") or {}).get("partition")
            if not isinstance(replicas, int) or replicas < 1:
                errors.append(f"{name}: access StatefulSet replicas must be a positive integer")
            elif partition != replicas - 1:
                errors.append(
                    f"{name}: access StatefulSet must start with partition=replicas-1 "
                    f"to prevent an uncontrolled parallel rollout (replicas={replicas}, partition={partition})"
                )
        if resource["kind"] == "ServiceAccount":
            expected_automount = {"dmh-deployer": True, "dmh-runtime": False}.get(metadata.get("name"))
            if expected_automount is None or resource.get("automountServiceAccountToken") is not expected_automount:
                errors.append(
                    f"{name}: ServiceAccount automountServiceAccountToken must be "
                    f"{str(expected_automount).lower()}"
                )
        if contains_key(resource, "hostPath"):
            errors.append(f"{name}: hostPath volumes are forbidden")
        if resource["kind"] != "Secret":
            for key_path in secret_like_keys(resource):
                errors.append(f"{name}: possible secret value at {key_path}")
    if release_contract is None:
        errors.append("release ConfigMap is required to bind environment and immutable Nacos group")
    else:
        environment = str(release_contract.get("environment", ""))
        nacos_group = str(release_contract.get("nacosGroup", ""))
        patterns = {
            "dev": r"^(DEFAULT_GROUP|DMH_DEV_[A-Za-z0-9_-]+)$",
            "staging": r"^DMH_STAGING_[0-9a-f]{40}$",
            "production": r"^DMH_PROD_[0-9a-f]{40}$",
        }
        pattern = patterns.get(environment)
        if pattern is None:
            errors.append(f"release ConfigMap environment is invalid: {environment or '<missing>'}")
        elif not re.fullmatch(pattern, nacos_group):
            errors.append(
                f"release ConfigMap Nacos group is not immutable for {environment}: {nacos_group or '<missing>'}"
            )
    if errors:
        for error in errors:
            print(f"helm policy error: {error}", file=sys.stderr)
        return 2
    print("rendered Helm resources passed policy")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
