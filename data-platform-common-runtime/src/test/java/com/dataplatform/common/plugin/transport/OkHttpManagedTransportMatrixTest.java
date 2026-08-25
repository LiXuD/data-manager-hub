package com.dataplatform.common.plugin.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageExecutionContext;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class OkHttpManagedTransportMatrixTest {

    @Test
    void sendsBodiesForEverySupportedMutationMethod() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            for (int index = 0; index < 4; index++) {
                server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
            }
            OkHttpManagedTransport transport = transport();

            for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
                byte[] body = ("{\"method\":\"" + method + "\"}").getBytes(StandardCharsets.UTF_8);
                var response = transport.execute(request(server, method, body,
                        Duration.ofSeconds(1), Duration.ofSeconds(2)), context());
                var recorded = server.takeRequest();

                assertEquals(200, response.statusCode());
                assertEquals(method, recorded.getMethod());
                assertEquals("application/json", recorded.getHeader("Content-Type"));
                assertArrayEquals(body, recorded.getBody().readByteArray());
            }
        }
    }

    @Test
    void readTimeoutIsClassifiedWithoutLeakingResponseAndMarksMaybeSent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("delayed")
                    .setBodyDelay(400, TimeUnit.MILLISECONDS));
            OkHttpManagedTransport transport = transport();

            ConnectorException error = assertThrows(ConnectorException.class,
                    () -> transport.execute(request(server, "GET", new byte[0],
                            Duration.ofMillis(50), Duration.ofSeconds(1)), context()));

            assertEquals(ErrorCategory.TRANSPORT_TIMEOUT, error.category());
            assertEquals("TRANSPORT_TIMEOUT", error.errorCode());
            assertEquals("Vendor request timed out", error.safeMessage());
            assertEquals(RequestDeliveryState.MAYBE_SENT, error.deliveryState());
        }
    }

    @Test
    void connectTimeoutUsesRequestDeadlineAndIsKnownNotSent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            NetworkPolicy policy = new NetworkPolicy(Set.of("http"), Set.of("localhost"), true,
                    Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(3), 4096);
            OkHttpManagedTransport transport = new OkHttpManagedTransport(
                    new OkHttpClient.Builder().socketFactory(new TimingOutSocketFactory()).build(), policy);
            ConnectorRequest request = new ConnectorRequest("GET", server.url("/connect-timeout").uri(),
                    Map.of(), Map.of(), "application/json", new byte[0], Duration.ofMillis(40),
                    Duration.ofSeconds(1), Duration.ofSeconds(2), IdempotencyPolicy.IDEMPOTENT,
                    null, 4096);

            ConnectorException error = assertThrows(ConnectorException.class,
                    () -> transport.execute(request, context()));

            assertEquals(ErrorCategory.TRANSPORT_TIMEOUT, error.category());
            assertEquals(RequestDeliveryState.NOT_SENT, error.deliveryState());
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test
    void totalCallTimeoutIsNotMisclassifiedAsConnectionFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("slow-total")
                    .setBodyDelay(400, TimeUnit.MILLISECONDS));
            OkHttpManagedTransport transport = transport();

            ConnectorException error = assertThrows(ConnectorException.class,
                    () -> transport.execute(request(server, "GET", new byte[0],
                            Duration.ofSeconds(1), Duration.ofMillis(60)), context()));

            assertEquals(ErrorCategory.TRANSPORT_TIMEOUT, error.category());
            assertEquals(RequestDeliveryState.MAYBE_SENT, error.deliveryState());
        }
    }

    @Test
    void returnsEmptyNonJsonAndHttpErrorBodiesWithoutFollowingRedirects() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("plain-text"));
            server.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/target")));
            OkHttpManagedTransport transport = transport();

            assertEquals(0, transport.execute(request(server, "GET", new byte[0],
                    Duration.ofSeconds(1), Duration.ofSeconds(2)), context()).body().length);
            assertEquals("plain-text", new String(transport.execute(request(server, "GET", new byte[0],
                    Duration.ofSeconds(1), Duration.ofSeconds(2)), context()).body(), StandardCharsets.UTF_8));
            assertEquals(503, transport.execute(request(server, "GET", new byte[0],
                    Duration.ofSeconds(1), Duration.ofSeconds(2)), context()).statusCode());
            assertEquals(302, transport.execute(request(server, "GET", new byte[0],
                    Duration.ofSeconds(1), Duration.ofSeconds(2)), context()).statusCode());
            assertEquals(4, server.getRequestCount());
        }
    }

    private OkHttpManagedTransport transport() {
        NetworkPolicy policy = new NetworkPolicy(Set.of("http"), Set.of("localhost"), true,
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(3), 4096);
        return new OkHttpManagedTransport(new OkHttpClient(), policy);
    }

    private ConnectorRequest request(
            MockWebServer server, String method, byte[] body, Duration readTimeout, Duration totalTimeout) {
        return new ConnectorRequest(method, server.url("/matrix").uri(), Map.of(), Map.of(),
                "application/json", body, Duration.ofSeconds(1), readTimeout, totalTimeout,
                IdempotencyPolicy.NON_IDEMPOTENT, null, 4096);
    }

    private StageExecutionContext context() {
        var pluginContext = TestPluginContexts.context();
        return new StageExecutionContext() {
            @Override public Clock clock() { return Clock.systemUTC(); }
            @Override public Instant deadline() { return Instant.now().plusSeconds(5); }
            @Override public boolean cancellationRequested() { return false; }
            @Override public PluginLogger logger() { return pluginContext.logger(); }
            @Override public PluginMetricRecorder metrics() { return pluginContext.metrics(); }
        };
    }

    private static final class TimingOutSocketFactory extends SocketFactory {
        @Override public Socket createSocket() { return timingOutSocket(); }
        @Override public Socket createSocket(String host, int port) { return timingOutSocket(); }
        @Override public Socket createSocket(String host, int port,
                                             java.net.InetAddress localHost, int localPort) {
            return timingOutSocket();
        }
        @Override public Socket createSocket(java.net.InetAddress host, int port) { return timingOutSocket(); }
        @Override public Socket createSocket(java.net.InetAddress address, int port,
                                             java.net.InetAddress localAddress, int localPort) {
            return timingOutSocket();
        }

        private Socket timingOutSocket() {
            return new Socket() {
                @Override
                public void connect(SocketAddress endpoint, int timeout) throws IOException {
                    try {
                        Thread.sleep(Math.max(1, timeout) + 20L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new java.io.InterruptedIOException("connect interrupted");
                    }
                    throw new SocketTimeoutException("connect timed out");
                }
            };
        }
    }
}
