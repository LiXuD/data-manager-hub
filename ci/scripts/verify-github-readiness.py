#!/usr/bin/env python3
"""Read-only audit of the GitHub controls required before CI/CD promotion.

This command deliberately performs no mutation.  It turns the repository's
external GitHub prerequisites into a machine-checkable evidence record so a
successful local CI run cannot be mistaken for a configured production
pipeline.  The same command can be run by a platform administrator after
GitHub Environments, branch protection, and protected runners are provisioned.
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


REPOSITORY_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
DEFAULT_ENVIRONMENTS = ("dev", "staging", "production", "plugin-signing")
DEFAULT_APPROVAL_ENVIRONMENTS = ("staging", "production", "plugin-signing")
DEFAULT_RUNNER_LABELS = ("nonprod-deploy", "prod-deploy", "plugin-signing")
DEFAULT_CODEOWNER_PATTERNS = ("/ci/", "/deploy/", "/docker/", "/nacos-config/", "/observability/")
DEFAULT_ENVIRONMENT_SECRETS = (
    "staging=DMH_PROMETHEUS_URL",
    "staging=DMH_PROMETHEUS_BEARER_TOKEN",
    "production=DMH_PRODUCTION_DB_INSTANCE",
    "production=DMH_SNAPSHOT_ADAPTER_BIN",
    "production=DMH_SNAPSHOT_SIGNATURE_VERIFIER",
    "production=DMH_PROMETHEUS_URL",
    "production=DMH_PROMETHEUS_BEARER_TOKEN",
    "plugin-signing=DMH_PLUGIN_SIGNING_ADAPTER",
)


class GithubApiError(RuntimeError):
    """Raised when a read-only GitHub API request cannot be proven."""


def gh_json(repository: str, endpoint: str, *, paginate: bool = False) -> Any:
    command = ["gh", "api"]
    if paginate:
        command.extend(["--paginate", "--slurp"])
    command.append(endpoint)
    result = subprocess.run(
        command,
        env=os.environ.copy(),
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or "unknown GitHub API error"
        raise GithubApiError(f"{endpoint}: {detail}")
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise GithubApiError(f"{endpoint}: GitHub API returned invalid JSON") from exc


def paginated_items(value: Any, key: str, endpoint: str) -> list[dict[str, Any]]:
    """Flatten gh --paginate --slurp responses without trusting page shape."""

    pages = value if isinstance(value, list) else [value]
    items: list[dict[str, Any]] = []
    for page in pages:
        if not isinstance(page, dict) or not isinstance(page.get(key), list):
            raise GithubApiError(f"{endpoint}: response has no {key} list")
        for item in page[key]:
            if not isinstance(item, dict):
                raise GithubApiError(f"{endpoint}: {key} contains a non-object item")
            items.append(item)
    return items


def repository_name(value: str) -> str:
    if not REPOSITORY_RE.fullmatch(value):
        raise ValueError("repository must use OWNER/REPOSITORY syntax")
    return value


def string_list(value: Any, field: str, endpoint: str) -> list[str]:
    """Validate an API list before using it in a set or evidence record."""

    if value is None:
        return []
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        raise GithubApiError(f"{endpoint}: {field} must be a list of strings")
    return value


def environment_protection(
    environment: dict[str, Any],
    name: str,
    *,
    require_approval: bool,
    endpoint: str,
) -> dict[str, Any]:
    rules = environment.get("protection_rules") or []
    if not isinstance(rules, list) or any(not isinstance(rule, dict) for rule in rules):
        raise GithubApiError(f"{endpoint}: {name} protection_rules must be a list of objects")
    reviewer_rules = [rule for rule in rules if rule.get("type") == "required_reviewers"]
    if require_approval and not any(
        isinstance(rule.get("reviewers"), list) and bool(rule["reviewers"]) for rule in reviewer_rules
    ):
        raise GithubApiError(f"{endpoint}: {name} must have at least one required reviewer")
    branch_policy = environment.get("deployment_branch_policy")
    if branch_policy is not None and not isinstance(branch_policy, dict):
        raise GithubApiError(f"{endpoint}: {name} deployment_branch_policy must be an object")
    if require_approval and not (
        isinstance(branch_policy, dict)
        and (branch_policy.get("protected_branches") is True or branch_policy.get("custom_branch_policies") is True)
    ):
        raise GithubApiError(f"{endpoint}: {name} must restrict deployments to protected or custom branches")
    return {
        "requiredReviewerRule": bool(reviewer_rules),
        "deploymentBranchPolicy": branch_policy or {},
    }


def decode_codeowners(value: Any, endpoint: str) -> str:
    if not isinstance(value, dict) or not isinstance(value.get("content"), str):
        raise GithubApiError(f"{endpoint}: CODEOWNERS content is missing")
    try:
        encoded = re.sub(r"\s+", "", value["content"])
        decoded = base64.b64decode(encoded, validate=True)
        return decoded.decode("utf-8")
    except (ValueError, UnicodeDecodeError) as exc:
        raise GithubApiError(f"{endpoint}: CODEOWNERS content is not valid base64 UTF-8") from exc


def require_disabled_flag(protection: dict[str, Any], field: str) -> None:
    value = protection.get(field)
    if not isinstance(value, dict) or value.get("enabled") is not False:
        raise GithubApiError(f"{field} must be disabled on the protected branch")


def parse_environment_secrets(values: list[str]) -> dict[str, set[str]]:
    requirements: dict[str, set[str]] = {}
    for value in values:
        environment, separator, secret = value.partition("=")
        valid_environment = re.fullmatch(r"[A-Za-z0-9_-]+", environment)
        valid_secret = re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", secret)
        if not separator or not environment or not secret or not valid_environment or not valid_secret:
            raise ValueError(f"environment secret must use ENVIRONMENT=SECRET_NAME syntax: {value!r}")
        requirements.setdefault(environment, set()).add(secret)
    return requirements


def audit(args: argparse.Namespace) -> dict[str, Any]:
    repository = repository_name(args.repository)
    repo = gh_json(repository, f"repos/{repository}")
    if not isinstance(repo, dict):
        raise GithubApiError("repository endpoint returned a non-object")
    if repo.get("default_branch") != args.default_branch:
        raise GithubApiError(
            f"default branch is {repo.get('default_branch', '<missing>')!r}; expected {args.default_branch!r}"
        )

    protection = gh_json(repository, f"repos/{repository}/branches/{args.default_branch}/protection")
    if not isinstance(protection, dict):
        raise GithubApiError("branch protection endpoint returned a non-object")
    required_status_checks = protection.get("required_status_checks") or {}
    if not isinstance(required_status_checks, dict):
        raise GithubApiError("required_status_checks must be an object")
    checks = required_status_checks.get("checks") or []
    context_values = string_list(required_status_checks.get("contexts"), "contexts", "branch protection")
    contexts = set(context_values)
    if not isinstance(checks, list):
        raise GithubApiError("required_status_checks.checks is not a list")
    for item in checks:
        if not isinstance(item, dict) or not isinstance(item.get("context"), str):
            raise GithubApiError("required_status_checks.checks contains an invalid check")
        contexts.add(item["context"])
    if args.required_check not in contexts:
        raise GithubApiError(f"required check is missing from branch protection: {args.required_check}")
    if required_status_checks.get("strict") is not True:
        raise GithubApiError("branch protection must require the branch to be up to date")
    require_disabled_flag(protection, "allow_force_pushes")
    require_disabled_flag(protection, "allow_deletions")

    reviews = protection.get("required_pull_request_reviews") or {}
    if not isinstance(reviews, dict):
        raise GithubApiError("required_pull_request_reviews must be an object")
    if reviews.get("dismiss_stale_reviews") is not True:
        raise GithubApiError("branch protection must dismiss stale approvals")
    if reviews.get("require_code_owner_reviews") is not True:
        raise GithubApiError("branch protection must require CODEOWNERS review")
    approval_count = reviews.get("required_approving_review_count")
    if not isinstance(approval_count, int) or isinstance(approval_count, bool) or approval_count < 1:
        raise GithubApiError("branch protection must require at least one approval")

    codeowners_endpoint = f"repos/{repository}/contents/.github/CODEOWNERS?ref={args.default_branch}"
    codeowners = decode_codeowners(gh_json(repository, codeowners_endpoint), codeowners_endpoint)
    missing_patterns = [pattern for pattern in args.require_codeowner_pattern if pattern not in codeowners]
    if missing_patterns:
        raise GithubApiError("CODEOWNERS is missing protected paths: " + ", ".join(missing_patterns))

    environments_response = gh_json(
        repository,
        f"repos/{repository}/environments",
        paginate=True,
    )
    environments = paginated_items(environments_response, "environments", "environments")
    environment_by_name = {}
    for item in environments:
        name = item.get("name")
        if not isinstance(name, str) or not name:
            raise GithubApiError("environments contains an invalid name")
        if name in environment_by_name:
            raise GithubApiError(f"environments contains duplicate name: {name}")
        environment_by_name[name] = item
    environment_names = sorted(environment_by_name)
    missing_environments = sorted(set(args.require_environment) - set(environment_names))
    if missing_environments:
        raise GithubApiError("required GitHub Environments are missing: " + ", ".join(missing_environments))
    environment_policies = {}
    for name in args.require_environment:
        environment_policies[name] = environment_protection(
            environment_by_name[name],
            name,
            require_approval=name in args.require_environment_approval,
            endpoint="environments",
        )

    required_secrets = parse_environment_secrets(args.require_environment_secret)
    environment_secrets: dict[str, list[str]] = {}
    for name, secret_names in required_secrets.items():
        if name not in environment_by_name:
            raise GithubApiError(f"environment secret check refers to missing Environment: {name}")
        endpoint = f"repos/{repository}/environments/{name}/secrets"
        response = gh_json(repository, endpoint)
        if not isinstance(response, dict) or not isinstance(response.get("secrets"), list):
            raise GithubApiError(f"{endpoint}: response has no secrets list")
        actual_names = set()
        for item in response["secrets"]:
            if not isinstance(item, dict) or not isinstance(item.get("name"), str):
                raise GithubApiError(f"{endpoint}: secrets contains an invalid entry")
            actual_names.add(item["name"])
        missing_secrets = sorted(secret_names - actual_names)
        if missing_secrets:
            raise GithubApiError(f"{endpoint}: required secret names are missing: {', '.join(missing_secrets)}")
        environment_secrets[name] = sorted(actual_names)

    runners_response = gh_json(repository, f"repos/{repository}/actions/runners", paginate=True)
    runners = paginated_items(runners_response, "runners", "runners")
    runner_labels = sorted(
        {
            label["name"]
            for runner in runners
            if isinstance(runner.get("labels"), list)
            for label in runner["labels"]
            if isinstance(label, dict) and isinstance(label.get("name"), str)
        }
    )
    online_runner_labels = sorted(
        {
            label["name"]
            for runner in runners
            if runner.get("status") == "online" and isinstance(runner.get("labels"), list)
            for label in runner["labels"]
            if isinstance(label, dict) and isinstance(label.get("name"), str)
        }
    )
    missing_labels = sorted(set(args.require_runner_label) - set(online_runner_labels))
    if missing_labels:
        raise GithubApiError("required online runner labels are missing: " + ", ".join(missing_labels))

    evidence = {
        "apiVersion": "cicd.data-manager-hub/v1",
        "kind": "GithubReadiness",
        "repository": repository,
        "defaultBranch": repo.get("default_branch"),
        "requiredCheck": args.required_check,
        "requiredChecks": sorted(contexts),
        "requiredApprovals": approval_count,
        "codeownersSha256": hashlib.sha256(codeowners.encode("utf-8")).hexdigest(),
        "codeownerPatterns": args.require_codeowner_pattern,
        "environments": environment_names,
        "environmentPolicies": environment_policies,
        "environmentSecrets": environment_secrets,
        "runnerLabels": runner_labels,
        "onlineRunnerLabels": online_runner_labels,
        "checkedAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
    }
    return evidence


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--default-branch", default="master")
    parser.add_argument("--required-check", default="CI / required-ci")
    parser.add_argument("--require-environment", action="append", default=None)
    parser.add_argument("--require-environment-approval", action="append", default=None)
    parser.add_argument("--require-runner-label", action="append", default=None)
    parser.add_argument("--require-codeowner-pattern", action="append", default=None)
    parser.add_argument("--require-environment-secret", action="append", default=None)
    parser.add_argument("--output", help="optional JSON evidence path")
    args = parser.parse_args()
    if args.require_environment is None:
        args.require_environment = list(DEFAULT_ENVIRONMENTS)
    if args.require_environment_approval is None:
        args.require_environment_approval = list(DEFAULT_APPROVAL_ENVIRONMENTS)
    if args.require_runner_label is None:
        args.require_runner_label = list(DEFAULT_RUNNER_LABELS)
    if args.require_codeowner_pattern is None:
        args.require_codeowner_pattern = list(DEFAULT_CODEOWNER_PATTERNS)
    if args.require_environment_secret is None:
        args.require_environment_secret = list(DEFAULT_ENVIRONMENT_SECRETS)
    try:
        evidence = audit(args)
    except (GithubApiError, OSError, ValueError) as exc:
        print(f"GitHub readiness audit failed: {exc}", file=sys.stderr)
        return 2
    encoded = json.dumps(evidence, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
