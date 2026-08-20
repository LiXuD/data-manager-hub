package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HostManagedTransportSessionTest {

    @Test
    void recordsEverySubrequestButOnlyFinalSessionOutcomeWithAllowedTags() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-12T08:00:00Z"), ZoneOffset.UTC);
        RecordingMetrics metrics = new RecordingMetrics();
        AtomicInteger calls = new AtomicInteger();
        var stageContext = new DefaultStageExecutionContext(
                clock, clock.instant().plusSeconds(30), () -> false, noOpLogger(), metrics);
        var session = new HostManagedTransportSession((request, context) -> {
            if (calls.incrementAndGet() == 1) {
                return response(request.url());
            }
            throw new ConnectorException(ErrorCategory.TRANSPORT_CONNECTION_ERROR,
                    "SECOND_CALL_FAILED", "Second call failed", RequestDeliveryState.NOT_SENT);
        }, stageContext, new HostDeadline(clock, clock.instant().plusSeconds(30)),
                new HostCancellationToken(() -> false), metrics, Map.of(
                "pluginId", "token-business",
                "pluginVersion", "1.0.0",
                "transportMode", "HOST_MANAGED_MULTI_HTTP"));

        session.execute(request());
        ConnectorException failure = assertThrows(ConnectorException.class,
                () -> session.execute(request()));

        assertEquals(RequestDeliveryState.SENT, failure.deliveryState());
        assertEquals(2, metrics.count("connector_managed_transport_subrequests_total"));
        assertEquals(0, metrics.count("connector_managed_transport_sessions_total"));

        session.finish(failure.category());
        session.finish(null);

        assertEquals(1, metrics.count("connector_managed_transport_sessions_total"));
        Map<String, String> finalTags = metrics.lastTags(
                "connector_managed_transport_sessions_total");
        assertEquals("TRANSPORT_CONNECTION_ERROR", finalTags.get("errorCategory"));
        assertEquals("HOST_MANAGED_MULTI_HTTP", finalTags.get("transportMode"));
        assertFalse(finalTags.containsKey("outcome"));
        assertFalse(finalTags.containsKey("endpoint"));
    }

    private ConnectorRequest request() {
        return new ConnectorRequest("GET", URI.create("https://vendor.example/resource"),
                Map.of(), Map.of(), "application/json", new byte[0], Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ofSeconds(3), IdempotencyPolicy.IDEMPOTENT,
                null, 1024);
    }

    private ConnectorRawResponse response(URI endpoint) {
        return new ConnectorRawResponse(200, Map.of(), "{}".getBytes(), Duration.ofMillis(5),
                endpoint, 0, 2);
    }

    private PluginLogger noOpLogger() {
        return new PluginLogger() {
            @Override public void debug(String event, Map<String, ?> safeFields) { }
            @Override public void info(String event, Map<String, ?> safeFields) { }
            @Override public void warn(String event, Map<String, ?> safeFields) { }
            @Override public void error(String event, Map<String, ?> safeFields) { }
        };
    }

    private static final class RecordingMetrics implements PluginMetricRecorder {
        private final Map<String, List<Map<String, String>>> counters = new java.util.HashMap<>();

        @Override
        public void increment(String metric, Map<String, String> tags) {
            counters.computeIfAbsent(metric, ignored -> new ArrayList<>()).add(Map.copyOf(tags));
        }

        @Override
        public void recordDuration(String metric, Duration duration, Map<String, String> tags) { }

        int count(String metric) {
            return counters.getOrDefault(metric, List.of()).size();
        }

        Map<String, String> lastTags(String metric) {
            List<Map<String, String>> values = counters.getOrDefault(metric, List.of());
            return values.get(values.size() - 1);
        }
    }
}
