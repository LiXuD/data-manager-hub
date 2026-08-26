#!/usr/bin/env python3
"""Deterministic loopback fixture for single HTTP, token+business, and polling flows."""

from __future__ import annotations

import argparse
import json
import ssl
import threading
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import urlparse

MAX_BODY_BYTES = 1024 * 1024


@dataclass
class FixtureState:
    lock: threading.Lock = field(default_factory=threading.Lock)
    next_token: int = 1
    next_job: int = 1
    tokens: set[str] = field(default_factory=set)
    jobs: dict[str, dict[str, Any]] = field(default_factory=dict)
    counters: dict[str, int] = field(default_factory=lambda: {
        "singleHttpRequests": 0,
        "tokenRequests": 0,
        "businessRequests": 0,
        "asyncSubmissions": 0,
        "asyncPolls": 0,
    })

    def issue_token(self) -> str:
        with self.lock:
            token = f"fixture-token-{self.next_token}"
            self.next_token += 1
            self.tokens.add(token)
            self.counters["tokenRequests"] += 1
            return token

    def accepts_token(self, token: str) -> bool:
        with self.lock:
            return token in self.tokens

    def submit_job(self, payload: dict[str, Any]) -> str:
        with self.lock:
            job_id = f"fixture-job-{self.next_job}"
            self.next_job += 1
            self.jobs[job_id] = {"payload": payload, "polls": 0}
            self.counters["asyncSubmissions"] += 1
            return job_id

    def poll_job(self, job_id: str) -> dict[str, Any] | None:
        with self.lock:
            job = self.jobs.get(job_id)
            if job is None:
                return None
            job["polls"] += 1
            self.counters["asyncPolls"] += 1
            if job["polls"] < 2:
                return {"jobId": job_id, "status": "PENDING"}
            return {
                "jobId": job_id,
                "status": "SUCCEEDED",
                "data": {"accepted": True, "received": job["payload"]},
            }

    def increment(self, counter: str) -> None:
        with self.lock:
            self.counters[counter] += 1

    def snapshot(self) -> dict[str, int]:
        with self.lock:
            return dict(self.counters)


class VendorFlowFixtureServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address: tuple[str, int], state: FixtureState | None = None):
        super().__init__(address, VendorFlowFixtureHandler)
        self.fixture_state = state or FixtureState()


class VendorFlowFixtureHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server: VendorFlowFixtureServer

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path == "/health":
            self._json(200, {"status": "UP", "fixture": "connector-product-model"})
            return
        if path == "/state":
            self._json(200, self.server.fixture_state.snapshot())
            return
        prefix = "/vendor/async/jobs/"
        if path.startswith(prefix):
            job_id = path[len(prefix):]
            result = self.server.fixture_state.poll_job(job_id)
            if result is None:
                self._json(404, {"error": "job_not_found"})
            else:
                self._json(200, result)
            return
        self._json(404, {"error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        try:
            payload = self._read_json()
        except FixtureRequestError as error:
            self._json(error.status, {"error": error.code})
            return
        path = urlparse(self.path).path
        if path == "/vendor/single-http":
            self.server.fixture_state.increment("singleHttpRequests")
            self._json(200, {
                "success": True,
                "flow": "single-http",
                "received": payload,
            })
            return
        if path == "/vendor/token":
            if not self._has_text(payload, "clientId") or not self._has_text(payload, "clientSecret"):
                self._json(400, {"error": "credentials_required"})
                return
            token = self.server.fixture_state.issue_token()
            self._json(200, {"accessToken": token, "tokenType": "Bearer", "expiresIn": 300})
            return
        if path == "/vendor/business":
            token = self.headers.get("Authorization", "").removeprefix("Bearer ")
            if not token or not self.server.fixture_state.accepts_token(token):
                self._json(401, {"error": "invalid_token"})
                return
            self.server.fixture_state.increment("businessRequests")
            self._json(200, {
                "success": True,
                "flow": "token-business",
                "received": payload,
            })
            return
        if path == "/vendor/async/submit":
            job_id = self.server.fixture_state.submit_job(payload)
            self._json(202, {
                "jobId": job_id,
                "status": "PENDING",
                "pollPath": f"/vendor/async/jobs/{job_id}",
            })
            return
        self._json(404, {"error": "not_found"})

    def log_message(self, _message: str, *_args: object) -> None:
        return

    def _read_json(self) -> dict[str, Any]:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError as error:
            raise FixtureRequestError(400, "invalid_content_length") from error
        if length < 0 or length > MAX_BODY_BYTES:
            raise FixtureRequestError(413, "payload_too_large")
        raw = self.rfile.read(length)
        try:
            value = json.loads(raw or b"{}")
        except (json.JSONDecodeError, UnicodeDecodeError) as error:
            raise FixtureRequestError(400, "invalid_json") from error
        if not isinstance(value, dict):
            raise FixtureRequestError(400, "json_object_required")
        return value

    def _json(self, status: int, body: object) -> None:
        encoded = json.dumps(body, separators=(",", ":"), sort_keys=True).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    @staticmethod
    def _has_text(payload: dict[str, Any], field: str) -> bool:
        value = payload.get(field)
        return isinstance(value, str) and bool(value.strip())


class FixtureRequestError(Exception):
    def __init__(self, status: int, code: str):
        super().__init__(code)
        self.status = status
        self.code = code


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--certificate")
    parser.add_argument("--private-key")
    args = parser.parse_args()
    if bool(args.certificate) != bool(args.private_key):
        raise SystemExit("--certificate and --private-key must be supplied together")

    server = VendorFlowFixtureServer((args.host, args.port))
    if args.certificate:
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(args.certificate, args.private_key)
        server.socket = tls.wrap_socket(server.socket, server_side=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
