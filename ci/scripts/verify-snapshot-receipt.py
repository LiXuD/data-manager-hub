#!/usr/bin/env python3
"""Fail-closed validation of a production PostgreSQL snapshot/PITR receipt."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
from pathlib import Path


DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")


def parse_time(value: str) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp must include timezone")
    return parsed.astimezone(dt.timezone.utc)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("receipt")
    parser.add_argument("--source-instance", required=True)
    parser.add_argument("--schema-version", required=True)
    parser.add_argument("--changelog-digest", required=True)
    parser.add_argument("--engine-major", default="16", help="required PostgreSQL major version")
    parser.add_argument("--max-age-hours", type=int, default=2)
    parser.add_argument("--not-before", help="minimum UTC timestamp for snapshot creation")
    parser.add_argument("--now")
    parser.add_argument("--signature-verifier", help="environment-owned executable that verifies receipt authenticity")
    args = parser.parse_args()
    if args.max_age_hours < 1:
        parser.error("--max-age-hours must be positive")
    try:
        receipt = json.loads(Path(args.receipt).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"snapshot receipt error: cannot read JSON: {exc}", file=sys.stderr)
        return 2
    if not isinstance(receipt, dict):
        print("snapshot receipt error: root must be a JSON object", file=sys.stderr)
        return 2
    errors: list[str] = []
    required = (
        "snapshotId", "sourceInstanceId", "engine", "engineVersion", "completedAt",
        "expiresAt", "consistency", "sourceSchemaVersion", "changelogDigest",
        "verificationStatus", "signature",
    )
    allowed = set(required) | {"recoveryPosition", "walLsn", "gtidExecuted"}
    unknown = sorted(set(receipt) - allowed)
    if unknown:
        errors.append("receipt contains forbidden fields: " + ", ".join(unknown))
    for key in required:
        if not receipt.get(key):
            errors.append(f"missing {key}")
    for key in (
        "snapshotId",
        "sourceInstanceId",
        "engine",
        "engineVersion",
        "completedAt",
        "expiresAt",
        "consistency",
        "sourceSchemaVersion",
        "changelogDigest",
        "verificationStatus",
        "signature",
    ):
        if key in receipt and not isinstance(receipt[key], str):
            errors.append(f"{key} must be a string")
    for key in ("recoveryPosition", "walLsn", "gtidExecuted"):
        if key in receipt and not isinstance(receipt[key], str):
            errors.append(f"{key} must be a string")
    if receipt.get("engine") != "postgresql":
        errors.append("engine must be postgresql")
    engine_version = str(receipt.get("engineVersion", ""))
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)*", engine_version) or engine_version.split(".", 1)[0] != args.engine_major:
        errors.append(f"engineVersion must use PostgreSQL major version {args.engine_major}")
    if receipt.get("sourceInstanceId") != args.source_instance:
        errors.append("sourceInstanceId does not match production")
    if receipt.get("consistency") != "TRANSACTION_CONSISTENT":
        errors.append("consistency must be TRANSACTION_CONSISTENT")
    if receipt.get("verificationStatus") != "VERIFIED":
        errors.append("verificationStatus must be VERIFIED")
    recovery_position = (
        receipt.get("recoveryPosition")
        or receipt.get("walLsn")
        or receipt.get("gtidExecuted")
    )
    if not isinstance(recovery_position, str) or not recovery_position.strip():
        errors.append("missing recoveryPosition (or walLsn/gtidExecuted compatibility field)")
    if receipt.get("sourceSchemaVersion") != args.schema_version:
        errors.append("sourceSchemaVersion does not match expected schema")
    if receipt.get("changelogDigest") != args.changelog_digest:
        errors.append("changelogDigest does not match build manifest")
    try:
        now = parse_time(args.now) if args.now else dt.datetime.now(dt.timezone.utc)
    except ValueError as exc:
        errors.append(f"invalid current timestamp: {exc}")
        now = dt.datetime.now(dt.timezone.utc)
    try:
        completed = parse_time(receipt["completedAt"])
        expires = parse_time(receipt["expiresAt"])
        if expires <= completed:
            errors.append("expiresAt must be later than completedAt")
        if args.not_before:
            not_before = parse_time(args.not_before)
            if completed < not_before:
                errors.append("snapshot was completed before the required deployment milestone")
        if completed > now + dt.timedelta(minutes=5):
            errors.append("completedAt is in the future")
        if now - completed > dt.timedelta(hours=args.max_age_hours):
            errors.append("snapshot is too old")
        if expires <= now:
            errors.append("snapshot is expired")
    except (KeyError, ValueError) as exc:
        errors.append(f"invalid timestamp: {exc}")
    signature = str(receipt.get("signature", ""))
    if not DIGEST_RE.fullmatch(signature):
        errors.append("signature must be a sha256 receipt signature")
    if args.signature_verifier:
        verifier = Path(args.signature_verifier)
        if not verifier.is_absolute() or not os.access(verifier, os.X_OK):
            errors.append("signature verifier must be an executable absolute path")
        elif not errors:
            result = subprocess.run(
                [str(verifier), str(Path(args.receipt).resolve())],
                capture_output=True,
                text=True,
                check=False,
            )
            if result.returncode != 0:
                errors.append("external snapshot signature verification failed")
    if errors:
        for error in errors:
            print(f"snapshot receipt error: {error}", file=sys.stderr)
        return 2
    print(json.dumps({"snapshotId": receipt["snapshotId"], "verified": True}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
