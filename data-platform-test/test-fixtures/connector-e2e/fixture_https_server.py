#!/usr/bin/env python3
"""Small TLS-only artifact repository plus deterministic vendor echo endpoint."""

import argparse
import json
import os
import pathlib
import socket
import ssl
import sys
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse


FAILOVER_LOCK = threading.Lock()
FAILOVER_TRIGGERED = False
CONNECTION_FAILURE_TRIGGERED = False
FLOW_LOCK = threading.Lock()
NEXT_TOKEN = 1
NEXT_JOB = 1
TOKENS: set[str] = set()
JOBS: dict[str, dict[str, object]] = {}
FLOW_COUNTERS = {
    "singleHttpRequests": 0,
    "vendorRequests": 0,
    "vendorEchoRequests": 0,
    "vendorFallbackRequests": 0,
    "tokenRequests": 0,
    "businessRequests": 0,
    "asyncSubmissions": 0,
    "asyncPolls": 0,
}


def increment(counter: str) -> None:
    with FLOW_LOCK:
        FLOW_COUNTERS[counter] += 1


def record_vendor_request(path: str) -> None:
    with FLOW_LOCK:
        FLOW_COUNTERS["vendorRequests"] += 1
        counter = {
            "/vendor/echo": "vendorEchoRequests",
            "/vendor/fallback": "vendorFallbackRequests",
        }.get(path)
        if counter is not None:
            FLOW_COUNTERS[counter] += 1


def issue_token() -> str:
    global NEXT_TOKEN
    with FLOW_LOCK:
        token = f"fixture-token-{NEXT_TOKEN}"
        NEXT_TOKEN += 1
        TOKENS.add(token)
        FLOW_COUNTERS["tokenRequests"] += 1
        return token


def submit_job(payload: object) -> str:
    global NEXT_JOB
    with FLOW_LOCK:
        job_id = f"fixture-job-{NEXT_JOB}"
        NEXT_JOB += 1
        JOBS[job_id] = {"payload": payload, "polls": 0}
        FLOW_COUNTERS["asyncSubmissions"] += 1
        return job_id


def poll_job(job_id: str) -> dict[str, object] | None:
    with FLOW_LOCK:
        job = JOBS.get(job_id)
        if job is None:
            return None
        job["polls"] = int(job["polls"]) + 1
        FLOW_COUNTERS["asyncPolls"] += 1
        if job["polls"] < 2:
            return {"jobId": job_id, "status": "PENDING"}
        return {
            "jobId": job_id,
            "status": "SUCCEEDED",
            "success": True,
            "flow": "async-polling",
            "received": job["payload"],
        }


def flow_state() -> dict[str, int]:
    with FLOW_LOCK:
        return dict(FLOW_COUNTERS)


class FixtureHandler(SimpleHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path == "/health":
            self._json(200, {"status": "UP", "fixture": "connector-e2e"})
            return
        if path == "/state":
            self._json(200, flow_state())
            return
        async_prefix = "/vendor/async/jobs/"
        if path.startswith(async_prefix):
            result = poll_job(path[len(async_prefix):])
            self._json(200, result) if result is not None else self._json(
                404, {"error": "job_not_found"})
            return
        super().do_GET()

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path in {"/vendor/token", "/vendor/business", "/vendor/async/submit"}:
            self._handle_managed_flow(path)
            return
        if path not in {"/vendor/echo", "/vendor/primary", "/vendor/fallback"}:
            self._json(404, {"error": "not_found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(min(length, 1024 * 1024))
        try:
            received = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._json(400, {"error": "invalid_json"})
            return
        probe = received.get("probe") if isinstance(received, dict) else None
        record_vendor_request(path)
        global CONNECTION_FAILURE_TRIGGERED, FAILOVER_TRIGGERED
        with FAILOVER_LOCK:
            failover = probe == "failover" and not FAILOVER_TRIGGERED
            if failover:
                FAILOVER_TRIGGERED = True
            connection_error = probe == "connection-error" and not CONNECTION_FAILURE_TRIGGERED
            if connection_error:
                CONNECTION_FAILURE_TRIGGERED = True
        if connection_error:
            self.close_connection = True
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            self.connection.close()
            return
        if failover:
            self._json(503, {
                "success": False,
                "errorCode": "FIXTURE_TRANSIENT_HTTP_ERROR",
                "errorMessage": "Fixture vendor returned a transient HTTP error",
            })
            return
        if probe == "http-error" and path == "/vendor/echo":
            self._json(503, {
                "success": False,
                "errorCode": "FIXTURE_HTTP_ERROR",
                "errorMessage": "Fixture vendor returned an HTTP error",
            })
            return
        if probe == "malformed":
            self._json(200, ["fixture", "response", "is", "not", "an", "object"])
            return
        if probe == "reject":
            self._json(200, {
                "success": False,
                "errorCode": "FIXTURE_VENDOR_REJECTED",
                "errorMessage": "Fixture vendor rejected the request",
                "received": received,
            })
            return
        increment("singleHttpRequests")
        self._json(200, {
            "success": True,
            "fixture": "e2e-signed-connector",
            "received": received,
        })

    def _handle_managed_flow(self, path: str) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(min(length, 1024 * 1024))
        try:
            received = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._json(400, {"error": "invalid_json"})
            return
        if not isinstance(received, dict):
            self._json(400, {"error": "json_object_required"})
            return
        if path == "/vendor/token":
            if not received.get("clientId") or not received.get("clientSecret"):
                self._json(400, {"error": "credentials_required"})
                return
            self._json(200, {
                "accessToken": issue_token(),
                "tokenType": "Bearer",
                "expiresIn": 300,
            })
            return
        if path == "/vendor/business":
            token = self.headers.get("Authorization", "").removeprefix("Bearer ")
            with FLOW_LOCK:
                valid = token in TOKENS
            if not valid:
                self._json(401, {"error": "invalid_token"})
                return
            increment("businessRequests")
            self._json(200, {
                "success": True,
                "fixture": "e2e-signed-connector",
                "flow": "token-business",
                "received": received,
            })
            return
        job_id = submit_job(received)
        self._json(202, {
            "jobId": job_id,
            "status": "PENDING",
            "pollPath": f"/vendor/async/jobs/{job_id}",
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
