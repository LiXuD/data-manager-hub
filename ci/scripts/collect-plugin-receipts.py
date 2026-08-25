#!/usr/bin/env python3
"""Collect signed connector plugin receipts without trusting mutable tags."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
ALLOWED_FIELDS = {
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require", action="store_true", help="fail when no signed plugin receipt is present")
    parser.add_argument("directory", default="plugin-receipts", nargs="?")
    parser.add_argument("output")
    args = parser.parse_args()
    root = Path(args.directory)
    receipts = []
    if root.exists():
        for path in sorted(root.glob("*.json")):
            try:
                receipt = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError) as exc:
                print(f"invalid plugin receipt {path}: {exc}", file=sys.stderr)
                return 2
            if receipt.get("apiVersion") != "cicd.data-manager-hub/v1" or receipt.get("kind") != "PluginReceipt":
                print(f"plugin receipt {path} has invalid apiVersion/kind", file=sys.stderr)
                return 2
            unknown = sorted(set(receipt) - ALLOWED_FIELDS)
            if unknown:
                print(f"plugin receipt {path} contains forbidden fields: {', '.join(unknown)}", file=sys.stderr)
                return 2
            required = ("id", "version", "repository", "sha256", "signatureSha256")
            missing = [key for key in required if not receipt.get(key)]
            if not receipt.get("signatureFingerprint") and not receipt.get("signingKeyFingerprint"):
                missing.append("signatureFingerprint")
            if missing:
                print(f"plugin receipt {path} missing: {', '.join(missing)}", file=sys.stderr)
                return 2
            for field in ("sha256", "signatureSha256"):
                value = str(receipt[field])
                if not DIGEST_RE.fullmatch(value):
                    print(f"plugin receipt {path} has invalid {field}", file=sys.stderr)
                    return 2
            receipts.append(receipt)
    if args.require and not receipts:
        print("plugin receipts are required for this source change", file=sys.stderr)
        return 2
    Path(args.output).write_text(json.dumps(receipts, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"collected {len(receipts)} plugin receipts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
