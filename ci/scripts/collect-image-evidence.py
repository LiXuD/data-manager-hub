#!/usr/bin/env python3
"""Combine matrix image receipts into the manifest input map."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


EXPECTED_COMPONENTS = {
    "gateway",
    "masterdata",
    "access",
    "billing",
    "identity",
    "governance",
    "web",
    "dbops",
    "acceptance",
}
DIGEST_REF_RE = re.compile(r"^ghcr\.io/[^@\s]+@sha256:[0-9a-f]{64}$")
OCI_DIGEST_REF_RE = re.compile(r"^oci://ghcr\.io/[^@\s]+@sha256:[0-9a-f]{64}$")
ALLOWED_IMAGE_FIELDS = {"reference", "sbom", "provenance"}
IMAGE_NAMESPACE = "ghcr.io/lixud"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory")
    parser.add_argument("output")
    args = parser.parse_args()
    result = {}
    for path in sorted(Path(args.directory).glob("*.json")):
        # Each matrix artifact also carries the raw CycloneDX document. It is
        # evidence for the image but not an image receipt, so it must not be
        # interpreted as a component map.
        if path.name.startswith("sbom-"):
            continue
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"invalid image receipt {path}: {exc}", file=sys.stderr)
            return 2
        unknown_receipt_fields = sorted(set(value) - {"component", "image"})
        if unknown_receipt_fields:
            print(
                f"image receipt {path} contains forbidden fields: {', '.join(unknown_receipt_fields)}",
                file=sys.stderr,
            )
            return 2
        component = value.get("component")
        image = value.get("image")
        if not isinstance(component, str) or component not in EXPECTED_COMPONENTS or not isinstance(image, dict):
            print(f"invalid image receipt: {path}", file=sys.stderr)
            return 2
        unknown = sorted(set(image) - ALLOWED_IMAGE_FIELDS)
        missing = sorted(ALLOWED_IMAGE_FIELDS - set(image))
        if unknown or missing:
            if unknown:
                print(f"image receipt {path} contains forbidden fields: {', '.join(unknown)}", file=sys.stderr)
            if missing:
                print(f"image receipt {path} missing fields: {', '.join(missing)}", file=sys.stderr)
            return 2
        expected_prefix = f"{IMAGE_NAMESPACE}/data-manager-hub-{component}"
        if (
            not isinstance(image["reference"], str)
            or not DIGEST_REF_RE.fullmatch(image["reference"])
            or not image["reference"].startswith(expected_prefix + "@")
        ):
            print(f"image receipt {path} has invalid digest image reference", file=sys.stderr)
            return 2
        for field in ("sbom", "provenance"):
            expected_artifact_prefix = expected_prefix + ("-sbom" if field == "sbom" else "")
            if (
                not isinstance(image[field], str)
                or not OCI_DIGEST_REF_RE.fullmatch(image[field])
                or not image[field].startswith("oci://" + expected_artifact_prefix + "@")
            ):
                print(f"image receipt {path} has invalid {field} digest reference", file=sys.stderr)
                return 2
        if component in result:
            print(f"duplicate image receipt for component: {component}", file=sys.stderr)
            return 2
        result[component] = image
    if set(result) != EXPECTED_COMPONENTS:
        missing = sorted(EXPECTED_COMPONENTS - set(result))
        extra = sorted(set(result) - EXPECTED_COMPONENTS)
        raise SystemExit(
            "image evidence set mismatch"
            + (f"; missing: {', '.join(missing)}" if missing else "")
            + (f"; unexpected: {', '.join(extra)}" if extra else "")
        )
    Path(args.output).write_text(json.dumps(result, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"collected {len(result)} image receipts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
