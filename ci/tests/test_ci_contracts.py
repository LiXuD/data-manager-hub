from __future__ import annotations

import datetime as dt
import hashlib
import http.server
import json
import os
import socket
import subprocess
import tempfile
import threading
import urllib.parse
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "ci" / "scripts"


def run_script(name: str, *args: str, expect: int = 0, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        ["python3", str(SCRIPTS / name), *args],
        cwd=ROOT,
        text=True,
        capture_output=True,
        env=env,
    )
    if result.returncode != expect:
        raise AssertionError(f"{name}: expected {expect}, got {result.returncode}\nstdout={result.stdout}\nstderr={result.stderr}")
    return result


class CiContractTests(unittest.TestCase):
    def test_github_readiness_audit_checks_external_protection_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fake_gh = root / "gh"
            fake_gh.write_text(
                "#!/usr/bin/env python3\n"
                "import base64, json, os, sys\n"
                "endpoint = sys.argv[-1]\n"
                "paginate = '--paginate' in sys.argv\n"
                "mode = os.environ.get('FAKE_GH_MODE', '')\n"
                "protection = {\n"
                "  'required_status_checks': {'strict': True, 'contexts': ['CI / required-ci'], 'checks': [{'context': 'CI / required-ci'}]},\n"
                "  'required_pull_request_reviews': {'dismiss_stale_reviews': True, 'require_code_owner_reviews': True, 'required_approving_review_count': 1},\n"
                "  'allow_force_pushes': {'enabled': False}, 'allow_deletions': {'enabled': False}\n"
                "}\n"
                "if mode == 'bad-context': protection['required_status_checks']['contexts'] = {'unexpected': True}\n"
                "if mode == 'stale-approval': protection['required_pull_request_reviews']['dismiss_stale_reviews'] = False\n"
                "environments = []\n"
                "for name in ('dev', 'staging', 'production', 'plugin-signing'):\n"
                "  protected = name in ('staging', 'production', 'plugin-signing')\n"
                "  environments.append({'name': name, 'protection_rules': ([{'type': 'required_reviewers', 'reviewers': [{'login': 'release-owner'}]}] if protected else []), 'deployment_branch_policy': ({'protected_branches': True, 'custom_branch_policies': False} if protected else {})})\n"
                "runners = {'total_count': 1, 'runners': [{'status': 'online', 'labels': [{'name': 'nonprod-deploy'}, {'name': 'prod-deploy'}, {'name': 'plugin-signing'}]}]}\n"
                "if endpoint == 'repos/acme/demo': response = {'default_branch': 'master'}\n"
                "elif endpoint == 'repos/acme/demo/branches/master/protection': response = protection\n"
                "elif endpoint.startswith('repos/acme/demo/contents/.github/CODEOWNERS'): encoded = base64.b64encode(b'/ci/ @platform\\n/deploy/ @platform\\n/docker/ @platform\\n/nacos-config/ @platform\\n/observability/ @platform\\n').decode(); response = {'content': encoded[:8] + '\\n' + encoded[8:]}\n"
                "elif endpoint == 'repos/acme/demo/environments': response = {'total_count': len(environments), 'environments': environments}\n"
                "elif endpoint.startswith('repos/acme/demo/environments/') and endpoint.endswith('/secrets'): secret_environment = endpoint.split('/')[4]; secrets = {'staging': ['DMH_PROMETHEUS_URL', 'DMH_PROMETHEUS_BEARER_TOKEN'], 'production': ['DMH_PRODUCTION_DB_INSTANCE', 'DMH_SNAPSHOT_ADAPTER_BIN', 'DMH_SNAPSHOT_SIGNATURE_VERIFIER', 'DMH_PROMETHEUS_URL', 'DMH_PROMETHEUS_BEARER_TOKEN'], 'plugin-signing': ['DMH_PLUGIN_SIGNING_ADAPTER']}.get(secret_environment, []); secrets = secrets[:-1] if mode == 'missing-secret' and secret_environment == 'production' else secrets; response = {'total_count': len(secrets), 'secrets': [{'name': name} for name in secrets]}\n"
                "elif endpoint == 'repos/acme/demo/actions/runners': response = runners\n"
                "else: raise SystemExit('unexpected endpoint: ' + endpoint)\n"
                "print(json.dumps([response] if paginate else response))\n",
                encoding="utf-8",
            )
            fake_gh.chmod(fake_gh.stat().st_mode | 0o111)
            env = os.environ.copy()
            env["PATH"] = f"{root}:{env['PATH']}"
            evidence = root / "github-readiness.json"
            result = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "verify-github-readiness.py"),
                    "--repository",
                    "acme/demo",
                    "--output",
                    str(evidence),
                ],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            payload = json.loads(evidence.read_text(encoding="utf-8"))
            self.assertEqual(payload["requiredCheck"], "CI / required-ci")
            self.assertEqual(payload["requiredApprovals"], 1)
            self.assertEqual(payload["onlineRunnerLabels"], ["nonprod-deploy", "plugin-signing", "prod-deploy"])
            self.assertEqual(payload["environments"], ["dev", "plugin-signing", "production", "staging"])
            self.assertIn("DMH_SNAPSHOT_ADAPTER_BIN", payload["environmentSecrets"]["production"])

            env["FAKE_GH_MODE"] = "bad-context"
            malformed = subprocess.run(
                ["python3", str(SCRIPTS / "verify-github-readiness.py"), "--repository", "acme/demo"],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(malformed.returncode, 2)
            self.assertIn("contexts must be a list of strings", malformed.stderr)

            env["FAKE_GH_MODE"] = "stale-approval"
            unprotected = subprocess.run(
                ["python3", str(SCRIPTS / "verify-github-readiness.py"), "--repository", "acme/demo"],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(unprotected.returncode, 2)
            self.assertIn("dismiss stale approvals", unprotected.stderr)

            env["FAKE_GH_MODE"] = "missing-secret"
            missing_secret = subprocess.run(
                ["python3", str(SCRIPTS / "verify-github-readiness.py"), "--repository", "acme/demo"],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(missing_secret.returncode, 2)
            self.assertIn("required secret names are missing", missing_secret.stderr)

    def test_workflow_contract_validator_parses_all_run_blocks(self) -> None:
        result = subprocess.run(
            ["python3", str(SCRIPTS / "check-workflows.py")],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("6 files", result.stdout)
        self.assertIn("82 run blocks", result.stdout)

    def test_ci_and_release_matrices_match_runtime_contract(self) -> None:
        result = subprocess.run(
            ["python3", str(SCRIPTS / "check-workflow-matrices.py")],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("9 components", result.stdout)

    def test_markdown_links_and_local_anchors_are_valid(self) -> None:
        result = subprocess.run(
            ["python3", str(SCRIPTS / "check-markdown-links.py")],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("markdown relative links and local anchors passed", result.stdout)

    def test_namespace_rbac_contract_rejects_cluster_scope_and_covers_chart_resources(self) -> None:
        result = subprocess.run(
            ["python3", str(SCRIPTS / "verify-rbac.py")],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        role = (ROOT / "deploy/rbac/base/role.yaml").read_text(encoding="utf-8")
        serviceaccounts = (ROOT / "deploy/rbac/base/serviceaccounts.yaml").read_text(encoding="utf-8")
        self.assertNotIn("ClusterRole", role)
        self.assertIn("networkpolicies", role)
        self.assertIn("poddisruptionbudgets", role)
        self.assertIn("name: dmh-deployer", serviceaccounts)
        self.assertIn("name: dmh-runtime", serviceaccounts)
        self.assertIn("name: dmh-deployer", serviceaccounts)
        self.assertIn("automountServiceAccountToken: true", serviceaccounts)
        self.assertIn("name: dmh-runtime", serviceaccounts)
        self.assertIn("automountServiceAccountToken: false", serviceaccounts)

    def test_admission_verifier_requires_namespace_and_host_boundary_guards(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            admission = root / "deploy/admission"
            admission.mkdir(parents=True)
            policy = (ROOT / "deploy/admission/job-secret-boundary.yaml").read_text(encoding="utf-8")
            admission.joinpath("job-secret-boundary.yaml").write_text(
                policy.replace("hostPID", "hostPidMissing"), encoding="utf-8"
            )
            result = subprocess.run(
                ["python3", str(SCRIPTS / "verify-admission-policy.py")],
                cwd=root,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
            self.assertIn("hostPID", result.stderr)

    def test_access_rollout_readiness_is_backed_by_nacos_health_group(self) -> None:
        result = subprocess.run(
            ["python3", str(SCRIPTS / "verify-contracts.py")],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        access_template = (ROOT / "deploy/helm/data-manager-hub/templates/access-statefulset.yaml").read_text(
            encoding="utf-8"
        )
        self.assertIn("/actuator/health/readiness", access_template)
        for path in (
            ROOT / "nacos-config/dev/data-platform-access-dev.yml",
            ROOT / "nacos-config/prod/data-platform-access-prod.yml",
        ):
            config = path.read_text(encoding="utf-8")
            self.assertIn("management.endpoint.health.group.readiness.include", config)
            self.assertIn("connectorRuntimeReadiness", config)

    def test_policy_rejects_future_dated_waiver(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            waiver = Path(directory) / "future-waiver.yaml"
            tomorrow = dt.date.today() + dt.timedelta(days=1)
            waiver.write_text(
                "schemaVersion: 1\n"
                "waivers:\n"
                "  - id: future\n"
                "    owner: release\n"
                "    reason: test\n"
                f"    createdAt: {tomorrow.isoformat()}\n"
                f"    expiresAt: {(tomorrow + dt.timedelta(days=1)).isoformat()}\n",
                encoding="utf-8",
            )
            result = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "verify-policy-files.py"),
                    "--base-ref",
                    "HEAD",
                    "--waiver",
                    str(waiver),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
            self.assertIn("createdAt cannot be in the future", result.stderr)

    def test_ghcr_retention_contract_requires_non_deletion_rules(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = (ROOT / "ci/policy/ghcr-retention.yaml").read_text(encoding="utf-8")
            fixture = Path(directory) / "retention.yaml"
            fixture.write_text(source.split("rules:\n", 1)[0] + "rules: []\n", encoding="utf-8")
            result = subprocess.run(
                ["python3", str(SCRIPTS / "verify-ghcr-retention.py"), str(fixture)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
            self.assertIn("retention rules are missing", result.stderr)

    def test_workflow_contract_validator_rejects_pull_request_target(self) -> None:
        workflow_dir = ROOT / ".github" / "workflows"
        fixture = workflow_dir / "zz-test-pull-request-target.yml"
        fixture.write_text(
            "name: forbidden fixture\n"
            "on:\n"
            "  pull_request_target:\n"
            "jobs:\n"
            "  test:\n"
            "    runs-on: ubuntu-latest\n"
            "    steps:\n"
            "      - run: true\n",
            encoding="utf-8",
        )
        try:
            result = subprocess.run(
                ["python3", str(SCRIPTS / "check-workflows.py")],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
            self.assertIn("pull_request_target is forbidden", result.stderr)
        finally:
            fixture.unlink(missing_ok=True)

    def test_workflow_contract_validator_rejects_duplicate_yaml_keys(self) -> None:
        workflow_dir = ROOT / ".github" / "workflows"
        fixture = workflow_dir / "zz-test-duplicate-key.yml"
        fixture.write_text(
            "name: duplicate key fixture\n"
            "name: attacker-overwrite\n"
            "on:\n"
            "  workflow_dispatch:\n"
            "jobs:\n"
            "  test:\n"
            "    runs-on: ubuntu-24.04\n"
            "    steps:\n"
            "      - run: true\n",
            encoding="utf-8",
        )
        try:
            result = subprocess.run(
                ["python3", str(SCRIPTS / "check-workflows.py")],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
            self.assertIn("duplicate key", result.stderr)
        finally:
            fixture.unlink(missing_ok=True)

    def test_workflow_contract_validator_rejects_self_hosted_pull_request_job(self) -> None:
        workflow_dir = ROOT / ".github" / "workflows"
        fixture = workflow_dir / "zz-test-self-hosted-pr.yml"
        fixture.write_text(
            "name: forbidden fixture\n"
            "on:\n"
            "  pull_request:\n"
            "jobs:\n"
            "  test:\n"
            "    runs-on: self-hosted\n"
            "    steps:\n"
            "      - run: true\n",
            encoding="utf-8",
        )
        try:
            result = subprocess.run(
                ["python3", str(SCRIPTS / "check-workflows.py")],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
            self.assertIn("must not schedule self-hosted runners", result.stderr)
        finally:
            fixture.unlink(missing_ok=True)

    def test_maven_wrapper_does_not_bypass_checksum_locked_distribution(self) -> None:
        wrapper = (ROOT / "mvnw").read_text(encoding="utf-8")
        properties = (ROOT / ".mvn/wrapper/maven-wrapper.properties").read_text(encoding="utf-8")
        self.assertIn("distributionSha256Sum=", properties)
        self.assertIn("actual_sha", wrapper)
        self.assertNotIn("command -v mvn", wrapper)

    def test_actionlint_is_digest_locked_and_required(self) -> None:
        lock = (ROOT / "ci/toolchain.lock.yaml").read_text(encoding="utf-8")
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("actionlint:\n    version: 1.7.12", lock)
        self.assertIn("rhysd/actionlint", lock)
        self.assertIn("promtool:\n    version: 2.54.0", lock)
        self.assertIn("prom/prometheus", lock)
        self.assertIn("rhysd/actionlint@sha256:", workflow)
        self.assertIn("Validate workflows with locked actionlint", workflow)
        self.assertIn("prom/prometheus@sha256:", workflow)
        self.assertIn("Validate Prometheus rules with locked promtool", workflow)

    def test_toolchain_lock_covers_every_runtime_tool_version(self) -> None:
        lock = (ROOT / "ci/toolchain.lock.yaml").read_text(encoding="utf-8")
        for entry in (
            "maven:\n  version: 3.9.15",
            "node:\n  version: 22.19.0",
            "npm:\n  version: 10.9.3",
            "helm:\n  version: 3.19.0",
            "kubectl:\n  version: 1.33.0",
        ):
            self.assertIn(entry, lock)
        run_script("verify-toolchain-lock.py")

    def test_codeowners_protects_ci_supply_chain_inputs(self) -> None:
        result = subprocess.run(
            ["python3", str(SCRIPTS / "verify-policy-files.py"), "--base-ref", "origin/master"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        codeowners = (ROOT / ".github/CODEOWNERS").read_text(encoding="utf-8")
        for pattern in ("/.mvn/", "/pom.xml", "/mvnw", "/ci/", "/deploy/", "/docker/", "/nacos-config/", "/observability/"):
            self.assertIn(pattern, codeowners)

    def test_production_namespace_mapping_is_explicit(self) -> None:
        workflow = (ROOT / ".github" / "workflows" / "_deploy-reusable.yml").read_text(encoding="utf-8")
        self.assertIn("DEPLOY_NAMESPACE: dmh-${{ inputs.environment == 'production' && 'prod' || inputs.environment }}", workflow)
        self.assertIn("ghcr.io/lixud/data-manager-hub-build-manifest@", workflow)
        build = (ROOT / ".github" / "workflows" / "build-release.yml").read_text(encoding="utf-8")
        self.assertIn("IMAGE_NAMESPACE: ghcr.io/lixud", build)
        self.assertIn("cp \"$receipt\" snapshot-receipt.json", workflow)
        self.assertIn("snapshotReceiptDigest", workflow)
        self.assertIn("snapshot-receipt-${{ inputs.environment }}-${{ github.run_id }}", workflow)
        self.assertGreaterEqual(workflow.count("migration_mode=UNKNOWN"), 2)
        self.assertGreaterEqual(workflow.count("if [[ -s release-manifest/build-manifest.v1.json ]]"), 2)
        self.assertIn("HELM_DRIVER: configmap", workflow)
        self.assertIn("manifest_digest must be sha256:<64 lowercase hex>", workflow)
        self.assertIn('oci_layer_digest=', workflow)
        self.assertIn('canonical_layer_digest=', workflow)
        self.assertIn('oras manifest fetch --descriptor "$reference"', workflow)
        self.assertIn('oras manifest fetch --descriptor "$sbom_ref"', workflow)
        self.assertIn("production may use only the prod-deploy Runner Group", workflow)
        self.assertIn("release_version is only valid for production", workflow)
        self.assertIn("snapshot_not_before:", workflow)
        self.assertIn('--not-before "$SNAPSHOT_NOT_BEFORE"', workflow)
        self.assertIn("Confirm checkout is the requested commit", workflow)
        self.assertIn("Upload pre-release SLO baseline", workflow)
        self.assertIn("name: Run release smoke gates\n        if: inputs.environment != 'dev'\n        env:\n          ENVIRONMENT: ${{ inputs.environment }}", workflow)
        production = (ROOT / ".github" / "workflows" / "promote-production.yml").read_text(encoding="utf-8")
        self.assertIn("group: production-release", production)
        self.assertIn("staging_completed_at", production)
        self.assertIn("snapshot_not_before: ${{ needs.resolve-staging.outputs.staging_completed_at }}", production)
        self.assertIn("Publish immutable OCI SemVer aliases", production)
        self.assertIn("publish-immutable-oci-aliases.sh", production)
        staging = (ROOT / ".github" / "workflows" / "promote-staging.yml").read_text(encoding="utf-8")
        self.assertIn("sort_by(.updated_at // .created_at)", staging)
        self.assertIn("sort_by(.updated_at // .created_at)", production)

    def test_snapshot_verifier_stays_on_protected_runner(self) -> None:
        contract = (ROOT / "ci/contracts/runtime-contract.v1.yaml").read_text(encoding="utf-8")
        self.assertIn("secretRefs: [dmh-runtime]", contract)
        self.assertIn("runnerSecretRefs: [dmh-snapshot-verifier]", contract)
        job_script = (SCRIPTS / "create-private-job.sh").read_text(encoding="utf-8")
        self.assertIn("dbops,dmh-runtime|acceptance,dmh-runtime,dmh-acceptance", job_script)
        self.assertNotIn("dmh-snapshot-verifier", job_script)

    def test_release_write_permissions_are_job_scoped(self) -> None:
        build = (ROOT / ".github" / "workflows" / "build-release.yml").read_text(encoding="utf-8")
        self.assertIn("permissions:\n  contents: read", build)
        self.assertIn("packages: write\n      id-token: write\n      attestations: write", build)
        self.assertIn("deployments: write\n    uses: ./.github/workflows/_deploy-reusable.yml", build)
        production = (ROOT / ".github" / "workflows" / "promote-production.yml").read_text(encoding="utf-8")
        self.assertIn("permissions:\n  contents: read", production)
        self.assertIn("  attestations: read\n  deployments: write", production)
        self.assertIn("permissions:\n      contents: write\n      packages: write\n      deployments: write", production)
        staging = (ROOT / ".github" / "workflows" / "promote-staging.yml").read_text(encoding="utf-8")
        self.assertIn("  attestations: read\n  deployments: write", staging)
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        self.assertIn("permissions:\n      contents: read\n      security-events: write", ci)

    def test_scheduled_security_runs_full_codeql_scan(self) -> None:
        workflow = (ROOT / ".github" / "workflows" / "scheduled-security.yml").read_text(encoding="utf-8")
        self.assertIn("CodeQL initialize (nightly full scan)", workflow)
        self.assertIn("CodeQL autobuild (nightly full scan)", workflow)
        self.assertIn("CodeQL analyze (nightly full scan)", workflow)
        self.assertIn("languages: java,javascript-typescript", workflow)

    def test_semver_release_policy_is_monotonic(self) -> None:
        script = SCRIPTS / "verify-semver-bump.py"
        first = subprocess.run(
            ["python3", str(script), "--candidate", "v1.0.0", "--tags-json", "[]"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(first.returncode, 0, first.stderr)
        rejected = subprocess.run(
            [
                "python3",
                str(script),
                "--candidate",
                "v1.2.0",
                "--tags-json",
                '["v1.3.0", "not-a-release"]',
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(rejected.returncode, 2, rejected.stdout + rejected.stderr)
        production = (ROOT / ".github" / "workflows" / "promote-production.yml").read_text(encoding="utf-8")
        self.assertIn("verify-semver-bump.py", production)
        self.assertIn("tags_json=", production)
        self.assertIn("gh api --include", production)
        self.assertIn('[[ "$status" == 404 ]]', production)

    def test_docker_ci_verifies_base_image_platforms(self) -> None:
        workflow = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        self.assertIn("Verify locked base image platforms", workflow)
        self.assertIn("check-base-image-platforms.py", workflow)

    def test_dependency_manifests_trigger_security_gate(self) -> None:
        classifier = (SCRIPTS / "classify-changes.sh").read_text(encoding="utf-8")
        self.assertIn("data-platform-web/package.json|data-platform-web/package-lock.json", classifier)
        self.assertIn("*/pom.xml|pom.xml", classifier)
        self.assertIn("frontend=true; security=true; deployability=true", classifier)

    def test_source_and_docs_classification_have_distinct_deployability(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "ci@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "CI"], cwd=root, check=True)
            (root / "README.md").write_text("base\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=root, check=True)

            (root / "README.md").write_text("docs\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "docs"], cwd=root, check=True)
            docs_result = subprocess.run(
                ["bash", str(SCRIPTS / "classify-changes.sh")],
                cwd=root,
                env={
                    **os.environ,
                    "BASE_REF": "HEAD^",
                    "GITHUB_EVENT_NAME": "pull_request",
                    "CLASSIFICATION_FILE": str(root / "classification.env"),
                },
                text=True,
                capture_output=True,
                check=True,
            )
            docs_values = dict(line.split("=", 1) for line in docs_result.stdout.splitlines() if "=" in line)
            self.assertEqual(docs_values["source_change"], "false")
            self.assertEqual(docs_values["deployability"], "false")
            classification_values = dict(
                line.split("=", 1) for line in (root / "classification.env").read_text(encoding="utf-8").splitlines()
            )
            self.assertEqual(classification_values["source_sha"], subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip())
            self.assertEqual(classification_values["base_ref"], "HEAD^")

            github_output = root / "github-output"
            output_result = subprocess.run(
                ["bash", str(SCRIPTS / "classify-changes.sh")],
                cwd=root,
                env={
                    **os.environ,
                    "BASE_REF": "HEAD^",
                    "GITHUB_EVENT_NAME": "push",
                    "GITHUB_OUTPUT": str(github_output),
                },
                text=True,
                capture_output=True,
                check=True,
            )
            self.assertIn("source_change=false", output_result.stdout)
            output_values = dict(line.split("=", 1) for line in github_output.read_text(encoding="utf-8").splitlines())
            self.assertEqual(output_values["base_ref"], "HEAD^")
            self.assertEqual(output_values["source_sha"], classification_values["source_sha"])

            (root / "src").mkdir()
            (root / "src/Main.java").write_text("class Main {}\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "source"], cwd=root, check=True)
            source_result = subprocess.run(
                ["bash", str(SCRIPTS / "classify-changes.sh")],
                cwd=root,
                env={**os.environ, "BASE_REF": "HEAD^", "GITHUB_EVENT_NAME": "pull_request"},
                text=True,
                capture_output=True,
                check=True,
            )
            source_values = dict(line.split("=", 1) for line in source_result.stdout.splitlines() if "=" in line)
            self.assertEqual(source_values["source_change"], "true")
            self.assertEqual(source_values["backend"], "true")
            self.assertEqual(source_values["security"], "true")
            self.assertEqual(source_values["deployability"], "true")

    def test_zero_push_base_never_classifies_fail_open(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "ci@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "CI"], cwd=root, check=True)
            (root / "src.java").write_text("class Source {}\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "initial"], cwd=root, check=True)
            result = subprocess.run(
                ["bash", str(SCRIPTS / "classify-changes.sh")],
                cwd=root,
                env={
                    **os.environ,
                    "BASE_REF": "0000000000000000000000000000000000000000",
                    "GITHUB_EVENT_NAME": "push",
                },
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("refusing to classify fail-open", result.stderr)

    def test_dependency_scan_uses_named_nvd_secret_and_manages_runtime_postgres(self) -> None:
        pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
        self.assertIn("<nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>", pom)
        self.assertIn("<ossindexAnalyzerEnabled>false</ossindexAnalyzerEnabled>", pom)
        self.assertIn("<skipProvidedScope>true</skipProvidedScope>", pom)
        self.assertIn("<suppressionFiles>ci/policy/dependency-check-suppressions.xml</suppressionFiles>", pom)
        suppressions = (ROOT / "ci/policy/dependency-check-suppressions.xml").read_text(encoding="utf-8")
        self.assertIn("CVE-2026-53914", suppressions)
        self.assertIn("CVE-2026-66299", suppressions)
        self.assertIn("org\\.eclipse\\.angus/angus-activation@2\\.0\\.3", suppressions)
        self.assertIn("CVE-2025-7962", suppressions)
        self.assertIn("io\\.prometheus/.*", suppressions)
        self.assertIn("<artifactId>postgresql</artifactId>\n                <version>${postgresql.driver.version}</version>", pom)
        for property_name, version in {
            "spring-framework.version": "6.2.19",
            "spring-security.version": "6.5.11",
            "netty.version": "4.1.136.Final",
            "jackson.version": "2.18.9",
            "httpclient5.version": "5.6.4",
            "httpcore5.version": "5.4.3",
            "commons-lang3.version": "3.18.0",
            "tomcat.version": "10.1.57",
            "kafka.clients.version": "3.9.2",
            "log4j.version": "2.25.5",
            "commons-fileupload.version": "1.6.0",
            "nacos.logback-adapter.version": "1.1.5",
        }.items():
            self.assertIn(f"<{property_name}>{version}</{property_name}>", pom)

    def test_external_plugin_receipts_are_distinct_from_host_runtime_changes(self) -> None:
        classifier = (SCRIPTS / "classify-changes.sh").read_text(encoding="utf-8")
        workflow = (ROOT / ".github" / "workflows" / "build-release.yml").read_text(encoding="utf-8")
        self.assertIn("external_plugin=true", classifier)
        self.assertIn('external_plugin_gate="${{ needs.guard.outputs.external_plugin }}"', workflow)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "ci@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "CI"], cwd=root, check=True)
            subprocess.run(["git", "commit", "--allow-empty", "-qm", "initial"], cwd=root, check=True)
            (root / "data-platform-common-runtime").mkdir(parents=True)
            (root / "data-platform-common-runtime" / "Runtime.java").write_text("class Runtime {}\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=root, check=True)
            (root / "data-platform-common-runtime" / "Runtime.java").write_text("class Runtime { }\n", encoding="utf-8")
            result = subprocess.run(
                ["bash", str(SCRIPTS / "classify-changes.sh")],
                cwd=root,
                env={**os.environ, "BASE_REF": "HEAD^", "GITHUB_EVENT_NAME": "pull_request"},
                text=True,
                capture_output=True,
                check=True,
            )
            values = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
            self.assertEqual(values["plugin"], "true")
            self.assertEqual(values["external_plugin"], "false")
            (root / "plugins").mkdir()
            (root / "plugins" / "fixture.jar").write_bytes(b"plugin")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "plugin"], cwd=root, check=True)
            result = subprocess.run(
                ["bash", str(SCRIPTS / "classify-changes.sh")],
                cwd=root,
                env={**os.environ, "BASE_REF": "HEAD^", "GITHUB_EVENT_NAME": "pull_request"},
                text=True,
                capture_output=True,
                check=True,
            )
            values = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
            self.assertEqual(values["external_plugin"], "true")

    def test_build_release_does_not_publish_docs_only_manifest(self) -> None:
        workflow = (ROOT / ".github" / "workflows" / "build-release.yml").read_text(encoding="utf-8")
        self.assertIn("name: verify master CI result and source change", workflow)
        self.assertIn("docs-only change: no release images or manifest will be built", workflow)
        self.assertIn("ci-classification-${CI_RUN_ID}", workflow)
        self.assertIn("classification artifact is not bound to the workflow source SHA", workflow)
        self.assertIn('CLASSIFICATION_BASE_REF="${{ needs.guard.outputs.base_ref }}"', workflow)
        self.assertIn('else \"not-applicable\" end)', workflow)
        self.assertIn('plugin_args+=(--require)', workflow)
        self.assertIn("name: sign and publish external plugins", workflow)
        self.assertIn("environment: plugin-signing", workflow)
        self.assertIn("DMH_PLUGIN_SIGNING_ADAPTER", workflow)
        self.assertIn("plugin-receipts-${{ github.run_id }}", workflow)
        self.assertIn("needs: [guard, build, sign-plugin]", workflow)
        self.assertIn("CI / required-ci", workflow)
        self.assertIn("matrix.component }}@${{ steps.build.outputs.digest }}", workflow)
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        self.assertIn("name: CI / docker", ci)
        self.assertIn("docker buildx build", ci)
        for component in ("gateway", "masterdata", "access", "billing", "identity", "governance", "web", "dbops", "acceptance"):
            self.assertIn(f"- component: {component}", ci)
        self.assertIn("bash verify-v048-routing.sh", ci)
        self.assertIn("bash verify-v049-connector-product-spec.sh", ci)
        self.assertIn("bash verify-v050-generic-http.sh", ci)
        self.assertIn("needs: [classify, meta, backend, frontend, migration, security, deployability, docker]", ci)
        self.assertIn("source changes cannot mark every substantive gate not-applicable", ci)
        self.assertIn('"verification": evidence', (SCRIPTS / "manifest.py").read_text(encoding="utf-8"))
        self.assertIn('verify-oci-references.py', workflow)
        publisher = (SCRIPTS / "publish-immutable-manifest.sh").read_text(encoding="utf-8")
        self.assertIn('immutable Build Manifest tag already points at different content', publisher)
        self.assertIn('stable_ci="$(gh api', workflow)
        self.assertIn('ci_run_attempt=$stable_ci_run_attempt', workflow)
        self.assertIn('--run-id "$ci_run_id"', workflow)
        self.assertIn('--generated-at "$(git show -s --format=%cI "$SOURCE_SHA")"', workflow)
        deploy = (ROOT / ".github" / "workflows" / "_deploy-reusable.yml").read_text(encoding="utf-8")
        self.assertIn("name: Plan Nacos bundle offline", deploy)
        self.assertIn("--env NACOS_MODE=plan -- nacos", deploy)
        self.assertIn("base_ref: ${{ steps.classify.outputs.base_ref }}", ci)
        self.assertGreaterEqual(ci.count("BASE_REF: ${{ needs.classify.outputs.base_ref }}"), 4)
        self.assertIn("--env NACOS_MODE=apply -- nacos", deploy)
        self.assertIn("--env NACOS_MODE=verify -- nacos", deploy)
        self.assertIn('job="dmh-nacos-plan-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"', deploy)
        self.assertIn('job="dmh-nacos-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"', deploy)
        self.assertIn('job="dmh-nacos-verify-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"', deploy)
        self.assertIn('job="dmh-migration-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"', deploy)
        self.assertIn('job="dmh-acceptance-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"', deploy)

    def test_internal_auth_contract_has_runtime_paths_and_least_privilege_mounts(self) -> None:
        deployments = (ROOT / "deploy/helm/data-manager-hub/templates/deployments.yaml").read_text(encoding="utf-8")
        access = (ROOT / "deploy/helm/data-manager-hub/templates/access-statefulset.yaml").read_text(encoding="utf-8")
        self.assertIn("mountPath: /run/secrets/dmh/internal-auth", deployments)
        self.assertNotIn("Values.global.nacosServer", deployments)
        self.assertIn("key: public.pem", deployments)
        self.assertIn('if eq $name "identity"', deployments)
        self.assertIn("key: private.pem", deployments)
        self.assertIn("mountPath: /run/secrets/dmh/internal-auth", access)
        self.assertIn("key: public.pem", access)
        self.assertNotIn("key: private.pem", access)
        for path in (ROOT / "nacos-config/dev").glob("data-platform-*.yml"):
            content = path.read_text(encoding="utf-8")
            self.assertNotIn("public-key-location: file:__PROJECT_ROOT__", content)
            self.assertNotIn("private-key-location: file:__PROJECT_ROOT__", content)
        identity_prod = (ROOT / "nacos-config/prod/data-platform-identity-prod.yml").read_text(encoding="utf-8")
        self.assertIn("platform.encryption.master-key: ${PLATFORM_ENCRYPTION_MASTER_KEY}", identity_prod)

    def test_network_policy_allows_only_explicit_metrics_namespace(self) -> None:
        values = (ROOT / "deploy/helm/data-manager-hub/values.yaml").read_text(encoding="utf-8")
        policy = (ROOT / "deploy/helm/data-manager-hub/templates/networkpolicy.yaml").read_text(encoding="utf-8")
        self.assertIn("metricsNamespaceLabels", values)
        self.assertIn("Values.networkPolicy.metricsNamespaceLabels", policy)

    def test_non_dev_nacos_entrypoints_fail_closed_on_loopback(self) -> None:
        publish = (ROOT / "publish-nacos-config.sh").read_text(encoding="utf-8")
        java_entrypoint = (ROOT / "docker/java-entrypoint.sh").read_text(encoding="utf-8")
        dbops = (SCRIPTS / "dbops-entrypoint.sh").read_text(encoding="utf-8")
        self.assertIn("staging/prod 必须显式提供 NACOS_SERVER_ADDR", publish)
        self.assertIn('https://*)', publish)
        self.assertIn('不允许降级为 http', publish)
        self.assertIn("NACOS_SCHEME 只支持 http 或 https", publish)
        self.assertIn("NACOS_SERVER_ADDR must point to the environment Nacos service", java_entrypoint)
        self.assertIn("non-loopback NACOS_SERVER_ADDR", dbops)
        self.assertIn('"${NACOS_MODE:-apply}" != plan', dbops)
        self.assertIn('"${NACOS_CONFIG_DRY_RUN:-false}" != true', dbops)
        self.assertIn('! -d /tmp/maven-repo/org', dbops)
        self.assertIn('cp -a "$MAVEN_REPO_SEED"/. /tmp/maven-repo/', dbops)
        self.assertIn('MAVEN_REPO_SEED', (ROOT / "docker/dbops.Dockerfile").read_text(encoding="utf-8"))
        strict = (SCRIPTS / "strict-migration.sh").read_text(encoding="utf-8")
        self.assertIn('MAVEN_OFFLINE="${MAVEN_OFFLINE:-false}"', strict)
        self.assertIn('maven_args=(-o "${maven_args[@]}")', strict)
        self.assertIn('MAVEN_OFFLINE=true', (ROOT / "docker/dbops.Dockerfile").read_text(encoding="utf-8"))

    def test_acceptance_image_has_portable_offline_test_contract(self) -> None:
        dockerfile = (ROOT / "docker/acceptance.Dockerfile").read_text(encoding="utf-8")
        pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
        self.assertIn("dependency:go-offline", dockerfile)
        self.assertIn("dependency:resolve-plugins", dockerfile)
        self.assertIn("surefire-junit-platform:3.5.4", dockerfile)
        self.assertIn("surefire-junit-platform:3.2.5", dockerfile)
        self.assertIn("junit-platform-launcher:1.12.2", dockerfile)
        self.assertIn("find /tmp/maven-repo/repository -name _remote.repositories -delete", dockerfile)
        self.assertIn("find /tmp/maven-repo/repository -name '*.lastUpdated' -delete", dockerfile)
        self.assertIn('"-o"', dockerfile)
        self.assertIn("acceptance-skip-unit-tests", pom)
        self.assertIn("<skipTests>true</skipTests>", pom)

    def test_strict_migration_rejects_unmanaged_schema_without_repair(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            psql = root / "psql"
            psql.write_text(
                "#!/usr/bin/env python3\n"
                "import sys\n"
                "sql = sys.argv[sys.argv.index('-c') + 1]\n"
                "if sql.strip() == 'SELECT 1':\n"
                "    print('1')\n"
                "elif 'information_schema.tables' in sql:\n"
                "    print('true')\n"
                "elif 'databasechangelog' in sql:\n"
                "    print('false')\n"
                "else:\n"
                "    raise SystemExit('unexpected SQL: ' + sql)\n",
                encoding="utf-8",
            )
            psql.chmod(psql.stat().st_mode | 0o111)
            result = subprocess.run(
                ["bash", str(SCRIPTS / "strict-migration.sh"), "preflight"],
                cwd=ROOT,
                env={
                    **os.environ,
                    "PATH": f"{root}:{os.environ['PATH']}",
                    "MAVEN_BIN": "true",
                    "DB_HOST": "127.0.0.1",
                    "DB_PORT": "5432",
                    "DB_NAME": "dataplatform_ci",
                    "DB_USERNAME": "postgres",
                    "DB_PASSWORD": "postgres",
                    "DB_CONNECT_ATTEMPTS": "1",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
            self.assertIn("application schema exists without DATABASECHANGELOG", result.stderr)

    def test_new_sql_files_must_be_referenced_by_changelog(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "ci@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "CI"], cwd=root, check=True)
            changelog = root / "sql/changelog/db.changelog-master.xml"
            migration = root / "sql/migrations/V051__fixture.sql"
            changelog.parent.mkdir(parents=True)
            migration.parent.mkdir(parents=True)
            changelog.write_text(
                '<?xml version="1.0"?><databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"/>\n',
                encoding="utf-8",
            )
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=root, check=True)
            migration.write_text("select 1;\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "migration"], cwd=root, check=True)
            command = [
                "python3",
                str(SCRIPTS / "check-changelog-references.py"),
                "--root",
                str(root),
                "--base-ref",
                "HEAD^",
            ]
            rejected = subprocess.run(command, cwd=root, text=True, capture_output=True)
            self.assertEqual(rejected.returncode, 2, rejected.stdout + rejected.stderr)
            self.assertIn("not indexed", rejected.stderr)
            changelog.write_text(
                '<?xml version="1.0"?><databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog">'
                '<changeSet id="fixture" author="ci"><sqlFile path="../migrations/V051__fixture.sql"/></changeSet>'
                "</databaseChangeLog>\n",
                encoding="utf-8",
            )
            missing_rollback = subprocess.run(command, cwd=root, text=True, capture_output=True)
            self.assertEqual(missing_rollback.returncode, 2, missing_rollback.stdout + missing_rollback.stderr)
            self.assertIn("requires sql/rollbacks/U051__", missing_rollback.stderr)
            rollback = root / "sql/rollbacks/U051__fixture.sql"
            rollback.parent.mkdir(parents=True)
            rollback.write_text("select 1;\n", encoding="utf-8")
            changelog.write_text(
                '<?xml version="1.0"?><databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog">'
                '<changeSet id="fixture" author="ci"><sqlFile path="../migrations/V051__fixture.sql"/>'
                '<rollback><sqlFile path="../rollbacks/U051__fixture.sql"/></rollback></changeSet>'
                "</databaseChangeLog>\n",
                encoding="utf-8",
            )
            accepted = subprocess.run(command, cwd=root, text=True, capture_output=True)
            self.assertEqual(accepted.returncode, 0, accepted.stdout + accepted.stderr)

    def test_source_changes_trigger_security_gate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "ci@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "CI"], cwd=root, check=True)
            subprocess.run(["git", "commit", "--allow-empty", "-qm", "initial"], cwd=root, check=True)
            backend = root / "data-platform-gateway" / "src" / "main" / "Gateway.java"
            frontend = root / "data-platform-web" / "src" / "App.tsx"
            backend.parent.mkdir(parents=True)
            frontend.parent.mkdir(parents=True)
            backend.write_text("class Gateway {}\n", encoding="utf-8")
            frontend.write_text("export const app = true;\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=root, check=True)
            backend.write_text("class Gateway { int port = 8888; }\n", encoding="utf-8")
            frontend.write_text("export const app = false;\n", encoding="utf-8")
            result = subprocess.run(
                ["bash", str(SCRIPTS / "classify-changes.sh")],
                cwd=root,
                env={**os.environ, "BASE_REF": "HEAD^", "GITHUB_EVENT_NAME": "pull_request"},
                text=True,
                capture_output=True,
                check=True,
            )
            values = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
            self.assertEqual(values["backend"], "true")
            self.assertEqual(values["frontend"], "true")
            self.assertEqual(values["security"], "true")
            self.assertEqual(values["deployability"], "true")

    def test_changed_coverage_baseline_must_match_measured_reports(self) -> None:
        classifier = (SCRIPTS / "classify-changes.sh").read_text(encoding="utf-8")
        self.assertIn("ci/policy/coverage-baseline.json", classifier)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "ci@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "CI"], cwd=root, check=True)
            subprocess.run(["git", "commit", "--allow-empty", "-qm", "initial"], cwd=root, check=True)
            policy = root / "ci/policy"
            policy.mkdir(parents=True)
            baseline = policy / "coverage-baseline.json"
            baseline.write_text(
                json.dumps({"schemaVersion": 1, "decreaseThresholdPercentagePoints": 0.5, "modules": {"module": {"line": 70, "branch": 70}}}),
                encoding="utf-8",
            )
            report = root / "module/target/site/jacoco/jacoco.xml"
            report.parent.mkdir(parents=True)
            report.write_text(
                '<report><counter type="LINE" missed="30" covered="70"/><counter type="BRANCH" missed="30" covered="70"/></report>\n',
                encoding="utf-8",
            )
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "baseline"], cwd=root, check=True)
            baseline.write_text(
                json.dumps({"schemaVersion": 1, "decreaseThresholdPercentagePoints": 0.5, "modules": {"module": {"line": 80, "branch": 80}}}),
                encoding="utf-8",
            )
            command = [
                "python3",
                str(SCRIPTS / "check-coverage.py"),
                "--root",
                ".",
                "--baseline",
                "ci/policy/coverage-baseline.json",
                "--scope",
                "backend",
                "--enforce-changed-baseline",
                "--base-ref",
                "HEAD^",
            ]
            rejected = subprocess.run(command, cwd=root, text=True, capture_output=True)
            self.assertEqual(rejected.returncode, 2, rejected.stdout + rejected.stderr)
            baseline.write_text(
                json.dumps({"schemaVersion": 1, "decreaseThresholdPercentagePoints": 0.5, "modules": {"module": {"line": 70, "branch": 70}}}),
                encoding="utf-8",
            )
            accepted = subprocess.run(command, cwd=root, text=True, capture_output=True)
            self.assertEqual(accepted.returncode, 0, accepted.stdout + accepted.stderr)

    def test_coverage_baseline_rejects_nonfinite_claims(self) -> None:
        payload = {
            "schemaVersion": 1,
            "decreaseThresholdPercentagePoints": 0.5,
            "modules": {"module": {"line": float("nan"), "branch": 70}},
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = root / "coverage-baseline.json"
            baseline.write_text(json.dumps(payload), encoding="utf-8")
            policy_result = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "verify-policy-files.py"),
                    "--base-ref",
                    "HEAD",
                    "--coverage-baseline",
                    str(baseline),
                    "--codeowners",
                    str(ROOT / ".github/CODEOWNERS"),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(policy_result.returncode, 2, policy_result.stdout + policy_result.stderr)
            self.assertIn("finite", policy_result.stderr)

            report = root / "module/target/site/jacoco/jacoco.xml"
            report.parent.mkdir(parents=True)
            report.write_text(
                '<report><counter type="LINE" missed="30" covered="70"/>'
                '<counter type="BRANCH" missed="30" covered="70"/></report>\n',
                encoding="utf-8",
            )
            coverage_result = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "check-coverage.py"),
                    "--root",
                    str(root),
                    "--baseline",
                    str(baseline),
                    "--scope",
                    "backend",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(coverage_result.returncode, 2, coverage_result.stdout + coverage_result.stderr)
            self.assertIn("finite", coverage_result.stderr)

    def test_manifest_round_trip_and_digest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images = {}
            for component in ("gateway", "masterdata", "access", "billing", "identity", "governance", "web", "dbops", "acceptance"):
                digest = "sha256:" + hashlib.sha256(component.encode()).hexdigest()
                images[component] = {
                    "reference": f"ghcr.io/lixud/data-manager-hub-{component}@{digest}",
                    "sbom": f"oci://ghcr.io/lixud/data-manager-hub-{component}-sbom@{digest}",
                    "provenance": f"oci://ghcr.io/lixud/data-manager-hub-{component}@{digest}",
                }
            for name, value in {
                "images.json": images,
                "plugins.json": [],
                "evidence.json": {
                    "backend": "passed",
                    "frontend": "not-applicable",
                    "security": "passed",
                    "deployability": "passed",
                    "migration": "not-applicable",
                },
            }.items():
                (root / name).write_text(json.dumps(value), encoding="utf-8")
            output = root / "manifest.json"
            result = run_script(
                "manifest.py",
                "create",
                "--git-sha",
                "0123456789abcdef0123456789abcdef01234567",
                "--run-id",
                "1",
                "--generated-at",
                "2026-08-22T00:00:00Z",
                "--source-tree-digest",
                "sha256:" + "1" * 64,
                "--toolchain-lock-digest",
                "sha256:" + "2" * 64,
                "--changelog-digest",
                "sha256:" + "3" * 64,
                "--nacos-digest",
                "sha256:" + "4" * 64,
                "--latest-migration",
                "V050",
                "--migration-mode",
                "NONE",
                "--images",
                str(root / "images.json"),
                "--plugins",
                str(root / "plugins.json"),
                "--evidence",
                str(root / "evidence.json"),
                "--output",
                str(output),
            )
            digest = json.loads(result.stdout)["manifestDigest"]
            run_script("manifest.py", "verify", "--manifest", str(output), "--expected-digest", digest)
            run_script("manifest.py", "verify", "--require-required-ci", "--manifest", str(output), expect=2)
            manifest = json.loads(output.read_text(encoding="utf-8"))
            manifest["spec"]["verification"]["requiredCi"] = {"runId": "1", "runAttempt": 1, "conclusion": "success"}
            output.write_bytes((json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode())
            run_script("manifest.py", "verify", "--require-required-ci", "--manifest", str(output))
            manifest["spec"]["verification"]["requiredCi"]["runnerSecret"] = "must-not-enter-manifest"
            output.write_bytes((json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode())
            run_script("manifest.py", "verify", "--require-required-ci", "--manifest", str(output), expect=2)
            manifest["spec"]["verification"]["requiredCi"].pop("runnerSecret")
            manifest["spec"]["images"]["gateway"]["runnerSecret"] = "must-not-enter-manifest"
            output.write_bytes((json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode())
            run_script("manifest.py", "verify", "--require-required-ci", "--manifest", str(output), expect=2)
            manifest["spec"]["images"]["gateway"].pop("runnerSecret")
            manifest["spec"]["verification"]["requiredCi"]["runId"] = "ci-1"
            output.write_bytes((json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode())
            run_script("manifest.py", "verify", "--require-required-ci", "--manifest", str(output), expect=2)
            manifest["spec"]["verification"]["requiredCi"]["runId"] = "1"
            manifest["metadata"]["runAttempt"] = 0
            output.write_bytes((json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode())
            run_script("manifest.py", "verify", "--manifest", str(output), expect=2)
            manifest["metadata"]["runAttempt"] = 1
            references = {name: value["reference"] for name, value in manifest["spec"]["images"].items()}
            references_path = root / "image-references.json"
            references_path.write_text(json.dumps(references), encoding="utf-8")
            run_script("verify-oci-references.py", str(output), str(references_path))
            manifest["spec"]["images"]["gateway"]["reference"] = "ghcr.io/lixud/data-manager-hub-gateway@sha256:not-a-digest"
            output.write_bytes((json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode())
            run_script("manifest.py", "verify", "--manifest", str(output), expect=2)

    def test_plugin_signing_and_receipt_collection_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key = root / "signing.pem"
            plugin = root / "plugin.jar"
            receipt = root / "plugin-receipt.json"
            receipts = root / "plugin-receipts"
            receipts.mkdir()
            plugin.write_bytes(b"fixture plugin artifact\n")
            subprocess.run(
                ["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(key)],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            remote_verifier = root / "remote-verifier.py"
            remote_verifier.write_text(
                "#!/usr/bin/env python3\n"
                "import argparse, hashlib, json\n"
                "parser=argparse.ArgumentParser(); parser.add_argument('--plugin-file'); parser.add_argument('--signature-file'); parser.add_argument('--repository'); args=parser.parse_args()\n"
                "print(json.dumps({'sha256':'sha256:'+hashlib.sha256(open(args.plugin_file,'rb').read()).hexdigest(),'signatureSha256':'sha256:'+hashlib.sha256(open(args.signature_file,'rb').read()).hexdigest(),'repository':args.repository}))\n",
                encoding="utf-8",
            )
            remote_verifier.chmod(remote_verifier.stat().st_mode | 0o111)
            env = os.environ.copy()
            env.update(
                {
                    "PLUGIN_FILE": str(plugin),
                    "PLUGIN_ID": "fixture-plugin",
                    "PLUGIN_VERSION": "1.0.0",
                    "PLUGIN_SIGNING_PRIVATE_KEY": str(key),
                    "PLUGIN_REPOSITORY": "oci://fixture.invalid/plugin",
                    "PLUGIN_REMOTE_VERIFY_COMMAND": str(remote_verifier),
                    "PLUGIN_RECEIPT_OUTPUT": str(receipt),
                }
            )
            signed = subprocess.run(
                ["bash", str(SCRIPTS / "sign-and-publish-plugin.sh")],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(signed.returncode, 0, signed.stderr)
            (receipts / "fixture.json").write_text(receipt.read_text(encoding="utf-8"), encoding="utf-8")
            output = root / "plugins.json"
            run_script("collect-plugin-receipts.py", "--require", str(receipts), str(output))
            collected = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(len(collected), 1)
            self.assertEqual(collected[0]["id"], "fixture-plugin")
            plugin_receipt = json.loads((receipts / "fixture.json").read_text(encoding="utf-8"))
            plugin_receipt["runnerSecret"] = "must-not-enter-manifest"
            (receipts / "fixture.json").write_text(json.dumps(plugin_receipt), encoding="utf-8")
            rejected = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "collect-plugin-receipts.py"),
                    str(receipts),
                    str(output),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected.returncode, 2)
            self.assertIn("forbidden fields", rejected.stderr)
            empty = root / "empty-receipts"
            empty.mkdir()
            run_script("collect-plugin-receipts.py", "--require", str(empty), str(root / "empty.json"), expect=2)

    def test_plugin_receipt_requires_versioned_kind(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipts = root / "plugin-receipts"
            receipts.mkdir()
            receipt = {
                "apiVersion": "wrong/v1",
                "kind": "PluginReceipt",
                "id": "fixture-plugin",
                "version": "1.0.0",
                "repository": "oci://fixture.invalid/plugin",
                "sha256": "sha256:" + "a" * 64,
                "signatureSha256": "sha256:" + "b" * 64,
                "signatureFingerprint": "sha256:" + "c" * 64,
            }
            (receipts / "fixture.json").write_text(json.dumps(receipt), encoding="utf-8")
            result = run_script(
                "collect-plugin-receipts.py",
                str(receipts),
                str(root / "plugins.json"),
                expect=2,
            )
            self.assertIn("invalid apiVersion/kind", result.stderr)

    def test_immutable_manifest_publisher_reuses_and_rejects_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "build-manifest.v1.json"
            manifest.write_text('{"kind":"BuildManifest"}\n', encoding="utf-8")
            state = root / "oras-state.json"
            oras = root / "oras"
            oras.write_text(
                "#!/usr/bin/env python3\n"
                "import hashlib, json, os, pathlib, sys\n"
                "state_path=pathlib.Path(os.environ['ORAS_STATE'])\n"
                "args=sys.argv[1:]\n"
                "if args[:2] == ['manifest','fetch']:\n"
                "    if os.environ.get('ORAS_ERROR'):\n"
                "        print('temporary registry failure', file=sys.stderr); raise SystemExit(17)\n"
                "    if not state_path.exists(): print('404 not found', file=sys.stderr); raise SystemExit(2)\n"
                "    state=json.loads(state_path.read_text())\n"
                "    if '--descriptor' in args: print(json.dumps({'digest': state['descriptor']}))\n"
                "    else: print(json.dumps({'artifactType':'application/vnd.dmh.build-manifest.v1+json','layers':[{'digest': state['layer']}]}))\n"
                "elif args and args[0] == 'push':\n"
                "    file_ref=args[-1].split(':application/json',1)[0]\n"
                "    layer='sha256:'+hashlib.sha256(pathlib.Path(file_ref).read_bytes()).hexdigest()\n"
                "    descriptor='sha256:'+hashlib.sha256(('descriptor:'+layer).encode()).hexdigest()\n"
                "    state_path.write_text(json.dumps({'layer':layer,'descriptor':descriptor}))\n"
                "else: raise SystemExit(f'unexpected oras invocation: {args}')\n",
                encoding="utf-8",
            )
            oras.chmod(oras.stat().st_mode | 0o111)
            env = os.environ.copy()
            env.update({"PATH": f"{root}:{env['PATH']}", "ORAS_STATE": str(state)})
            command = [
                "bash",
                str(SCRIPTS / "publish-immutable-manifest.sh"),
                "--ref",
                "ghcr.io/example/build-manifest:sha-test",
                "--file",
                str(manifest),
            ]
            first = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            second = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(second.returncode, 0, second.stderr)
            self.assertEqual(first.stdout, second.stdout)
            manifest.write_text('{"kind":"BuildManifest","changed":true}\n', encoding="utf-8")
            drift = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(drift.returncode, 2)
            failed_env = {**env, "ORAS_ERROR": "1"}
            failed = subprocess.run(command, cwd=ROOT, env=failed_env, text=True, capture_output=True)
            self.assertEqual(failed.returncode, 2)
            self.assertIn("unable to determine immutable Build Manifest tag state", failed.stderr)

    def test_immutable_oci_alias_publisher_reuses_and_rejects_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            components = ("gateway", "masterdata", "access", "billing", "identity", "governance", "web", "dbops", "acceptance")
            hex_digests = "012345678"
            manifest = root / "build-manifest.v1.json"
            manifest.write_text(
                json.dumps(
                    {
                        "spec": {
                            "images": {
                                component: {
                                    "reference": f"ghcr.io/lixud/data-manager-hub-{component}@sha256:{hex_digests[index] * 64}"
                                }
                                for index, component in enumerate(components)
                            }
                        }
                    }
                ),
                encoding="utf-8",
            )
            state = root / "oras-state.json"
            oras = root / "oras"
            oras.write_text(
                "#!/usr/bin/env python3\n"
                "import json, os, pathlib, sys\n"
                "path=pathlib.Path(os.environ['ORAS_STATE'])\n"
                "state=json.loads(path.read_text()) if path.exists() else {}\n"
                "args=sys.argv[1:]\n"
                "if args[:2] == ['manifest','fetch'] and '--descriptor' in args:\n"
                "    ref=args[-1]\n"
                "    if os.environ.get('ORAS_ERROR'):\n"
                "        print('temporary registry failure', file=sys.stderr); raise SystemExit(17)\n"
                "    if ref not in state: print('404 not found', file=sys.stderr); raise SystemExit(1)\n"
                "    print(json.dumps({'digest': state[ref]}))\n"
                "elif args and args[0] == 'tag':\n"
                "    source, tag=args[1], args[2]\n"
                "    state[source.split('@',1)[0] + ':' + tag]=source.split('@',1)[1]\n"
                "    path.write_text(json.dumps(state))\n"
                "else: raise SystemExit(f'unexpected oras invocation: {args}')\n",
                encoding="utf-8",
            )
            oras.chmod(oras.stat().st_mode | 0o111)
            manifest_digest = "sha256:" + "d" * 64
            state.write_text(
                json.dumps(
                    {
                        **{
                            f"ghcr.io/lixud/data-manager-hub-{component}@sha256:{hex_digests[index] * 64}": f"sha256:{hex_digests[index] * 64}"
                            for index, component in enumerate(components)
                        },
                        f"ghcr.io/lixud/data-manager-hub-build-manifest@{manifest_digest}": manifest_digest,
                    }
                ),
                encoding="utf-8",
            )
            command = [
                "bash",
                str(SCRIPTS / "publish-immutable-oci-aliases.sh"),
                "--manifest",
                str(manifest),
                "--build-manifest-ref",
                f"ghcr.io/lixud/data-manager-hub-build-manifest@{manifest_digest}",
                "--version",
                "v1.2.3",
                "--image-namespace",
                "ghcr.io/lixud",
                "--output",
                str(root / "release-aliases.v1.json"),
            ]
            env = os.environ.copy()
            env.update({"PATH": f"{root}:{env['PATH']}", "ORAS_STATE": str(state)})
            first = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            aliases = json.loads((root / "release-aliases.v1.json").read_text(encoding="utf-8"))
            self.assertEqual(len(aliases["aliases"]), 10)
            second = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(second.returncode, 0, second.stderr)
            current = json.loads(state.read_text(encoding="utf-8"))
            current["ghcr.io/lixud/data-manager-hub-gateway:v1.2.3"] = "sha256:" + "f" * 64
            state.write_text(json.dumps(current), encoding="utf-8")
            drift = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(drift.returncode, 2)
            self.assertIn("different digest", drift.stderr)
            state_before_failure = state.read_text(encoding="utf-8")
            failed = subprocess.run(
                command,
                cwd=ROOT,
                env={**env, "ORAS_ERROR": "1"},
                text=True,
                capture_output=True,
            )
            self.assertEqual(failed.returncode, 2)
            self.assertIn("unable to inspect OCI reference", failed.stderr)
            self.assertEqual(state.read_text(encoding="utf-8"), state_before_failure)

    def test_image_evidence_collector_ignores_raw_sbom_documents(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            components = ("gateway", "masterdata", "access", "billing", "identity", "governance", "web", "dbops", "acceptance")
            for component in components:
                digest = "sha256:" + hashlib.sha256(component.encode()).hexdigest()
                (root / f"{component}.json").write_text(
                    json.dumps(
                        {
                            "component": component,
                            "image": {
                                "reference": f"ghcr.io/lixud/data-manager-hub-{component}@{digest}",
                                "sbom": f"oci://ghcr.io/lixud/data-manager-hub-{component}-sbom@{digest}",
                                "provenance": f"oci://ghcr.io/lixud/data-manager-hub-{component}@{digest}",
                            },
                        }
                    ),
                    encoding="utf-8",
                )
                (root / f"sbom-{component}.json").write_text(
                    json.dumps({"bomFormat": "CycloneDX", "metadata": {"component": {"name": component}}}),
                    encoding="utf-8",
                )
            output = root / "images.json"
            result = run_script("collect-image-evidence.py", str(root), str(output))
            self.assertIn("collected 9 image receipts", result.stdout)
            self.assertEqual(set(json.loads(output.read_text(encoding="utf-8"))), set(components))
            poisoned = json.loads((root / "gateway.json").read_text(encoding="utf-8"))
            poisoned["image"]["runnerSecret"] = "must-not-enter-manifest"
            (root / "gateway.json").write_text(json.dumps(poisoned), encoding="utf-8")
            rejected_secret = subprocess.run(
                ["python3", str(SCRIPTS / "collect-image-evidence.py"), str(root), str(output)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_secret.returncode, 2)
            self.assertIn("forbidden fields", rejected_secret.stderr)
            (root / "gateway.json").write_text(
                json.dumps(
                    {
                        "component": "gateway",
                        "image": {
                            "reference": "ghcr.io/lixud/data-manager-hub-gateway@sha256:" + "f" * 64,
                            "sbom": "oci://ghcr.io/lixud/data-manager-hub-gateway-sbom@sha256:" + "f" * 64,
                            "provenance": "oci://ghcr.io/lixud/data-manager-hub-gateway@sha256:" + "f" * 64,
                        },
                    }
                ),
                encoding="utf-8",
            )
            (root / "duplicate.json").write_text(
                json.dumps(
                    {
                        "component": "gateway",
                        "image": {
                            "reference": "ghcr.io/lixud/data-manager-hub-duplicate@sha256:" + "f" * 64,
                            "sbom": "oci://ghcr.io/lixud/data-manager-hub-duplicate-sbom@sha256:" + "f" * 64,
                            "provenance": "oci://ghcr.io/lixud/data-manager-hub-duplicate@sha256:" + "f" * 64,
                        },
                    }
                ),
                encoding="utf-8",
            )
            run_script("collect-image-evidence.py", str(root), str(output), expect=2)

    def test_image_evidence_rejects_top_level_runner_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            digest = "sha256:" + "a" * 64
            (root / "gateway.json").write_text(
                json.dumps(
                    {
                        "component": "gateway",
                        "runnerSecret": "must-not-enter-manifest",
                        "image": {
                            "reference": f"ghcr.io/lixud/data-manager-hub-gateway@{digest}",
                            "sbom": f"oci://ghcr.io/lixud/data-manager-hub-gateway-sbom@{digest}",
                            "provenance": f"oci://ghcr.io/lixud/data-manager-hub-gateway@{digest}",
                        },
                    }
                ),
                encoding="utf-8",
            )
            result = run_script(
                "collect-image-evidence.py",
                str(root),
                str(root / "images.json"),
                expect=2,
            )
            self.assertIn("forbidden fields", result.stderr)

    def test_snapshot_receipt_rejects_expiry_and_accepts_wal_position(self) -> None:
        now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
        receipt = {
            "snapshotId": "snap-1",
            "sourceInstanceId": "prod-db",
            "engine": "postgresql",
            "engineVersion": "16.4",
            "completedAt": now.isoformat().replace("+00:00", "Z"),
            "expiresAt": (now + dt.timedelta(hours=1)).isoformat().replace("+00:00", "Z"),
            "consistency": "TRANSACTION_CONSISTENT",
            "recoveryPosition": "0/16B6A80",
            "sourceSchemaVersion": "V050",
            "changelogDigest": "sha256:" + "a" * 64,
            "verificationStatus": "VERIFIED",
            "signature": "sha256:" + "b" * 64,
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(receipt), encoding="utf-8")
            run_script(
                "verify-snapshot-receipt.py",
                str(path),
                "--source-instance",
                "prod-db",
                "--schema-version",
                "V050",
                "--changelog-digest",
                "sha256:" + "a" * 64,
                "--now",
                now.isoformat().replace("+00:00", "Z"),
            )
            invalid_now = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "verify-snapshot-receipt.py"),
                    str(path),
                    "--source-instance",
                    "prod-db",
                    "--schema-version",
                    "V050",
                    "--changelog-digest",
                    "sha256:" + "a" * 64,
                    "--now",
                    "not-a-timestamp",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(invalid_now.returncode, 2)
            self.assertIn("invalid current timestamp", invalid_now.stderr)
            self.assertNotIn("Traceback", invalid_now.stderr)
            receipt["recoveryPosition"] = 123
            path.write_text(json.dumps(receipt), encoding="utf-8")
            run_script(
                "verify-snapshot-receipt.py",
                str(path),
                "--source-instance",
                "prod-db",
                "--schema-version",
                "V050",
                "--changelog-digest",
                "sha256:" + "a" * 64,
                "--now",
                now.isoformat().replace("+00:00", "Z"),
                expect=2,
            )
            receipt["recoveryPosition"] = "0/16B6A80"
            path.write_text(json.dumps(receipt), encoding="utf-8")
            run_script(
                "verify-snapshot-receipt.py",
                str(path),
                "--source-instance",
                "prod-db",
                "--schema-version",
                "V050",
                "--changelog-digest",
                "sha256:" + "a" * 64,
                "--not-before",
                (now + dt.timedelta(minutes=10)).isoformat().replace("+00:00", "Z"),
                "--now",
                now.isoformat().replace("+00:00", "Z"),
                expect=2,
            )
            receipt["expiresAt"] = (now - dt.timedelta(minutes=1)).isoformat().replace("+00:00", "Z")
            path.write_text(json.dumps(receipt), encoding="utf-8")
            run_script(
                "verify-snapshot-receipt.py",
                str(path),
                "--source-instance",
                "prod-db",
                "--schema-version",
                "V050",
                "--changelog-digest",
                "sha256:" + "a" * 64,
                "--now",
                now.isoformat().replace("+00:00", "Z"),
                expect=2,
            )
            receipt["expiresAt"] = (now + dt.timedelta(hours=1)).isoformat().replace("+00:00", "Z")
            receipt["signature"] = "sha256:" + "g" * 64
            path.write_text(json.dumps(receipt), encoding="utf-8")
            run_script(
                "verify-snapshot-receipt.py",
                str(path),
                "--source-instance",
                "prod-db",
                "--schema-version",
                "V050",
                "--changelog-digest",
                "sha256:" + "a" * 64,
                "--now",
                now.isoformat().replace("+00:00", "Z"),
                expect=2,
            )

            receipt["signature"] = "sha256:" + "b" * 64
            receipt["completedAt"] = now.isoformat().replace("+00:00", "Z")
            receipt["expiresAt"] = (now - dt.timedelta(minutes=1)).isoformat().replace("+00:00", "Z")
            path.write_text(json.dumps(receipt), encoding="utf-8")
            invalid_window = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "verify-snapshot-receipt.py"),
                    str(path),
                    "--source-instance",
                    "prod-db",
                    "--schema-version",
                    "V050",
                    "--changelog-digest",
                    "sha256:" + "a" * 64,
                    "--now",
                    now.isoformat().replace("+00:00", "Z"),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(invalid_window.returncode, 2)
            self.assertIn("expiresAt must be later", invalid_window.stderr)

            path.write_text("[]", encoding="utf-8")
            invalid_root = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "verify-snapshot-receipt.py"),
                    str(path),
                    "--source-instance",
                    "prod-db",
                    "--schema-version",
                    "V050",
                    "--changelog-digest",
                    "sha256:" + "a" * 64,
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(invalid_root.returncode, 2)
            self.assertIn("root must be a JSON object", invalid_root.stderr)

    def test_snapshot_receipt_rejects_wrong_postgresql_major(self) -> None:
        now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
        digest = "sha256:" + "a" * 64
        with tempfile.TemporaryDirectory() as directory:
            receipt = Path(directory) / "receipt.json"
            receipt.write_text(
                json.dumps(
                    {
                        "snapshotId": "snap-wrong-major",
                        "sourceInstanceId": "prod-db",
                        "engine": "postgresql",
                        "engineVersion": "15.8",
                        "completedAt": now.isoformat().replace("+00:00", "Z"),
                        "expiresAt": (now + dt.timedelta(hours=1)).isoformat().replace("+00:00", "Z"),
                        "consistency": "TRANSACTION_CONSISTENT",
                        "recoveryPosition": "0/16B6A80",
                        "sourceSchemaVersion": "V050",
                        "changelogDigest": digest,
                        "verificationStatus": "VERIFIED",
                        "signature": digest,
                    }
                ),
                encoding="utf-8",
            )
            result = run_script(
                "verify-snapshot-receipt.py",
                str(receipt),
                "--source-instance",
                "prod-db",
                "--schema-version",
                "V050",
                "--changelog-digest",
                digest,
                expect=2,
            )
            self.assertIn("major version 16", result.stderr)

    def test_snapshot_adapter_wrapper_creates_verified_receipt(self) -> None:
        now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
        digest = "sha256:" + "c" * 64
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adapter = root / "adapter.py"
            adapter.write_text(
                "#!/usr/bin/env python3\n"
                "import datetime as d, json, sys\n"
                "args=dict(zip(sys.argv[1::2], sys.argv[2::2]))\n"
                "now=d.datetime.now(d.timezone.utc).replace(microsecond=0)\n"
                "print(json.dumps({'snapshotId':'adapter-snap','sourceInstanceId':args['--source-instance'],'engine':'postgresql','engineVersion':'16.4','completedAt':now.isoformat().replace('+00:00','Z'),'expiresAt':(now+d.timedelta(hours=1)).isoformat().replace('+00:00','Z'),'consistency':'TRANSACTION_CONSISTENT','recoveryPosition':'0/16B6A80','sourceSchemaVersion':args['--schema-version'],'changelogDigest':args['--changelog-digest'],'verificationStatus':'VERIFIED','signature':'sha256:'+'d'*64}))\n",
                encoding="utf-8",
            )
            adapter.chmod(adapter.stat().st_mode | 0o111)
            verifier = root / "verifier.py"
            marker = root / "verified"
            verifier.write_text(
                "#!/usr/bin/env python3\n"
                "from pathlib import Path\n"
                f"Path({str(marker)!r}).write_text(Path(__import__('sys').argv[1]).read_text(), encoding='utf-8')\n",
                encoding="utf-8",
            )
            verifier.chmod(verifier.stat().st_mode | 0o111)
            output = root / "receipt.json"
            result = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-snapshot-receipt.sh"),
                    "--adapter",
                    str(adapter),
                    "--source-instance",
                    "prod-db",
                    "--schema-version",
                    "V050",
                    "--changelog-digest",
                    digest,
                    "--signature-verifier",
                    str(verifier),
                    "--not-before",
                    (now - dt.timedelta(hours=1)).isoformat().replace("+00:00", "Z"),
                    "--output",
                    str(output),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(output.read_text(encoding="utf-8"))["snapshotId"], "adapter-snap")
            self.assertTrue(marker.exists())

            receipt_data = json.loads(output.read_text(encoding="utf-8"))
            receipt_data["runnerSecret"] = "must-not-enter-snapshot-artifact"
            output.write_text(json.dumps(receipt_data), encoding="utf-8")
            rejected = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "verify-snapshot-receipt.py"),
                    str(output),
                    "--source-instance",
                    "prod-db",
                    "--schema-version",
                    "V050",
                    "--changelog-digest",
                    digest,
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected.returncode, 2)
            self.assertIn("forbidden fields", rejected.stderr)

    def test_snapshot_restore_wrapper_requires_new_verified_instance(self) -> None:
        now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
        digest = "sha256:" + "e" * 64
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = root / "receipt.json"
            receipt.write_text(
                json.dumps(
                    {
                        "snapshotId": "snap-restore",
                        "sourceInstanceId": "prod-db",
                        "engine": "postgresql",
                        "engineVersion": "16.4",
                        "completedAt": now.isoformat().replace("+00:00", "Z"),
                        "expiresAt": (now + dt.timedelta(hours=1)).isoformat().replace("+00:00", "Z"),
                        "consistency": "TRANSACTION_CONSISTENT",
                        "recoveryPosition": "0/16B6A80",
                        "sourceSchemaVersion": "V050",
                        "changelogDigest": digest,
                        "verificationStatus": "VERIFIED",
                        "signature": "sha256:" + "f" * 64,
                    }
                ),
                encoding="utf-8",
            )
            adapter = root / "restore-adapter.py"
            adapter.write_text(
                "#!/usr/bin/env python3\n"
                "import json, sys\n"
                "args=dict(zip(sys.argv[1::2], sys.argv[2::2]))\n"
                "print(json.dumps({'status':'VERIFIED','sourceInstanceId':args['--source-instance'],'targetInstanceId':args['--target-instance']}))\n",
                encoding="utf-8",
            )
            adapter.chmod(adapter.stat().st_mode | 0o111)
            output = root / "restore-result.json"
            command = [
                "bash",
                str(SCRIPTS / "restore-snapshot.sh"),
                "--adapter",
                str(adapter),
                "--receipt",
                str(receipt),
                "--source-instance",
                "prod-db",
                "--target-instance",
                "prod-db-recovered",
                "--schema-version",
                "V050",
                "--changelog-digest",
                digest,
                "--output",
                str(output),
            ]
            restored = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(restored.returncode, 0, restored.stderr)
            self.assertEqual(json.loads(output.read_text(encoding="utf-8"))["status"], "VERIFIED")
            old_receipt = json.loads(receipt.read_text(encoding="utf-8"))
            old_receipt["completedAt"] = (now - dt.timedelta(hours=3)).isoformat().replace("+00:00", "Z")
            old_receipt["expiresAt"] = (now + dt.timedelta(hours=1)).isoformat().replace("+00:00", "Z")
            receipt.write_text(json.dumps(old_receipt), encoding="utf-8")
            self.assertEqual(subprocess.run(command, cwd=ROOT, text=True, capture_output=True).returncode, 2)
            extended = command[: command.index("--output")] + ["--max-age-hours", "168"] + command[command.index("--output") :]
            extended_result = subprocess.run(extended, cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(extended_result.returncode, 0, extended_result.stderr)
            same_target = subprocess.run(
                command[: command.index("--target-instance") + 1] + ["prod-db"] + command[command.index("--target-instance") + 2 :],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(same_target.returncode, 2)

    def test_release_gate_is_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "gates.json"
            gate_args = (
                "--environment",
                "staging",
                "--source-sha",
                "a" * 40,
                "--manifest-digest",
                "sha256:" + "b" * 64,
            )
            passing = {
                "environment": "staging",
                "sourceSha": "a" * 40,
                "manifestDigest": "sha256:" + "b" * 64,
                "traffic": 120,
                "errorRatio": 0.001,
                "p95Seconds": 0.5,
                "p95LimitSeconds": 1.0,
                "baselineP95Seconds": 0.5,
                "readyReplicas": 2,
                "desiredReplicas": 2,
                "oomKills": 0,
                "restarts": 0,
                "connectorReadiness": "UP",
                "synthetic": "passed",
                "collectedAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
            }
            path.write_text(json.dumps(passing), encoding="utf-8")
            run_script("release-gates.py", str(path), *gate_args)
            passing["runnerSecret"] = "must-not-enter-receipt"
            path.write_text(json.dumps(passing), encoding="utf-8")
            run_script("release-gates.py", str(path), *gate_args, expect=2)
            passing.pop("runnerSecret")
            path.write_text(json.dumps(passing), encoding="utf-8")
            run_script(
                "release-gates.py",
                str(path),
                "--environment",
                "production",
                "--source-sha",
                "c" * 40,
                "--manifest-digest",
                "sha256:" + "d" * 64,
                expect=2,
            )
            passing["errorRatio"] = 0.02
            path.write_text(json.dumps(passing), encoding="utf-8")
            run_script("release-gates.py", str(path), *gate_args, expect=2)
            passing["errorRatio"] = 0.001
            passing["readyReplicas"] = 3
            passing["desiredReplicas"] = 2
            path.write_text(json.dumps(passing), encoding="utf-8")
            run_script("release-gates.py", str(path), *gate_args, expect=2)
            passing["readyReplicas"] = 2
            policy = Path(directory) / "policy.yaml"
            policy.write_text("schemaVersion: 1\nthresholds:\n  minimumTraffic: 200\n  errorRatio: 0.01\n  maxRestarts: 1\n", encoding="utf-8")
            passing["errorRatio"] = 0.001
            path.write_text(json.dumps(passing), encoding="utf-8")
            run_script("release-gates.py", str(path), "--policy", str(policy), *gate_args, expect=2)
            passing["p95LimitSeconds"] = 999.0
            passing["collectedAt"] = dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")
            path.write_text(json.dumps(passing), encoding="utf-8")
            run_script("release-gates.py", str(path), *gate_args, expect=2)
            passing["collectedAt"] = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=21)).isoformat().replace("+00:00", "Z")
            path.write_text(json.dumps(passing), encoding="utf-8")
            run_script("release-gates.py", str(path), *gate_args, expect=2)

    def test_release_gate_uses_policy_p95_thresholds(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            policy = root / "policy.yaml"
            policy.write_text(
                "schemaVersion: 1\n"
                "thresholds:\n"
                "  minimumTraffic: 1\n"
                "  errorRatio: 0.01\n"
                "  maxRestarts: 1\n"
                "  p95AbsoluteSeconds: 2.0\n"
                "  p95Multiplier: 1.5\n",
                encoding="utf-8",
            )
            samples = root / "gates.json"
            samples.write_text(
                json.dumps(
                    {
                        "environment": "staging",
                        "sourceSha": "a" * 40,
                        "manifestDigest": "sha256:" + "b" * 64,
                        "traffic": 2,
                        "errorRatio": 0.0,
                        "p95Seconds": 2.9,
                        "p95LimitSeconds": 3.0,
                        "baselineP95Seconds": 2.0,
                        "readyReplicas": 1,
                        "desiredReplicas": 1,
                        "oomKills": 0,
                        "restarts": 0,
                        "connectorReadiness": "UP",
                        "synthetic": "passed",
                        "collectedAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
                    }
                ),
                encoding="utf-8",
            )
            run_script(
                "release-gates.py",
                str(samples),
                "--policy",
                str(policy),
                "--environment",
                "staging",
                "--source-sha",
                "a" * 40,
                "--manifest-digest",
                "sha256:" + "b" * 64,
            )
            data = json.loads(samples.read_text(encoding="utf-8"))
            data["p95LimitSeconds"] = 2.4
            samples.write_text(json.dumps(data), encoding="utf-8")
            run_script(
                "release-gates.py",
                str(samples),
                "--policy",
                str(policy),
                "--environment",
                "staging",
                "--source-sha",
                "a" * 40,
                "--manifest-digest",
                "sha256:" + "b" * 64,
                expect=2,
            )

    def test_release_gate_sample_rejects_unknown_fields_before_receipt(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sample = root / "sample.json"
            normalized = root / "normalized.json"
            sample_data = {
                "environment": "staging",
                "sourceSha": "a" * 40,
                "manifestDigest": "sha256:" + "b" * 64,
                "traffic": 120,
                "errorRatio": 0.001,
                "p95Seconds": 0.5,
                "p95LimitSeconds": 1.0,
                "baselineP95Seconds": 0.5,
                "readyReplicas": 2,
                "desiredReplicas": 2,
                "oomKills": 0,
                "restarts": 0,
                "connectorReadiness": "UP",
                "synthetic": "passed",
                "collectedAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
                "runnerSecret": "must-not-enter-receipt",
            }
            sample.write_text(json.dumps(sample_data), encoding="utf-8")
            rejected = run_script(
                "normalize-release-gates.py", str(sample), str(normalized), expect=2
            )
            self.assertIn("forbidden fields", rejected.stderr)
            self.assertFalse(normalized.exists())
            sample_data.pop("runnerSecret")
            sample.write_text(json.dumps(sample_data), encoding="utf-8")
            run_script("normalize-release-gates.py", str(sample), str(normalized))
            self.assertNotIn("runnerSecret", normalized.read_text(encoding="utf-8"))

    def test_prometheus_release_rules_cover_continuous_slo_signals(self) -> None:
        result = run_script("verify-observability-rules.py")
        self.assertIn("6 alerts", result.stdout)
        rules = (ROOT / "observability" / "prometheus-rules.yaml").read_text(encoding="utf-8")
        self.assertIn("DmhReleaseErrorBudgetBurnCritical", rules)
        self.assertIn("DmhConnectorRuntimeNotReady", rules)
        self.assertIn("min(dm_connector_runtime_readiness", rules)
        critical = rules.split("alert: DmhReleaseErrorBudgetBurnCritical", 1)[1].split("alert:", 1)[0]
        warning = rules.split("alert: DmhReleaseErrorBudgetBurnWarning", 1)[1].split("alert:", 1)[0]
        self.assertIn("[1h]", critical)
        self.assertIn("[6h]", warning)
        self.assertIn("\n          and\n", critical)
        self.assertIn("\n          and\n", warning)

    def test_release_gate_policy_rejects_weakened_thresholds(self) -> None:
        run_script("verify-release-gates-policy.py")
        with tempfile.TemporaryDirectory() as directory:
            policy = Path(directory) / "release-gates.yaml"
            source = (ROOT / "observability/release-gates.yaml").read_text(encoding="utf-8")
            policy.write_text(source.replace("errorRatio: 0.01", "errorRatio: 0.02"), encoding="utf-8")
            rejected = run_script("verify-release-gates-policy.py", str(policy), expect=2)
            self.assertIn("thresholds.errorRatio", rejected.stderr)

    def test_release_gate_collection_binds_prometheus_baseline(self) -> None:
        deploy_workflow = (ROOT / ".github/workflows/_deploy-reusable.yml").read_text(encoding="utf-8")
        self.assertIn("observation_seconds", deploy_workflow)
        self.assertIn('"$observation_seconds" -ge 900', deploy_workflow)
        self.assertIn("--window baseline", deploy_workflow)
        with tempfile.TemporaryDirectory() as directory:
            rejected = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "collect-release-gates.py"),
                    "--url",
                    "http://prometheus.example",
                    "--namespace",
                    "dmh-staging",
                    "--environment",
                    "staging",
                    "--source-sha",
                    "a" * 40,
                    "--manifest-digest",
                    "sha256:" + "b" * 64,
                    "--synthetic-status",
                    "passed",
                    "--output",
                    str(Path(directory) / "gates.json"),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected.returncode, 2)
            self.assertIn("refuses bearer-token transport", rejected.stderr)
            rejected_https = subprocess.run(
                [
                    "python3",
                    str(SCRIPTS / "collect-release-gates.py"),
                    "--url",
                    "https://prometheus.example",
                    "--namespace",
                    "dmh-staging",
                    "--environment",
                    "staging",
                    "--source-sha",
                    "a" * 40,
                    "--manifest-digest",
                    "sha256:" + "b" * 64,
                    "--synthetic-status",
                    "passed",
                    "--output",
                    str(Path(directory) / "https-gates.json"),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_https.returncode, 2)
            self.assertIn("requires PROMETHEUS_BEARER_TOKEN", rejected_https.stderr)

        values = {
            "status=~\"5..\"": 0.2,
            "http_server_requests_seconds_count": 200.0,
            "http_server_requests_seconds_bucket": 0.4,
            "kube_deployment_status_replicas_available": 2.0,
            "kube_deployment_spec_replicas": 2.0,
            "kube_pod_container_status_last_terminated_reason": 0.0,
            "kube_pod_container_status_restarts_total": 0.0,
            "dm_connector_runtime_readiness": 1.0,
        }

        class PrometheusHandler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *_args: object) -> None:
                return

            def do_GET(self) -> None:  # noqa: N802 - stdlib handler API
                query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).get("query", [""])[0]
                queries.append(query)
                value = next((number for marker, number in values.items() if marker in query), None)
                if value is None:
                    payload = {"status": "success", "data": {"result": []}}
                else:
                    payload = {"status": "success", "data": {"result": [{"value": ["1", str(value)]}]}}
                raw = json.dumps(payload).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)

        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), PrometheusHandler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                baseline = root / "baseline.json"
                baseline_output = root / "pre-release-gates.json"
                output = root / "gates.json"
                baseline.write_text(json.dumps({"p95Seconds": 0.5}), encoding="utf-8")
                queries: list[str] = []
                baseline_result = subprocess.run(
                    [
                        "python3",
                        str(SCRIPTS / "collect-release-gates.py"),
                        "--url",
                        f"http://127.0.0.1:{server.server_port}",
                        "--namespace",
                        "dmh-staging",
                        "--environment",
                        "staging",
                        "--source-sha",
                        "a" * 40,
                        "--manifest-digest",
                        "sha256:" + "b" * 64,
                        "--synthetic-status",
                        "not-run",
                        "--window",
                        "baseline",
                        "--output",
                        str(baseline_output),
                    ],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                )
                self.assertEqual(baseline_result.returncode, 0, baseline_result.stderr)
                self.assertEqual(json.loads(baseline_output.read_text(encoding="utf-8"))["p95Seconds"], 0.4)
                self.assertTrue(any("increase" in query and "[30m]" in query for query in queries))
                result = subprocess.run(
                    [
                        "python3",
                        str(SCRIPTS / "collect-release-gates.py"),
                        "--url",
                        f"http://127.0.0.1:{server.server_port}",
                        "--namespace",
                        "dmh-staging",
                        "--environment",
                        "staging",
                        "--source-sha",
                        "a" * 40,
                        "--manifest-digest",
                        "sha256:" + "b" * 64,
                        "--synthetic-status",
                        "passed",
                        "--baseline-file",
                        str(baseline),
                        "--output",
                        str(output),
                    ],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                gates = json.loads(output.read_text(encoding="utf-8"))
                self.assertEqual(gates["environment"], "staging")
                self.assertEqual(gates["sourceSha"], "a" * 40)
                self.assertEqual(gates["manifestDigest"], "sha256:" + "b" * 64)
                self.assertEqual(gates["traffic"], 200.0)
                self.assertEqual(gates["p95LimitSeconds"], 1.0)
                self.assertEqual(gates["connectorReadiness"], "UP")
                self.assertTrue(any("rate" in query and "[5m]" in query for query in queries))
        finally:
            server.shutdown()
            server.server_close()

    def test_live_image_verification_matches_workload_digests(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "manifest.json"
            images = {}
            for component in ("gateway", "masterdata", "access", "billing", "identity", "governance", "web", "dbops", "acceptance"):
                digest = "sha256:" + (component.encode().hex() + "0" * 64)[:64]
                images[component] = {"reference": f"ghcr.io/lixud/data-manager-hub-{component}@{digest}"}
            manifest.write_text(json.dumps({"spec": {"images": images}}), encoding="utf-8")
            kubectl = root / "kubectl"
            kubectl.write_text(
                "#!/usr/bin/env python3\n"
                "import json, sys\n"
                "args=sys.argv[1:]\n"
                "if 'get' not in args: raise SystemExit('unexpected kubectl call')\n"
                "resource=args[args.index('get')+1]\n"
                "name=resource.split('/',1)[1]\n"
                "component=name.rsplit('-',1)[-1]\n"
                "print(json.dumps({'spec': {'template': {'spec': {'containers': [{'image': 'ghcr.io/lixud/data-manager-hub-'+component+'@sha256:' + (component.encode().hex() + '0'*64)[:64]}]}}}}))\n",
                encoding="utf-8",
            )
            kubectl.chmod(kubectl.stat().st_mode | 0o111)
            env = os.environ.copy()
            env.update({"PATH": f"{root}:{env['PATH']}", "NAMESPACE": "dmh-staging", "MANIFEST": str(manifest)})
            result = subprocess.run(
                ["bash", str(SCRIPTS / "verify-live-images.sh")],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_private_job_renders_secret_and_explicit_env_before_apply(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            capture = root / "job.json"
            kubectl = root / "kubectl"
            kubectl.write_text(
                "#!/usr/bin/env python3\n"
                "import json, os, sys\n"
                "if 'create' in sys.argv:\n"
                "    print(json.dumps({'apiVersion':'batch/v1','kind':'Job','metadata':{'name':'test'},'spec':{'template':{'spec':{'containers':[{'name':'test','image':'ghcr.io/lixud/data-manager-hub-dbops@sha256:'+'a'*64}]}}}}))\n"
                "elif 'apply' in sys.argv:\n"
                "    open(os.environ['CAPTURE'], 'w', encoding='utf-8').write(sys.stdin.read())\n"
                "else:\n"
                "    raise SystemExit('unexpected kubectl invocation')\n",
                encoding="utf-8",
            )
            kubectl.chmod(kubectl.stat().st_mode | 0o111)
            env = os.environ.copy()
            env["PATH"] = f"{root}:{env['PATH']}"
            env["CAPTURE"] = str(capture)
            env["ENV_SECRETS"] = "dmh-runtime"
            result = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--env",
                    "NACOS_GROUP=DMH_STAGING_sha=with=equals",
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={**env, "NAMESPACE": "dmh-staging", "JOB": "job-1", "IMAGE": "ghcr.io/lixud/data-manager-hub-dbops@sha256:" + "a" * 64},
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            rendered = json.loads(capture.read_text(encoding="utf-8"))
            pod = rendered["spec"]["template"]["spec"]
            self.assertEqual(rendered["spec"]["backoffLimit"], 0)
            self.assertEqual(pod["serviceAccountName"], "dmh-runtime")
            self.assertFalse(pod["automountServiceAccountToken"])
            self.assertTrue(pod["securityContext"]["runAsNonRoot"])
            container = rendered["spec"]["template"]["spec"]["containers"][0]
            self.assertTrue(container["securityContext"]["readOnlyRootFilesystem"])
            self.assertEqual(
                {volume["name"] for volume in pod["volumes"]},
                {"tmp", "workspace-target", "runtime"},
            )
            self.assertEqual(
                {mount["mountPath"] for mount in container["volumeMounts"]},
                {"/tmp", "/workspace/target", "/workspace/.runtime"},
            )
            self.assertEqual(container["args"], ["nacos"])
            self.assertEqual(
                container["envFrom"],
                [{"secretRef": {"name": "dmh-runtime"}}],
            )
            self.assertEqual(container["env"], [{"name": "NACOS_GROUP", "value": "DMH_STAGING_sha=with=equals"}])

            acceptance_result = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "acceptance-1",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-acceptance@sha256:" + "b" * 64,
                    "ENV_SECRETS": "dmh-runtime,dmh-acceptance",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(acceptance_result.returncode, 0, acceptance_result.stderr)
            acceptance_pod = json.loads(capture.read_text(encoding="utf-8"))["spec"]["template"]["spec"]
            self.assertFalse(acceptance_pod["containers"][0]["securityContext"]["readOnlyRootFilesystem"])

            rejected = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--env",
                    "NACOS_GROUP=DMH_STAGING_sha=with=equals",
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-2",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-dbops@sha256:" + "a" * 64,
                    "SERVICE_ACCOUNT": "dmh-deployer",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected.returncode, 64)
            self.assertIn("least-privilege", rejected.stderr)

            rejected_secret = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-3",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-dbops@sha256:" + "a" * 64,
                    "ENV_SECRETS": "dmh-internal-auth",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_secret.returncode, 64)
            self.assertIn("image-to-Secret contract", rejected_secret.stderr)

            rejected_image_secret = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-image-secret",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-dbops@sha256:" + "a" * 64,
                    "ENV_SECRETS": "dmh-runtime,dmh-acceptance",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_image_secret.returncode, 64)
            self.assertIn("image-to-Secret contract", rejected_image_secret.stderr)

            rejected_env = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--env",
                    "DB_PASSWORD=should-not-be-inline",
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-4",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-dbops@sha256:" + "a" * 64,
                    "ENV_SECRETS": "dmh-runtime",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_env.returncode, 64)
            self.assertIn("not allowlisted", rejected_env.stderr)

            rejected_image_namespace = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-namespace",
                    "IMAGE": "docker.io/library/alpine@sha256:" + "a" * 64,
                    "ENV_SECRETS": "dmh-runtime",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_image_namespace.returncode, 64)
            self.assertIn("data-manager-hub GHCR", rejected_image_namespace.stderr)

            rejected_tag = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-5",
                    "IMAGE": "example:latest",
                    "ENV_SECRETS": "dmh-runtime",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_tag.returncode, 64)
            self.assertIn("data-manager-hub GHCR", rejected_tag.stderr)

            rejected_component = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--",
                    "migrate",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-component",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-gateway@sha256:" + "a" * 64,
                    "ENV_SECRETS": "dmh-runtime",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_component.returncode, 64)
            self.assertIn("dbops or acceptance", rejected_component.stderr)

            rejected_acceptance_args = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--",
                    "migrate",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-acceptance-args",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-acceptance@sha256:" + "b" * 64,
                    "ENV_SECRETS": "dmh-runtime,dmh-acceptance",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_acceptance_args.returncode, 64)
            self.assertIn("without arguments", rejected_acceptance_args.stderr)

            rejected_direct_nacos_host = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--env",
                    "NACOS_SERVER_ADDR=https://attacker.invalid",
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-nacos-host",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-dbops@sha256:" + "a" * 64,
                    "ENV_SECRETS": "dmh-runtime",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_direct_nacos_host.returncode, 64)
            self.assertIn("not allowlisted", rejected_direct_nacos_host.stderr)

            rejected_retry = subprocess.run(
                [
                    "bash",
                    str(SCRIPTS / "create-private-job.sh"),
                    "--",
                    "nacos",
                ],
                cwd=ROOT,
                env={
                    **env,
                    "NAMESPACE": "dmh-staging",
                    "JOB": "job-6",
                    "IMAGE": "ghcr.io/lixud/data-manager-hub-dbops@sha256:" + "a" * 64,
                    "ENV_SECRETS": "dmh-runtime",
                    "BACKOFF_LIMIT": "1",
                },
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected_retry.returncode, 64)
            self.assertIn("backoffLimit=0", rejected_retry.stderr)

    def test_wait_for_job_distinguishes_failed_from_timeout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            kubectl = root / "kubectl"
            kubectl.write_text(
                "#!/usr/bin/env python3\n"
                "import json, os\n"
                "state = os.environ['JOB_STATE']\n"
                "condition = {'type': state, 'status': 'True'}\n"
                "print(json.dumps({'status': {'conditions': [condition]}}))\n",
                encoding="utf-8",
            )
            kubectl.chmod(kubectl.stat().st_mode | 0o111)
            env = {**os.environ, "PATH": f"{root}:{os.environ['PATH']}", "NAMESPACE": "dmh-staging", "JOB": "job-1", "TIMEOUT_SECONDS": "1"}
            completed = subprocess.run(
                ["bash", str(SCRIPTS / "wait-for-job.sh")],
                cwd=ROOT,
                env={**env, "JOB_STATE": "Complete"},
                text=True,
                capture_output=True,
            )
            self.assertEqual(completed.returncode, 0, completed.stderr)
            failed = subprocess.run(
                ["bash", str(SCRIPTS / "wait-for-job.sh")],
                cwd=ROOT,
                env={**env, "JOB_STATE": "Failed"},
                text=True,
                capture_output=True,
            )
            self.assertEqual(failed.returncode, 2)
            self.assertIn("Job failed", failed.stderr)
            workflow = (ROOT / ".github/workflows/_deploy-reusable.yml").read_text(encoding="utf-8")
            self.assertIn("wait-for-job.sh", workflow)

    def test_cluster_preflight_checks_metadata_without_reading_secret_values(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            calls = root / "kubectl.calls"
            kubectl = root / "kubectl"
            kubectl.write_text(
                "#!/usr/bin/env python3\n"
                "import os, sys\n"
                "with open(os.environ['CAPTURE'], 'a', encoding='utf-8') as handle:\n"
                "    handle.write(' '.join(sys.argv[1:]) + '\\n')\n"
                "args = sys.argv[1:]\n"
                "if 'auth' in args and 'whoami' in args:\n"
                "    print('{\"status\":{\"userInfo\":{\"username\":\"system:serviceaccount:dmh-staging:dmh-deployer\"}}}')\n"
                "elif 'auth' in args and 'can-i' in args:\n"
                "    forbidden = {'list', 'watch', 'create', 'update', 'patch', 'delete', 'deletecollection'}\n"
                "    print('no' if args[args.index('can-i') + 1] in forbidden and 'secrets' in args else 'yes')\n"
                "elif 'get' in args:\n"
                "    print('resource/name')\n"
                "elif 'create' in args and '--dry-run=client' in args:\n"
                "    print('{\"apiVersion\":\"batch/v1\",\"kind\":\"Job\",\"metadata\":{\"name\":\"dmh-admission-probe\"},\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"probe\",\"image\":\"ghcr.io/lixud/data-manager-hub-dbops@sha256:0000000000000000000000000000000000000000000000000000000000000000\"}]}}}}}')\n"
                "elif 'create' in args and '--dry-run=server' in args:\n"
                "    print('The resource is invalid: private data-manager-hub Jobs must use the image entrypoint', file=sys.stderr)\n"
                "    raise SystemExit(1)\n"
                "else:\n"
                "    raise SystemExit(f'unexpected kubectl invocation: {args}')\n",
                encoding="utf-8",
            )
            kubectl.chmod(kubectl.stat().st_mode | 0o111)
            env = os.environ.copy()
            env.update(
                {
                    "PATH": f"{root}:{env['PATH']}",
                    "CAPTURE": str(calls),
                    "NAMESPACE": "dmh-staging",
                    "ENVIRONMENT": "staging",
                }
            )
            result = subprocess.run(
                ["bash", str(SCRIPTS / "preflight-cluster.sh")],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            call_text = calls.read_text(encoding="utf-8")
            self.assertIn("get secret dmh-internal-auth -o name", call_text)
            self.assertIn("get serviceaccount dmh-runtime -o name", call_text)
            self.assertIn("auth can-i get secret/dmh-runtime -n dmh-staging", call_text)
            self.assertIn("auth can-i list secrets -n dmh-staging", call_text)
            self.assertIn("auth can-i patch secrets -n dmh-staging", call_text)
            self.assertIn("auth can-i delete secrets -n dmh-staging", call_text)
            self.assertIn("auth can-i get pods/log -n dmh-staging", call_text)
            self.assertIn("auth can-i list pods -n dmh-staging", call_text)
            self.assertIn("auth can-i delete deployments -n dmh-staging", call_text)
            self.assertIn("auth can-i get persistentvolumeclaims -n dmh-staging", call_text)
            self.assertIn("auth can-i create networkpolicies.networking.k8s.io -n dmh-staging", call_text)
            self.assertIn("auth can-i patch poddisruptionbudgets.policy -n dmh-staging", call_text)
            self.assertIn("create job dmh-admission-probe -n dmh-staging", call_text)
            self.assertIn("create --dry-run=server -f -", call_text)
            self.assertNotIn("auth can-i create serviceaccounts -n dmh-staging", call_text)
            self.assertNotIn("auth can-i patch serviceaccounts -n dmh-staging", call_text)
            self.assertNotIn("auth can-i delete serviceaccounts -n dmh-staging", call_text)
            self.assertNotIn("jsonpath", call_text)
            self.assertNotIn(".data", call_text)

    def test_cluster_preflight_rejects_broad_secret_permissions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            kubectl = root / "kubectl"
            kubectl.write_text(
                "#!/usr/bin/env python3\n"
                "import sys\n"
                "args = sys.argv[1:]\n"
                "if 'auth' in args and 'whoami' in args:\n"
                "    print('{\"status\":{\"userInfo\":{\"username\":\"system:serviceaccount:dmh-staging:dmh-deployer\"}}}')\n"
                "elif 'auth' in args and 'can-i' in args:\n"
                "    print('yes')\n"
                "elif 'get' in args:\n"
                "    print('resource/name')\n"
                "else:\n"
                "    raise SystemExit(f'unexpected kubectl invocation: {args}')\n",
                encoding="utf-8",
            )
            kubectl.chmod(kubectl.stat().st_mode | 0o111)
            env = os.environ.copy()
            env.update(
                {
                    "PATH": f"{root}:{env['PATH']}",
                    "NAMESPACE": "dmh-staging",
                    "ENVIRONMENT": "staging",
                }
            )
            result = subprocess.run(
                ["bash", str(SCRIPTS / "preflight-cluster.sh")],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("must not have list secrets permission", result.stderr)

    def test_nacos_publish_apply_verify_and_drift_contract(self) -> None:
        state: dict[tuple[str, str, str], str] = {}
        namespaces: set[str] = set()

        class NacosHandler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *_args: object) -> None:
                return

            def respond(self, status: int, body: str, content_type: str = "text/plain") -> None:
                raw = body.encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)

            def do_GET(self) -> None:  # noqa: N802 - stdlib handler API
                parsed = urllib.parse.urlparse(self.path)
                query = urllib.parse.parse_qs(parsed.query)
                if parsed.path.endswith("/console/health/readiness"):
                    self.respond(200, "ok")
                elif parsed.path.endswith("/console/namespaces"):
                    body = json.dumps([{"namespace": value} for value in sorted(namespaces)], separators=(",", ":"))
                    self.respond(200, body, "application/json")
                elif parsed.path.endswith("/cs/configs"):
                    key = (query.get("dataId", [""])[0], query.get("group", [""])[0], query.get("tenant", [""])[0])
                    if key not in state:
                        self.respond(404, "not found")
                    else:
                        self.respond(200, state[key])
                else:
                    self.respond(404, "not found")

            def do_POST(self) -> None:  # noqa: N802 - stdlib handler API
                parsed = urllib.parse.urlparse(self.path)
                length = int(self.headers.get("Content-Length", "0"))
                form = urllib.parse.parse_qs(self.rfile.read(length).decode("utf-8"))
                if parsed.path.endswith("/console/namespaces"):
                    namespaces.add(form["customNamespaceId"][0])
                    self.respond(200, "true")
                elif parsed.path.endswith("/cs/configs"):
                    key = (form["dataId"][0], form["group"][0], form["tenant"][0])
                    state[key] = form.get("content", [""])[0]
                    self.respond(200, "true")
                else:
                    self.respond(404, "not found")

        server = http.server.ThreadingHTTPServer(("0.0.0.0", 0), NacosHandler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                probe.connect(("198.51.100.1", 9))
                nacos_host = probe.getsockname()[0]
            finally:
                probe.close()
            env = os.environ.copy()
            env.update(
                {
                    "NACOS_SERVER_ADDR": f"{nacos_host}:{server.server_port}",
                    "NACOS_SCHEME": "http",
                    "NACOS_NAMESPACE": "ci-test",
                    "NACOS_GROUP": "DMH_PROD_0123456789abcdef0123456789abcdef01234567",
                    "NACOS_STARTUP_ATTEMPTS": "3",
                    "NACOS_CONFIG_DRY_RUN": "false",
                }
            )

            # Offline plans must not require an endpoint or create a namespace.
            plan_env = {key: value for key, value in env.items() if key != "NACOS_SERVER_ADDR"}
            plan_env["NACOS_MODE"] = "plan"
            plan_env["NACOS_GROUP"] = "DMH_STAGING_0123456789abcdef0123456789abcdef01234567"
            planned = subprocess.run(
                ["bash", "./publish-nacos-config.sh", "staging"],
                cwd=ROOT,
                env=plan_env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(planned.returncode, 0, planned.stderr)
            self.assertIn("plan:", planned.stdout)

            dry_run_env = {key: value for key, value in env.items() if key != "NACOS_SERVER_ADDR"}
            dry_run_env["NACOS_MODE"] = "apply"
            dry_run_env["NACOS_CONFIG_DRY_RUN"] = "true"
            dry_run_env["NACOS_GROUP"] = "DMH_STAGING_0123456789abcdef0123456789abcdef01234567"
            dry_run = subprocess.run(
                ["bash", "./publish-nacos-config.sh", "staging"],
                cwd=ROOT,
                env=dry_run_env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(dry_run.returncode, 0, dry_run.stderr)
            self.assertIn("plan:", dry_run.stdout)
            self.assertEqual(namespaces, set())
            self.assertEqual(state, {})

            def run(mode: str) -> subprocess.CompletedProcess[str]:
                mode_env = {**env, "NACOS_MODE": mode}
                return subprocess.run(
                    ["bash", "./publish-nacos-config.sh", "prod"],
                    cwd=ROOT,
                    env=mode_env,
                    text=True,
                    capture_output=True,
                )

            first = run("apply")
            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(len(state), 7)
            second = run("apply")
            self.assertEqual(second.returncode, 0, second.stderr)
            verified = run("verify")
            self.assertEqual(verified.returncode, 0, verified.stderr)
            namespaces.clear()
            verify_missing_namespace = run("verify")
            self.assertEqual(verify_missing_namespace.returncode, 2)
            self.assertNotIn("ci-test", namespaces)
            namespaces.add("ci-test")
            key = next(iter(state))
            original = state[key]
            state[key] = "drifted"
            drift = run("apply")
            self.assertEqual(drift.returncode, 2, drift.stdout + drift.stderr)
            state[key] = original
        finally:
            server.shutdown()
            server.server_close()

    def test_access_rollout_processes_each_ordinal_once(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            capture = root / "patches.log"
            kubectl = root / "kubectl"
            kubectl.write_text(
                "#!/usr/bin/env python3\n"
                "import os, sys\n"
                "args = sys.argv[1:]\n"
                "capture = os.environ['CAPTURE']\n"
                "if 'patch' in args:\n"
                "    payload = args[args.index('-p') + 1]\n"
                "    if 'partition:-1' in payload or 'partition\\\":-1' in payload:\n"
                "        raise SystemExit('partition -1 must never be patched')\n"
                "    with open(capture, 'a', encoding='utf-8') as handle:\n"
                "        handle.write(payload + '\\n')\n"
                "    raise SystemExit(0)\n"
                "if 'get' in args:\n"
                "    get_index = args.index('get')\n"
                "    resource_type, resource_name = args[get_index + 1:get_index + 3]\n"
                "    jsonpath = args[args.index('-o') + 1]\n"
                "    if resource_type == 'statefulset' and '.spec.replicas' in jsonpath:\n"
                "        print('2')\n"
                "    elif resource_type == 'statefulset' and '.status.updateRevision' in jsonpath:\n"
                "        print('rev-2')\n"
                "    elif resource_type == 'pod' and resource_name.endswith('-1') and '.metadata.labels' in jsonpath:\n"
                "        print('rev-2')\n"
                "    elif resource_type == 'pod' and resource_name.endswith('-0') and '.metadata.labels' in jsonpath:\n"
                "        print('rev-2')\n"
                "    elif resource_type == 'pod' and '.status.conditions' in jsonpath:\n"
                "        print('True')\n"
                "    else:\n"
                "        raise SystemExit(f'unexpected kubectl get: {args}')\n"
                "    raise SystemExit(0)\n"
                "raise SystemExit(f'unexpected kubectl invocation: {args}')\n",
                encoding="utf-8",
            )
            kubectl.chmod(kubectl.stat().st_mode | 0o111)
            env = os.environ.copy()
            env.update(
                {
                    "PATH": f"{root}:{env['PATH']}",
                    "CAPTURE": str(capture),
                    "NAMESPACE": "dmh-staging",
                    "STATEFULSET": "data-manager-hub-access",
                }
            )
            result = subprocess.run(
                ["bash", str(SCRIPTS / "access-rollout.sh")],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            patches = capture.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(patches), 2)
            self.assertNotIn("partition:-1", "\n".join(patches))
            self.assertIn("replicas=2", result.stdout)

    def test_helm_policy_rejects_privileged_or_unpinned_workload(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            rendered = Path(directory) / "rendered.yaml"
            rendered.write_text(
                """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: unsafe
spec:
  template:
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        seccompProfile: {type: RuntimeDefault}
      containers:
        - name: app
          image: example/app:latest
          securityContext:
            privileged: true
            allowPrivilegeEscalation: true
            readOnlyRootFilesystem: false
            capabilities: {drop: []}
          resources: {requests: {cpu: 1m}, limits: {cpu: 1m}}
          livenessProbe: {httpGet: {path: /health, port: 8080}}
          readinessProbe: {httpGet: {path: /health, port: 8080}}
""",
                encoding="utf-8",
            )
            run_script("check-helm-policy.py", str(rendered), expect=2)

    def test_helm_policy_rejects_mutable_non_dev_nacos_group(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            rendered = Path(directory) / "rendered.yaml"
            rendered.write_text(
                """
apiVersion: v1
kind: ConfigMap
metadata:
  name: data-manager-hub-release
data:
  environment: production
  nacosGroup: DMH_PROD_LOCAL
""",
                encoding="utf-8",
            )
            rejected = subprocess.run(
                ["python3", str(SCRIPTS / "check-helm-policy.py"), str(rendered)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected.returncode, 2)
            self.assertIn("not immutable for production", rejected.stderr)
            rendered.write_text(
                rendered.read_text(encoding="utf-8").replace(
                    "DMH_PROD_LOCAL", "DMH_PROD_" + "a" * 40
                ),
                encoding="utf-8",
            )
            accepted = subprocess.run(
                ["python3", str(SCRIPTS / "check-helm-policy.py"), str(rendered)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(accepted.returncode, 0, accepted.stderr)

    def test_helm_policy_requires_access_partition_before_rollout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            rendered = Path(directory) / "rendered.yaml"
            rendered.write_text(
                """
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: data-manager-hub-access
spec:
  replicas: 2
  updateStrategy:
    type: RollingUpdate
    rollingUpdate:
      partition: 0
  template:
    spec:
      serviceAccountName: dmh-runtime
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        seccompProfile: {type: RuntimeDefault}
      containers:
        - name: access
          image: ghcr.io/lixud/data-manager-hub-access@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: {drop: [ALL]}
          resources: {requests: {cpu: 1m}, limits: {cpu: 1m}}
          livenessProbe: {httpGet: {path: /health, port: 8080}}
          readinessProbe: {httpGet: {path: /health, port: 8080}}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: data-manager-hub-release
data:
  environment: staging
  nacosGroup: DMH_STAGING_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
""",
                encoding="utf-8",
            )
            rejected = subprocess.run(
                ["python3", str(SCRIPTS / "check-helm-policy.py"), str(rendered)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(rejected.returncode, 2, rejected.stdout + rejected.stderr)
            self.assertIn("partition=replicas-1", rejected.stderr)
            rendered.write_text(
                rendered.read_text(encoding="utf-8").replace("partition: 0", "partition: 1"),
                encoding="utf-8",
            )
            accepted = subprocess.run(
                ["python3", str(SCRIPTS / "check-helm-policy.py"), str(rendered)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(accepted.returncode, 0, accepted.stdout + accepted.stderr)


if __name__ == "__main__":
    unittest.main()
