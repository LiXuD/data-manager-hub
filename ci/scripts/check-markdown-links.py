#!/usr/bin/env python3
"""Validate repository Markdown relative links and GitHub-style anchors."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit


LINK_RE = re.compile(r"(?<!!)(?:\[[^\]]*\])\(([^)]+)\)")
HEADING_RE = re.compile(r"^#{1,6}\s+(.+?)\s*#*\s*$", re.MULTILINE)
CODE_FENCE_RE = re.compile(r"```.*?```|~~~.*?~~~", re.DOTALL)


def github_slug(text: str) -> str:
    """Return the GitHub-compatible anchor slug for the supported headings."""

    text = re.sub(r"[`*_~]", "", text).strip().lower()
    text = re.sub(r"[^\w\u4e00-\u9fff -]", "", text, flags=re.UNICODE)
    return re.sub(r"[\s-]+", "-", text).strip("-")


def heading_anchors(content: str) -> set[str]:
    anchors: set[str] = set()
    duplicate_count: dict[str, int] = {}
    for match in HEADING_RE.finditer(content):
        base = github_slug(match.group(1))
        if not base:
            continue
        count = duplicate_count.get(base, 0)
        anchor = base if count == 0 else f"{base}-{count}"
        duplicate_count[base] = count + 1
        anchors.add(anchor)
    return anchors


def markdown_files(root: Path) -> list[Path]:
    # AGENTS.md is a local agent instruction file.  Its link to the ignored
    # developer-only CLAUDE.md is intentionally not part of the published
    # repository documentation and must not make the product-doc link gate
    # fail on a clean GitHub runner.
    excluded = {".git", ".gitnexus", ".idea", ".claude", "node_modules", "target", "logs"}
    return sorted(
        path
        for path in root.rglob("*.md")
        if path.name != "AGENTS.md"
        and not any(part in excluded for part in path.relative_to(root).parts)
    )


def validate(root: Path, files: list[Path]) -> list[str]:
    contents = {path: path.read_text(encoding="utf-8") for path in files}
    anchors = {path: heading_anchors(content) for path, content in contents.items()}
    errors: list[str] = []
    for source, content in contents.items():
        # Links in fenced examples are command snippets, not document links;
        # excluding them avoids treating shell/Python syntax as Markdown.
        scan_content = CODE_FENCE_RE.sub("", content)
        for match in LINK_RE.finditer(scan_content):
            target = match.group(1).strip().split(maxsplit=1)[0]
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc:
                continue
            raw_path = unquote(parsed.path)
            raw_anchor = unquote(parsed.fragment).lower()
            if raw_path:
                candidate = (source.parent / raw_path).resolve()
                if not candidate.exists():
                    errors.append(f"{source.relative_to(root)}: missing relative link {target}")
                    continue
            else:
                candidate = source.resolve()
            if raw_anchor and candidate.suffix.lower() == ".md":
                if raw_anchor not in anchors.get(candidate, set()):
                    errors.append(
                        f"{source.relative_to(root)}: missing anchor {target}"
                    )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    files = markdown_files(root)
    errors = validate(root, files)
    if errors:
        for error in errors:
            print(f"markdown link error: {error}", file=sys.stderr)
        return 2
    print(f"markdown relative links and local anchors passed: {len(files)} files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
