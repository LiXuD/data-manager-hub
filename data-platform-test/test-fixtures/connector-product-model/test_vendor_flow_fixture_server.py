from __future__ import annotations

import http.client
import json
import threading
import unittest

from vendor_flow_fixture_server import VendorFlowFixtureServer


class VendorFlowFixtureServerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.server = VendorFlowFixtureServer(("127.0.0.1", 0))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.port = self.server.server_address[1]

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)

    def test_single_http_echo_and_counter(self) -> None:
        status, body = self.request("POST", "/vendor/single-http", {"company": "fixture"})
        self.assertEqual(200, status)
        self.assertEqual("single-http", body["flow"])
        self.assertEqual({"company": "fixture"}, body["received"])

        status, counters = self.request("GET", "/state")
        self.assertEqual(200, status)
        self.assertEqual(1, counters["singleHttpRequests"])

    def test_token_then_business_requires_issued_bearer_token(self) -> None:
        status, body = self.request("POST", "/vendor/business", {"query": "fixture"})
        self.assertEqual(401, status)
        self.assertEqual("invalid_token", body["error"])

        status, token_body = self.request("POST", "/vendor/token", {
            "clientId": "fixture-client",
            "clientSecret": "fixture-secret",
        })
        self.assertEqual(200, status)
        self.assertNotIn("fixture-secret", json.dumps(token_body))
        status, business = self.request(
            "POST",
            "/vendor/business",
            {"query": "fixture"},
            {"Authorization": f"Bearer {token_body['accessToken']}"},
        )
        self.assertEqual(200, status)
        self.assertEqual("token-business", business["flow"])

    def test_async_submit_reaches_terminal_state_on_second_poll(self) -> None:
        status, submitted = self.request("POST", "/vendor/async/submit", {"query": "fixture"})
        self.assertEqual(202, status)
        self.assertEqual("PENDING", submitted["status"])

        status, first = self.request("GET", submitted["pollPath"])
        self.assertEqual(200, status)
        self.assertEqual("PENDING", first["status"])
        status, second = self.request("GET", submitted["pollPath"])
        self.assertEqual(200, status)
        self.assertEqual("SUCCEEDED", second["status"])
        self.assertEqual({"query": "fixture"}, second["data"]["received"])

    def request(
        self,
        method: str,
        path: str,
        body: dict[str, object] | None = None,
        headers: dict[str, str] | None = None,
    ) -> tuple[int, dict[str, object]]:
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        encoded = None if body is None else json.dumps(body).encode("utf-8")
        request_headers = dict(headers or {})
        if encoded is not None:
            request_headers["Content-Type"] = "application/json"
        connection.request(method, path, body=encoded, headers=request_headers)
        response = connection.getresponse()
        decoded = json.loads(response.read().decode("utf-8"))
        connection.close()
        return response.status, decoded


if __name__ == "__main__":
    unittest.main()
