#!/usr/bin/env python3
"""Verify that every user-facing Spring controller route has a policy entry.

This is intentionally a small source contract rather than a runtime scanner. It
fails closed when a controller mapping cannot be reduced to a stable method and
path, while explicitly excluding service-internal and API-key public surfaces.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


HTTP_METHODS = ("GET", "POST", "PUT", "PATCH", "DELETE")
MAPPING_NAMES = {"GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping"}
NON_USER_PREFIXES = ("/internal", "/openapi/v1")
PUBLIC_AUTH_ROUTES = {("POST", "/auth/login"), ("GET", "/auth/verify")}


def annotation_calls(source: str):
    """Yield (name, arguments, start, end) for mapping annotations."""
    marker = re.compile(r"@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b")
    for match in marker.finditer(source):
        index = match.end()
        while index < len(source) and source[index] in " \t":
            index += 1
        if index >= len(source) or source[index] != "(":
            yield match.group(1), "", match.start(), index
            continue

        depth = 1
        arguments_start = index + 1
        index += 1
        in_string = False
        escaped = False
        while index < len(source) and depth:
            character = source[index]
            if in_string:
                if escaped:
                    escaped = False
                elif character == "\\":
                    escaped = True
                elif character == '"':
                    in_string = False
            elif character == '"':
                in_string = True
            elif character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            index += 1
        if depth:
            raise ValueError("unterminated mapping annotation")
        yield match.group(1), source[arguments_start:index - 1], match.start(), index


def annotation_paths(name: str, arguments: str) -> list[str]:
    named = re.search(r"\b(?:value|path)\s*=\s*(\{[^}]*\}|\"[^\"]*\")", arguments, re.DOTALL)
    if named:
        expression = named.group(1)
        return re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', expression)
    if not arguments.strip():
        return [""]
    first = re.match(r'\s*"([^"\\]*(?:\\.[^"\\]*)*)"', arguments, re.DOTALL)
    if first:
        return [first.group(1)]
    return [""]


def annotation_methods(name: str, arguments: str, class_level: bool) -> list[str]:
    if name != "RequestMapping":
        return [name.removesuffix("Mapping").upper()]
    methods = re.findall(r"RequestMethod\.([A-Z]+)", arguments)
    if methods:
        return methods
    return [] if class_level else list(HTTP_METHODS)


def normalize_path(base: str, child: str) -> str:
    if not base:
        path = child or "/"
    elif not child:
        path = base
    else:
        path = f"{base.rstrip('/')}/{child.lstrip('/')}"
    path = re.sub(r"/+$", "", path) or "/"
    path = re.sub(r"\{[^}]+\}", "*", path)
    return re.sub(r"/{2,}", "/", path)


def controller_routes(root: Path) -> tuple[list[tuple[str, str, str]], list[str]]:
    routes: list[tuple[str, str, str]] = []
    errors: list[str] = []
    for path in sorted(root.glob("data-platform-*/**/src/main/java/**/*Controller.java")):
        source = path.read_text(encoding="utf-8")
        try:
            mappings = list(annotation_calls(source))
        except ValueError as error:
            errors.append(f"{path}: {error}")
            continue
        class_match = re.search(r"\bclass\s+\w+", source)
        if not class_match:
            continue
        class_mapping = next((item for item in mappings if item[2] < class_match.start()), None)
        if not class_mapping or class_mapping[0] != "RequestMapping":
            errors.append(f"{path}: missing class-level @RequestMapping")
            continue
        bases = annotation_paths(class_mapping[0], class_mapping[1])
        if not bases:
            errors.append(f"{path}: class-level @RequestMapping has no path")
            continue
        for name, arguments, start, _ in mappings:
            if start <= class_match.start():
                continue
            methods = annotation_methods(name, arguments, class_level=False)
            paths = annotation_paths(name, arguments)
            if not methods:
                errors.append(f"{path}: {name} has no HTTP method")
                continue
            for base in bases:
                for child in paths:
                    for method in methods:
                        routes.append((method, normalize_path(base, child), str(path)))
    return routes, errors


def policy_routes(policy_path: Path) -> list[tuple[str, str]]:
    source = policy_path.read_text(encoding="utf-8")
    return re.findall(r"route(?:Any)?\(\s*\"([A-Z]+)\"\s*,\s*\"([^\"]+)\"", source)


def ant_matches(pattern: str, path: str) -> bool:
    pieces: list[str] = []
    index = 0
    while index < len(pattern):
        if pattern.startswith("**", index):
            pieces.append(".*")
            index += 2
        elif pattern[index] == "*":
            pieces.append("[^/]+")
            index += 1
        elif pattern[index] == "?":
            pieces.append("[^/]")
            index += 1
        else:
            pieces.append(re.escape(pattern[index]))
            index += 1
    return re.fullmatch("".join(pieces), path) is not None


def excluded(method: str, path: str) -> bool:
    return path.startswith(NON_USER_PREFIXES) or (method, path) in PUBLIC_AUTH_ROUTES


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    routes, parse_errors = controller_routes(root)
    policies = policy_routes(root / "data-platform-common-web/src/main/java/com/dataplatform/common/security/UserRoutePermissionPolicy.java")
    unmapped = [
        (method, path, source)
        for method, path, source in routes
        if not excluded(method, path)
        and not any(policy_method == method and ant_matches(pattern, path) for policy_method, pattern in policies)
    ]
    if parse_errors or unmapped:
        for error in parse_errors:
            print(f"ERROR {error}", file=sys.stderr)
        for method, path, source in unmapped:
            print(f"ERROR {method} {path} from {source} is not in UserRoutePermissionPolicy", file=sys.stderr)
        print(f"route policy verification failed: {len(parse_errors)} parser errors, {len(unmapped)} unmapped routes",
              file=sys.stderr)
        return 1
    print(f"route policy verification passed: {len(routes)} controller mappings, "
          f"{len(policies)} policy entries, {sum(excluded(method, path) for method, path, _ in routes)} excluded public/internal mappings")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
