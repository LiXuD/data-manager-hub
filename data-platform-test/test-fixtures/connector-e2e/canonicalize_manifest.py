#!/usr/bin/env python3
"""Extract and canonicalize the connector manifest, then build its signature payload."""

import argparse
import json
import pathlib
import zipfile


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", type=pathlib.Path)
    parser.add_argument("sha256")
    parser.add_argument("output", type=pathlib.Path)
    args = parser.parse_args()

    if len(args.sha256) != 64 or any(ch not in "0123456789abcdef" for ch in args.sha256):
        raise SystemExit("sha256 must be 64 lowercase hexadecimal characters")
    with zipfile.ZipFile(args.jar) as archive:
        manifest = json.loads(archive.read("META-INF/data-platform/plugin.json"))
    canonical = json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    args.output.write_bytes((canonical + "\n" + args.sha256).encode("utf-8"))


if __name__ == "__main__":
    main()
