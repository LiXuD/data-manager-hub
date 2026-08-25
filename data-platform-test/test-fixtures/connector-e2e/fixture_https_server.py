#!/usr/bin/env python3
"""Small TLS-only artifact repository plus deterministic vendor echo endpoint."""

import argparse
import json
import os
import pathlib
import ssl
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


class FixtureHandler(SimpleHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            self._json(200, {"status": "UP", "fixture": "connector-e2e"})
            return
        super().do_GET()

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/vendor/echo":
            self._json(404, {"error": "not_found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(min(length, 1024 * 1024))
        try:
            received = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._json(400, {"error": "invalid_json"})
            return
        self._json(200, {
            "success": True,
            "fixture": "e2e-signed-connector",
            "received": received,
        })

    def log_message(self, message: str, *args: object) -> None:
        print("fixture-https:", message % args, flush=True)

    def _json(self, status: int, body: object) -> None:
        encoded = json.dumps(body, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, type=pathlib.Path)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--certificate", required=True)
    parser.add_argument("--private-key", required=True)
    parser.add_argument("--daemonize", action="store_true")
    parser.add_argument("--pid-file", type=pathlib.Path)
    parser.add_argument("--log-file", type=pathlib.Path)
    args = parser.parse_args()

    if args.daemonize:
        if args.pid_file is None or args.log_file is None:
            raise SystemExit("--daemonize requires --pid-file and --log-file")
        if detach(args.pid_file, args.log_file):
            return

    handler = lambda *values, **kwargs: FixtureHandler(  # noqa: E731
        *values, directory=str(args.root), **kwargs)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), handler)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.load_cert_chain(args.certificate, args.private_key)
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    server.serve_forever()


def detach(pid_file: pathlib.Path, log_file: pathlib.Path) -> bool:
    """Double-fork so the fixture survives the preparing shell without sharing its process group."""
    first_child = os.fork()
    if first_child > 0:
        os.waitpid(first_child, 0)
        return True
    os.setsid()
    second_child = os.fork()
    if second_child > 0:
        os._exit(0)

    devnull = os.open(os.devnull, os.O_RDONLY)
    log_fd = os.open(log_file, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
    os.dup2(devnull, sys.stdin.fileno())
    os.dup2(log_fd, sys.stdout.fileno())
    os.dup2(log_fd, sys.stderr.fileno())
    os.close(devnull)
    os.close(log_fd)
    pid_file.write_text(str(os.getpid()) + "\n", encoding="ascii")
    return False


if __name__ == "__main__":
    main()
