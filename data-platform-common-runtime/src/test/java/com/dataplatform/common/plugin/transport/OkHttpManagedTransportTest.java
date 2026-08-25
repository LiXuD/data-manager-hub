package com.dataplatform.common.plugin.transport;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.StageExecutionContext;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OkHttpManagedTransportTest {

    @Test
    void performsRealHttpCallWithQueryHeadersAndResponseLimit() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
            NetworkPolicy policy = new NetworkPolicy(Set.of("http"), Set.of("localhost"), true,
                    Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(3), 1024);
            OkHttpManagedTransport transport = new OkHttpManagedTransport(new OkHttpClient(), policy);
            ConnectorRequest request = new ConnectorRequest("GET", server.url("/query").uri(),
                    Map.of("X-Test", List.of("yes")), Map.of("name", List.of("alice")),
                    "application/json", new byte[0], Duration.ofSeconds(1), Duration.ofSeconds(1),
                    Duration.ofSeconds(2), IdempotencyPolicy.IDEMPOTENT, null, 1024);

            var response = transport.execute(request, context());
            var recorded = server.takeRequest();
            assertEquals(200, response.statusCode());
            assertEquals("alice", recorded.getRequestUrl().queryParameter("name"));
            assertEquals("yes", recorded.getHeader("X-Test"));

            server.enqueue(new MockResponse().setBody("x".repeat(2048)));
            ConnectorException tooLarge = assertThrows(ConnectorException.class,
                    () -> transport.execute(request, context()));
            assertEquals(ErrorCategory.TRANSPORT_HTTP_ERROR, tooLarge.category());
        }
    }

    @Test
    void rejectsEndpointOutsideAllowlistBeforeSending() {
        NetworkPolicy policy = new NetworkPolicy(Set.of("https"), Set.of("api.example.com"), false,
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(3), 1024);
        OkHttpManagedTransport transport = new OkHttpManagedTransport(new OkHttpClient(), policy);
        ConnectorRequest request = new ConnectorRequest("GET", URI.create("https://evil.example/query"),
                Map.of(), Map.of(), "application/json", new byte[0], Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(2), IdempotencyPolicy.IDEMPOTENT, null, 1024);
        ConnectorException error = assertThrows(ConnectorException.class,
                () -> transport.execute(request, context()));
        assertEquals(com.dataplatform.plugin.spi.RequestDeliveryState.NOT_SENT, error.deliveryState());
        assertEquals(ErrorCategory.CONFIGURATION_ERROR, error.category());
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
}
