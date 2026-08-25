#!/usr/bin/env python3
"""Validate the repository's runtime contract without contacting a cluster."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml


REQUIRED_SECRET_NAMES = {
    "dmh-runtime",
    "dmh-internal-auth",
    "dmh-connector-truststore",
    "dmh-snapshot-verifier",
    "dmh-acceptance",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", default="ci/contracts/runtime-contract.v1.yaml")
    parser.add_argument("--root", default=".")
    parser.add_argument(
        "--require-artifacts",
        action="store_true",
        help="also require locally built artifact parent directories",
    )
    args = parser.parse_args()
    root = Path(args.root).resolve()
    contract = yaml.safe_load((root / args.contract).read_text(encoding="utf-8"))
    errors: list[str] = []
    if contract.get("schemaVersion") != 1 or contract.get("kind") != "RuntimeContract":
        errors.append("runtime contract schemaVersion/kind is invalid")
    if contract.get("springProfile") != {"dev": "dev", "staging": "staging", "production": "prod"}:
        errors.append("springProfile must map logical production to Spring profile prod")
    if contract.get("namespace") != {"dev": "dmh-dev", "staging": "dmh-staging", "production": "dmh-prod"}:
        errors.append("namespace must map production to dmh-prod and non-production environments to their dmh-* namespaces")
    runtime_secret = (contract.get("secretContracts") or {}).get("dmh-runtime") or {}
    if "NACOS_SERVER_ADDR" not in set(runtime_secret.get("requiredEnvKeys", [])):
        errors.append("dmh-runtime must declare NACOS_SERVER_ADDR as a required runtime key")
    internal_auth = (contract.get("secretContracts") or {}).get("dmh-internal-auth") or {}
    required_auth_env_keys = {
        "INTERNAL_AUTH_TOKEN_URI",
        "INTERNAL_AUTH_ACCESS_SECRET",
        "INTERNAL_AUTH_BILLING_SECRET",
        "INTERNAL_AUTH_MASTERDATA_SECRET",
        "INTERNAL_AUTH_IDENTITY_SECRET",
        "INTERNAL_AUTH_GOVERNANCE_SECRET",
        "PLATFORM_ENCRYPTION_MASTER_KEY",
    }
    if set(internal_auth.get("envKeys", [])) != required_auth_env_keys:
        errors.append("dmh-internal-auth envKeys must contain the complete internal-auth secret contract")
    if set(internal_auth.get("volumeKeys", [])) != {"public.pem", "private.pem"}:
        errors.append("dmh-internal-auth volumeKeys must contain public.pem and private.pem")
    if internal_auth.get("mountPath") != "/run/secrets/dmh/internal-auth" or internal_auth.get("mode") != "0440":
        errors.append("dmh-internal-auth mountPath/mode does not match the non-root Helm contract")
    components = contract.get("components", {})
    chart_values_path = root / "deploy/helm/data-manager-hub/values.yaml"
    if chart_values_path.exists():
        chart_values = yaml.safe_load(chart_values_path.read_text(encoding="utf-8")) or {}
        chart_components = chart_values.get("components", {}) or {}
        expected_chart_components = {
            name for name, component in components.items()
            if component.get("workload") in {"Deployment", "StatefulSet"}
        }
        if set(chart_components) != expected_chart_components:
            errors.append("Helm component set does not match runtime workload contract")
        for name in expected_chart_components:
            if chart_components.get(name, {}).get("workload") != components[name].get("workload"):
                errors.append(f"Helm workload type mismatch for {name}")
        secrets = chart_values.get("existingSecrets", {}) or {}
        expected_secrets = {
            "runtime": "dmh-runtime",
            "internalAuth": "dmh-internal-auth",
            "connectorTruststore": "dmh-connector-truststore",
            "ghcrPull": "dmh-ghcr-pull",
        }
        for key, expected in expected_secrets.items():
            if secrets.get(key) != expected:
                errors.append(f"Helm existingSecrets.{key} must be {expected}")
        if (chart_values.get("global") or {}).get("serviceAccount") != "dmh-deployer":
            errors.append("Helm global.serviceAccount must be dmh-deployer")
        if (chart_values.get("global") or {}).get("workloadServiceAccount") != "dmh-runtime":
            errors.append("Helm global.workloadServiceAccount must be dmh-runtime")
        chart_templates = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (root / "deploy/helm/data-manager-hub/templates").glob("*.yaml")
        )
        if "Values.global.nacosServer" in chart_templates:
            errors.append("Helm templates must not hard-code a global Nacos server address")
        if "Values.existingSecrets.ghcrPull" not in chart_templates:
            errors.append("Helm workloads must source imagePullSecrets from existingSecrets.ghcrPull")
        network_policy = chart_values.get("networkPolicy", {}) or {}
        if not isinstance(network_policy.get("metricsNamespaceLabels"), dict) or not network_policy.get("metricsNamespaceLabels"):
            errors.append("Helm networkPolicy must declare explicit metricsNamespaceLabels for cross-namespace Prometheus scraping")
        if "Values.networkPolicy.metricsNamespaceLabels" not in chart_templates:
            errors.append("Helm NetworkPolicy must use the explicit metricsNamespaceLabels boundary")
        if "serviceAccountName: {{ $.Values.global.workloadServiceAccount }}" not in chart_templates or "serviceAccountName: {{ .Values.global.workloadServiceAccount }}" not in chart_templates:
            errors.append("Helm workloads must use the dedicated workloadServiceAccount, not the Runner identity")
        access_template = root / "deploy/helm/data-manager-hub/templates/access-statefulset.yaml"
        if not access_template.exists() or "/actuator/health/readiness" not in access_template.read_text(encoding="utf-8"):
            errors.append("Access StatefulSet must probe the readiness group that includes connectorRuntimeReadiness")
    else:
        errors.append(f"Helm values file is missing: {chart_values_path.relative_to(root)}")
    images: set[str] = set()
    ports: set[int] = set()
    data_ids: set[str] = set()
    expected_nacos_files = {"dev": set(), "prod": set()}
    for name, component in components.items():
        for key in ("workload", "module", "image", "secretRefs"):
            if key not in component:
                errors.append(f"{name}: missing {key}")
        image = component.get("image")
        if image in images:
            errors.append(f"duplicate image: {image}")
        images.add(image)
        port = int(component.get("port", 0))
        if port and port in ports:
            errors.append(f"duplicate service port: {port}")
        if port:
            ports.add(port)
        data_id = component.get("nacosDataId")
        if data_id:
            if data_id in data_ids:
                errors.append(f"duplicate Nacos Data ID: {data_id}")
            data_ids.add(data_id)
            try:
                for profile in expected_nacos_files:
                    expected_nacos_files[profile].add(str(data_id).format(profile=profile))
            except (KeyError, IndexError, ValueError) as exc:
                errors.append(f"{name}: nacosDataId must contain only the profile placeholder: {exc}")
        for secret in component.get("secretRefs", []):
            if secret not in REQUIRED_SECRET_NAMES:
                errors.append(f"{name}: unknown Secret reference {secret}")
        runner_secret_refs = component.get("runnerSecretRefs", [])
        if not isinstance(runner_secret_refs, list):
            errors.append(f"{name}: runnerSecretRefs must be a list")
        for secret in runner_secret_refs:
            if secret not in REQUIRED_SECRET_NAMES:
                errors.append(f"{name}: unknown deployment-runner Secret reference {secret}")
        if name == "dbops" and runner_secret_refs != ["dmh-snapshot-verifier"]:
            errors.append("dbops: runnerSecretRefs must contain only dmh-snapshot-verifier")
        if name != "dbops" and runner_secret_refs:
            errors.append(f"{name}: only dbops may declare runnerSecretRefs")
        if component.get("workload") in {"Deployment", "StatefulSet"}:
            if not component.get("probePort") or not component.get("servicePort"):
                errors.append(f"{name}: workload must define probePort and servicePort")
            replicas = component.get("replicas", {})
            for environment in ("dev", "staging", "production"):
                if int(replicas.get(environment, 0)) < 1:
                    errors.append(f"{name}: replicas.{environment} must be >= 1")
        module = root / component["module"]
        if not module.exists():
            errors.append(f"{name}: module path does not exist: {component['module']}")
        artifact_glob = component.get("artifactGlob")
        if args.require_artifacts and artifact_glob and not artifact_glob.startswith("ci/"):
            artifact_parent = root / artifact_glob.split("/target/")[0]
            if not artifact_parent.exists():
                errors.append(f"{name}: artifact parent does not exist: {artifact_parent.relative_to(root)}")
    if set(contract.get("policies", {}).get("platforms", [])) != {"linux/amd64", "linux/arm64"}:
        errors.append("platform policy must declare linux/amd64 and linux/arm64")
    access_component = components.get("access") or {}
    readiness_indicator = access_component.get("readinessIndicator")
    if readiness_indicator != "connectorRuntimeReadiness":
        errors.append("access readinessIndicator must be connectorRuntimeReadiness")
    # The Access pod-by-pod rollout relies on the Kubernetes readiness probe,
    # so the Nacos source bundle must keep the connector health indicator in
    # the readiness group for every deployable profile. Staging currently
    # reuses the prod source bundle, which is checked explicitly below.
    access_profiles = {
        "dev": root / "nacos-config/dev/data-platform-access-dev.yml",
        "staging": root / "nacos-config/prod/data-platform-access-prod.yml",
        "production": root / "nacos-config/prod/data-platform-access-prod.yml",
    }
    for environment, path in access_profiles.items():
        if not path.exists():
            errors.append(f"Access Nacos config is missing for {environment}: {path.relative_to(root)}")
            continue
        profile = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        readiness_group = str(profile.get("management.endpoint.health.group.readiness.include", ""))
        if readiness_indicator not in {part.strip() for part in readiness_group.split(",") if part.strip()}:
            errors.append(
                f"Access Nacos readiness group for {environment} must include {readiness_indicator}"
            )
    for profile, directory_name in (("dev", "dev"), ("prod", "prod")):
        directory = root / "nacos-config" / directory_name
        actual_files = {path.name for pattern in ("*.yml", "*.properties") for path in directory.glob(pattern)}
        missing_files = sorted(expected_nacos_files[profile] - actual_files)
        unexpected_files = sorted(actual_files - expected_nacos_files[profile])
        if missing_files:
            errors.append(f"Nacos {profile} config is missing contract Data IDs: {', '.join(missing_files)}")
        if unexpected_files:
            errors.append(f"Nacos {profile} config has uncontracted Data IDs: {', '.join(unexpected_files)}")
    if errors:
        for error in errors:
            print(f"contract error: {error}", file=sys.stderr)
        return 2
    print(f"runtime contract valid: {len(components)} components, {len(images)} images")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
