#!/usr/bin/env python3
"""Create and verify the immutable build-manifest.v1 contract."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
EXPECTED_COMPONENTS = {
    "gateway", "masterdata", "access", "billing", "identity", "governance", "web", "dbops", "acceptance"
}
VERIFICATION_FIELDS = {"backend", "frontend", "security", "deployability", "migration"}
VERIFICATION_STATUSES = {"passed", "not-applicable"}
REQUIRED_CI_FIELDS = {"runId", "runAttempt", "conclusion"}
METADATA_FIELDS = {"schemaVersion", "gitSha", "sourceRef", "runId", "runAttempt", "generatedAt"}
SPEC_FIELDS = {
    "sourceTreeDigest",
    "toolchainLockDigest",
    "changelogDigest",
    "nacosSourceBundleDigest",
    "migration",
    "images",
    "plugins",
    "verification",
}
IMAGE_FIELDS = {"reference", "sbom", "provenance"}
ROOT_FIELDS = {"apiVersion", "kind", "metadata", "spec"}
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
GHCR_DIGEST_RE = re.compile(r"^ghcr\.io/[^@\s]+@sha256:[0-9a-f]{64}$")
OCI_GHCR_DIGEST_RE = re.compile(r"^oci://ghcr\.io/[^@\s]+@sha256:[0-9a-f]{64}$")
IMAGE_NAMESPACE = "ghcr.io/lixud"


def read_json(path: str | None, default: Any) -> Any:
    if not path:
        return default
    return json.loads(Path(path).read_text(encoding="utf-8"))


def canonical_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return "sha256:" + hashlib.sha256(value).hexdigest()


def validate(manifest: dict[str, Any], *, require_required_ci: bool = False) -> list[str]:
    errors: list[str] = []
    unknown_root_fields = sorted(set(manifest) - ROOT_FIELDS)
    if unknown_root_fields:
        errors.append("manifest contains forbidden fields: " + ", ".join(unknown_root_fields))
    if manifest.get("apiVersion") != "cicd.data-manager-hub/v1":
        errors.append("apiVersion must be cicd.data-manager-hub/v1")
    if manifest.get("kind") != "BuildManifest":
        errors.append("kind must be BuildManifest")
    metadata = manifest.get("metadata", {})
    if not isinstance(metadata, dict):
        return errors + ["metadata must be an object"]
    unknown_metadata_fields = sorted(set(metadata) - METADATA_FIELDS)
    if unknown_metadata_fields:
        errors.append("metadata contains forbidden fields: " + ", ".join(unknown_metadata_fields))
    if metadata.get("schemaVersion") != SCHEMA_VERSION:
        errors.append("metadata.schemaVersion must be 1")
    git_sha = str(metadata.get("gitSha", ""))
    if not re.fullmatch(r"[0-9a-f]{40}", git_sha):
        errors.append("metadata.gitSha must be a 40-character SHA")
    if metadata.get("sourceRef") != "refs/heads/master":
        errors.append("metadata.sourceRef must be refs/heads/master")
    run_id = str(metadata.get("runId", ""))
    if not re.fullmatch(r"[1-9][0-9]*", run_id):
        errors.append("metadata.runId must be a positive numeric GitHub run id")
    run_attempt = metadata.get("runAttempt")
    if isinstance(run_attempt, bool) or not isinstance(run_attempt, int) or run_attempt < 1:
        errors.append("metadata.runAttempt must be a positive integer")
    generated_at = str(metadata.get("generatedAt", ""))
    try:
        parsed_generated_at = dt.datetime.fromisoformat(generated_at.replace("Z", "+00:00"))
        if parsed_generated_at.tzinfo is None:
            errors.append("metadata.generatedAt must include a timezone")
    except ValueError:
        errors.append("metadata.generatedAt must be an ISO-8601 timestamp")
    spec = manifest.get("spec", {})
    if not isinstance(spec, dict):
        return errors + ["spec must be an object"]
    unknown_spec_fields = sorted(set(spec) - SPEC_FIELDS)
    if unknown_spec_fields:
        errors.append("spec contains forbidden fields: " + ", ".join(unknown_spec_fields))
    for key in ("sourceTreeDigest", "toolchainLockDigest", "changelogDigest", "nacosSourceBundleDigest"):
        if not DIGEST_RE.fullmatch(str(spec.get(key, ""))):
            errors.append(f"spec.{key} must be a sha256 digest")
    images = spec.get("images", {})
    if not isinstance(images, dict):
        return errors + ["spec.images must be an object"]
    if set(images) != EXPECTED_COMPONENTS:
        errors.append(f"spec.images must contain exactly {sorted(EXPECTED_COMPONENTS)}")
    for name, image in images.items():
        if not isinstance(image, dict):
            errors.append(f"image {name} must be an object")
            continue
        unknown_image_fields = sorted(set(image) - IMAGE_FIELDS)
        if unknown_image_fields:
            errors.append(f"image {name} contains forbidden fields: " + ", ".join(unknown_image_fields))
        reference = str(image.get("reference", ""))
        expected_image_prefix = f"{IMAGE_NAMESPACE}/data-manager-hub-{name}"
        if not GHCR_DIGEST_RE.fullmatch(reference) or not reference.startswith(expected_image_prefix + "@"):
            errors.append(f"image {name} must use {expected_image_prefix}@sha256:<64 hex>")
        expected_sbom_prefix = expected_image_prefix + "-sbom"
        expected_provenance_prefix = expected_image_prefix
        for field in ("sbom", "provenance"):
            value = str(image.get(field, ""))
            expected_prefix = expected_sbom_prefix if field == "sbom" else expected_provenance_prefix
            if not OCI_GHCR_DIGEST_RE.fullmatch(value) or not value.startswith("oci://" + expected_prefix + "@"):
                errors.append(f"image {name}.{field} must use oci://{expected_prefix}@sha256:<64 hex>")
    plugins = spec.get("plugins", [])
    if not isinstance(plugins, list):
        errors.append("spec.plugins must be a list")
        plugins = []
    plugin_ids: set[str] = set()
    allowed_plugin_fields = {
        "apiVersion",
        "kind",
        "id",
        "version",
        "repository",
        "sha256",
        "signatureSha256",
        "signatureFingerprint",
        "signingKeyFingerprint",
    }
    for plugin in plugins:
        if not isinstance(plugin, dict):
            errors.append("plugin receipt must be an object")
            continue
        if plugin.get("apiVersion") != "cicd.data-manager-hub/v1" or plugin.get("kind") != "PluginReceipt":
            errors.append(f"plugin {plugin.get('id', '<unknown>')} must be a cicd.data-manager-hub/v1 PluginReceipt")
        unknown_plugin_fields = sorted(set(plugin) - allowed_plugin_fields)
        if unknown_plugin_fields:
            errors.append(
                f"plugin {plugin.get('id', '<unknown>')} contains forbidden fields: "
                + ", ".join(unknown_plugin_fields)
            )
        for field in ("id", "version", "repository"):
            if not str(plugin.get(field, "")):
                errors.append(f"plugin {plugin.get('id', '<unknown>')} missing {field}")
        plugin_id = str(plugin.get("id", ""))
        if plugin_id and plugin_id in plugin_ids:
            errors.append(f"duplicate plugin receipt id: {plugin_id}")
        plugin_ids.add(plugin_id)
        if not plugin.get("signatureFingerprint") and not plugin.get("signingKeyFingerprint"):
            errors.append(f"plugin {plugin.get('id', '<unknown>')} missing signatureFingerprint")
        for field in ("sha256", "signatureSha256"):
            if not DIGEST_RE.fullmatch(str(plugin.get(field, ""))):
                errors.append(f"plugin {plugin.get('id', '<unknown>')}.{field} must be a digest")
    migration = spec.get("migration", {})
    if not isinstance(migration, dict):
        errors.append("spec.migration must be an object")
        migration = {}
    migration_mode = str(migration.get("mode", ""))
    unknown_migration_fields = sorted(set(migration) - {"latestVersion", "mode"})
    if unknown_migration_fields:
        errors.append("spec.migration contains forbidden fields: " + ", ".join(unknown_migration_fields))
    if migration_mode not in {"NONE", "FORWARD"}:
        errors.append("spec.migration.mode must be NONE or FORWARD")
    if not re.fullmatch(r"V[0-9]+", str(migration.get("latestVersion", ""))):
        errors.append("spec.migration.latestVersion must be a V-number")
    verification = spec.get("verification", {})
    if not isinstance(verification, dict):
        errors.append("spec.verification must be an object")
        verification = {}
    unknown_verification_fields = sorted(set(verification) - VERIFICATION_FIELDS - {"requiredCi"})
    if unknown_verification_fields:
        errors.append(
            "spec.verification contains forbidden fields: " + ", ".join(unknown_verification_fields)
        )
    for field in VERIFICATION_FIELDS:
        if field not in verification:
            errors.append(f"spec.verification.{field} must be present")
        elif verification[field] not in VERIFICATION_STATUSES:
            errors.append(f"spec.verification.{field} must be passed or not-applicable")
    required_ci = verification.get("requiredCi")
    if required_ci is not None:
        if not isinstance(required_ci, dict):
            errors.append("spec.verification.requiredCi must be an object")
        else:
            unknown_required_ci_fields = sorted(set(required_ci) - REQUIRED_CI_FIELDS)
            if unknown_required_ci_fields:
                errors.append(
                    "spec.verification.requiredCi contains forbidden fields: "
                    + ", ".join(unknown_required_ci_fields)
                )
            missing_required_ci_fields = sorted(REQUIRED_CI_FIELDS - set(required_ci))
            for field in missing_required_ci_fields:
                errors.append(f"spec.verification.requiredCi.{field} must be present")
            required_ci_run_id = str(required_ci.get("runId", ""))
            if not re.fullmatch(r"[1-9][0-9]*", required_ci_run_id):
                errors.append("spec.verification.requiredCi.runId must be a positive numeric GitHub run id")
            run_attempt = required_ci.get("runAttempt")
            if isinstance(run_attempt, bool) or not isinstance(run_attempt, int) or run_attempt < 1:
                errors.append("spec.verification.requiredCi.runAttempt must be a positive integer")
            if required_ci.get("conclusion") != "success":
                errors.append("spec.verification.requiredCi.conclusion must be success")
    if require_required_ci and not isinstance(required_ci, dict):
        if required_ci is None:
            errors.append("spec.verification.requiredCi must be present before deployment")
    return errors


def create(args: argparse.Namespace) -> int:
    images = read_json(args.images, {})
    plugins = read_json(args.plugins, [])
    evidence = read_json(args.evidence, {})
    manifest = {
        "apiVersion": "cicd.data-manager-hub/v1",
        "kind": "BuildManifest",
        "metadata": {
            "schemaVersion": SCHEMA_VERSION,
            "gitSha": args.git_sha,
            "sourceRef": args.source_ref,
            "runId": str(args.run_id),
            "runAttempt": int(args.run_attempt),
            "generatedAt": args.generated_at,
        },
        "spec": {
            "sourceTreeDigest": args.source_tree_digest,
            "toolchainLockDigest": args.toolchain_lock_digest,
            "changelogDigest": args.changelog_digest,
            "nacosSourceBundleDigest": args.nacos_digest,
            "migration": {"latestVersion": args.latest_migration, "mode": args.migration_mode},
            "images": images,
            "plugins": plugins,
            "verification": evidence,
        },
    }
    errors = validate(manifest)
    if errors:
        for error in errors:
            print(f"manifest error: {error}")
        return 2
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    encoded = canonical_bytes(manifest)
    output.write_bytes(encoded)
    print(json.dumps({"manifestDigest": sha256_bytes(encoded), "path": str(output)}, sort_keys=True))
    return 0


def verify(args: argparse.Namespace) -> int:
    path = Path(args.manifest)
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"manifest error: cannot read JSON: {exc}")
        return 2
    if not isinstance(manifest, dict):
        print("manifest error: root must be an object")
        return 2
    errors = validate(manifest, require_required_ci=args.require_required_ci)
    actual_digest = sha256_bytes(canonical_bytes(manifest))
    if args.expected_digest and actual_digest != args.expected_digest:
        errors.append(f"digest mismatch: expected {args.expected_digest}, got {actual_digest}")
    if errors:
        for error in errors:
            print(f"manifest error: {error}")
        return 2
    print(json.dumps({"manifestDigest": actual_digest, "gitSha": manifest["metadata"]["gitSha"]}, sort_keys=True))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    create_parser = subparsers.add_parser("create")
    create_parser.add_argument("--git-sha", required=True)
    create_parser.add_argument("--source-ref", default="refs/heads/master")
    create_parser.add_argument("--run-id", required=True)
    create_parser.add_argument("--run-attempt", default="1")
    create_parser.add_argument("--generated-at", required=True)
    create_parser.add_argument("--source-tree-digest", required=True)
    create_parser.add_argument("--toolchain-lock-digest", required=True)
    create_parser.add_argument("--changelog-digest", required=True)
    create_parser.add_argument("--nacos-digest", required=True)
    create_parser.add_argument("--latest-migration", required=True)
    create_parser.add_argument("--migration-mode", required=True)
    create_parser.add_argument("--images", required=True)
    create_parser.add_argument("--plugins", required=True)
    create_parser.add_argument("--evidence", required=True)
    create_parser.add_argument("--output", required=True)
    create_parser.set_defaults(handler=create)
    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("--manifest", required=True)
    verify_parser.add_argument("--expected-digest")
    verify_parser.add_argument("--require-required-ci", action="store_true")
    verify_parser.set_defaults(handler=verify)
    args = parser.parse_args()
    return args.handler(args)


if __name__ == "__main__":
    raise SystemExit(main())
