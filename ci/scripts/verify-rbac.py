#!/usr/bin/env python3
"""Validate the repository-owned namespace RBAC contract.

The deployment runner must be namespace-scoped.  This check intentionally
rejects ClusterRole/ClusterRoleBinding and wildcard permissions so a future
copy/paste cannot silently turn an ARC runner into cluster-admin.
"""

from __future__ import annotations

import sys
from pathlib import Path

import yaml


EXPECTED_RESOURCES = {
    "deployments",
    "statefulsets",
    "jobs",
    "services",
    "configmaps",
    "serviceaccounts",
    "persistentvolumeclaims",
    "pods",
    "pods/log",
    "secrets",
    "networkpolicies",
    "poddisruptionbudgets",
}
EXPECTED_SECRET_NAMES = {
    "dmh-runtime",
    "dmh-internal-auth",
    "dmh-connector-truststore",
    "dmh-ghcr-pull",
    "dmh-acceptance",
    "dmh-snapshot-verifier",
}
EXPECTED_NAMESPACES = {"dev": "dmh-dev", "staging": "dmh-staging", "production": "dmh-prod"}
EXPECTED_SERVICE_ACCOUNTS = {"dmh-deployer", "dmh-runtime"}


def main() -> int:
    root = Path("deploy/rbac")
    errors: list[str] = []
    role_files = sorted((root / "base").glob("*.yaml"))
    documents: list[tuple[Path, dict]] = []
    for path in role_files:
        try:
            for document in yaml.safe_load_all(path.read_text(encoding="utf-8")):
                if isinstance(document, dict):
                    documents.append((path, document))
        except (OSError, yaml.YAMLError) as exc:
            errors.append(f"{path}: invalid YAML: {exc}")

    roles = [document for _, document in documents if document.get("kind") == "Role"]
    bindings = [document for _, document in documents if document.get("kind") == "RoleBinding"]
    service_accounts = {
        document.get("metadata", {}).get("name")
        for _, document in documents
        if document.get("kind") == "ServiceAccount"
    }
    if service_accounts != EXPECTED_SERVICE_ACCOUNTS:
        errors.append(
            "base must pre-create exactly dmh-deployer and dmh-runtime ServiceAccounts "
            f"(got {sorted(name for name in service_accounts if name)})"
        )
    expected_automount = {"dmh-deployer": True, "dmh-runtime": False}
    for path, document in documents:
        if document.get("kind") == "ServiceAccount":
            name = document.get("metadata", {}).get("name")
            expected = expected_automount.get(name)
            if expected is None or document.get("automountServiceAccountToken") is not expected:
                errors.append(
                    f"{path}: {name} automountServiceAccountToken must be {str(expected).lower()}"
                )
    for path, document in documents:
        kind = document.get("kind")
        if kind in {"ClusterRole", "ClusterRoleBinding"}:
            errors.append(f"{path}: {kind} is forbidden for deployment runners")
        if kind == "Role":
            if document.get("metadata", {}).get("name") != "dmh-deployer":
                errors.append(f"{path}: Role must be named dmh-deployer")
            found: set[str] = set()
            for rule in document.get("rules", []):
                if any(value == "*" for key in ("apiGroups", "resources", "verbs") for value in rule.get(key, [])):
                    errors.append(f"{path}: wildcard RBAC permissions are forbidden")
                if set(rule.get("verbs", [])) & {"bind", "escalate", "impersonate"}:
                    errors.append(f"{path}: privilege-escalation verbs are forbidden")
                if set(rule.get("verbs", [])) & {"deletecollection"}:
                    errors.append(f"{path}: bulk delete permission is forbidden")
                found.update(rule.get("resources", []))
                if "secrets" in rule.get("resources", []):
                    if set(rule.get("verbs", [])) != {"get"} or set(rule.get("resourceNames", [])) != EXPECTED_SECRET_NAMES:
                        errors.append(f"{path}: Secret access must be named get-only")
            missing = sorted(EXPECTED_RESOURCES - found)
            if missing:
                errors.append(f"{path}: missing required resources: {', '.join(missing)}")
            extra = sorted(found - EXPECTED_RESOURCES)
            if extra:
                errors.append(f"{path}: unexpected resources: {', '.join(extra)}")
        if kind == "RoleBinding":
            if document.get("roleRef", {}).get("kind") != "Role":
                errors.append(f"{path}: RoleBinding must reference a namespace Role")
            subjects = document.get("subjects", [])
            if not any(subject.get("kind") == "ServiceAccount" and subject.get("name") == "dmh-deployer" for subject in subjects):
                errors.append(f"{path}: dmh-deployer ServiceAccount must be bound")
    if len(roles) != 1 or len(bindings) != 1:
        errors.append("base must contain exactly one Role and one RoleBinding")

    for environment, namespace in EXPECTED_NAMESPACES.items():
        path = root / "overlays" / environment / "kustomization.yaml"
        if not path.exists():
            errors.append(f"missing RBAC overlay: {path}")
            continue
        try:
            document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        except (OSError, yaml.YAMLError) as exc:
            errors.append(f"{path}: invalid YAML: {exc}")
            continue
        if document.get("namespace") != namespace:
            errors.append(f"{path}: namespace must be {namespace}")
        if "../../base" not in document.get("resources", []):
            errors.append(f"{path}: must include ../../base")

    if errors:
        for error in errors:
            print(f"RBAC policy error: {error}", file=sys.stderr)
        return 2
    print("namespace RBAC policy passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
