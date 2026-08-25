#!/usr/bin/env python3
"""Validate the cluster admission contract protecting private CI Jobs."""

from __future__ import annotations

import sys
from pathlib import Path

import yaml


POLICY_NAME = "data-manager-hub-private-job-boundary"
REQUIRED_EXPRESSIONS = (
    "serviceAccountName == 'dmh-runtime'",
    "automountServiceAccountToken",
    "backoffLimit == 0",
    "c.image.matches('^ghcr.io/lixud/data-manager-hub-(dbops|acceptance)@sha256:[0-9a-f]{64}$')",
    "object.spec.template.spec.containers.size() == 1",
    "object.spec.template.spec.initContainers.size() == 0",
    "object.spec.template.spec.containers.all(c, !has(c.command))",
    "c.image.matches('^ghcr.io/lixud/data-manager-hub-dbops@sha256:[0-9a-f]{64}$')",
    "c.image.matches('^ghcr.io/lixud/data-manager-hub-acceptance@sha256:[0-9a-f]{64}$')",
    "c.args.all(a, a in ['migrate', 'preflight', 'status', 'update-sql', 'nacos'])",
    "c.args.size() == 0",
    "runAsUser == 10001",
    "runAsGroup == 10001",
    "seccompProfile.type == 'RuntimeDefault'",
    "c.securityContext.allowPrivilegeEscalation == false",
    "c.securityContext.capabilities.drop.exists(cap, cap == 'ALL')",
    "c.securityContext.readOnlyRootFilesystem == false",
    "c.securityContext.readOnlyRootFilesystem == true",
    "imagePullSecrets.size() == 1",
    "s.name == 'dmh-ghcr-pull'",
    "object.spec.template.spec.volumes.size() == 3",
    "v.name in ['tmp', 'workspace-target', 'runtime']",
    "has(v.emptyDir)",
    "c.volumeMounts.size() == 3",
    "m.mountPath == '/tmp'",
    "m.mountPath == '/workspace/target'",
    "m.mountPath == '/workspace/.runtime'",
    "e.secretRef.name in ['dmh-runtime', 'dmh-acceptance']",
    "has(e.secretRef)",
    "c.image.matches('^ghcr.io/lixud/data-manager-hub-dbops@sha256:[0-9a-f]{64}$') &&",
    "c.image.matches('^ghcr.io/lixud/data-manager-hub-acceptance@sha256:[0-9a-f]{64}$') &&",
    "c.envFrom.size() == 1",
    "c.envFrom.size() == 2",
    "e.name in ['NACOS_PROFILE', 'NACOS_NAMESPACE', 'NACOS_GROUP', 'NACOS_MODE']",
    "initContainers",
    "v.projected.sources",
    "s.serviceAccountToken",
    "v.secret.secretName in ['dmh-runtime', 'dmh-acceptance']",
    "v.hostPath",
    "v.csi",
    "hostNetwork",
    "hostPID",
    "hostIPC",
)


def main() -> int:
    path = Path("deploy/admission/job-secret-boundary.yaml")
    errors: list[str] = []
    try:
        resources = [item for item in yaml.safe_load_all(path.read_text(encoding="utf-8")) if item]
    except (OSError, yaml.YAMLError) as exc:
        print(f"admission policy error: {exc}", file=sys.stderr)
        return 2
    policies = [item for item in resources if item.get("kind") == "ValidatingAdmissionPolicy"]
    bindings = [item for item in resources if item.get("kind") == "ValidatingAdmissionPolicyBinding"]
    if len(policies) != 1 or len(bindings) != 1:
        errors.append("exactly one policy and one binding are required")
    if policies:
        policy = policies[0]
        if policy.get("metadata", {}).get("name") != POLICY_NAME:
            errors.append("policy name is not stable")
        spec = policy.get("spec", {})
        if spec.get("failurePolicy") != "Fail":
            errors.append("failurePolicy must be Fail")
        rules = spec.get("matchConstraints", {}).get("resourceRules", [])
        if not any(
            set(rule.get("apiGroups", [])) == {"batch"}
            and set(rule.get("apiVersions", [])) == {"v1"}
            and set(rule.get("resources", [])) == {"jobs"}
            and set(rule.get("operations", [])) == {"CREATE", "UPDATE"}
            for rule in rules
        ):
            errors.append("policy must cover CREATE and UPDATE of batch/v1 Jobs")
        namespace_selector = spec.get("matchConstraints", {}).get("namespaceSelector", {})
        namespace_matches = namespace_selector.get("matchExpressions", []) if isinstance(namespace_selector, dict) else []
        if not any(
            item.get("key") == "kubernetes.io/metadata.name"
            and item.get("operator") == "In"
            and set(item.get("values", [])) == {"dmh-dev", "dmh-staging", "dmh-prod"}
            for item in namespace_matches
            if isinstance(item, dict)
        ):
            errors.append("policy must be namespace-scoped to dmh-dev, dmh-staging, and dmh-prod")
        expressions = "\n".join(str(item.get("expression", "")) for item in spec.get("validations", []))
        for required in REQUIRED_EXPRESSIONS:
            if required not in expressions:
                errors.append(f"policy is missing expression fragment: {required}")
    if bindings:
        binding = bindings[0]
        spec = binding.get("spec", {})
        if binding.get("metadata", {}).get("name") != POLICY_NAME:
            errors.append("binding name is not stable")
        if spec.get("policyName") != POLICY_NAME:
            errors.append("binding must reference the private Job policy")
        if spec.get("validationActions") != ["Deny"]:
            errors.append("binding must enforce Deny")
    if errors:
        for error in errors:
            print(f"admission policy error: {error}", file=sys.stderr)
        return 2
    print("admission policy passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
