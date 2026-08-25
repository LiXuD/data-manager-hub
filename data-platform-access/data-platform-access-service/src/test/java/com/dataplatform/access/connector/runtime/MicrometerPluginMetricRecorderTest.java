package com.dataplatform.access.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MicrometerPluginMetricRecorderTest {

    @Test
    void acceptsOnlyConnectorMetricsAndLowCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPluginMetricRecorder recorder = new MicrometerPluginMetricRecorder(registry);

        recorder.increment("connector_plugin_events", Map.of(
                "pluginId", "demo", "requestId", "request-123"));

        assertEquals(1.0, registry.get("connector_plugin_events").tag("pluginId", "demo")
                .counter().count());
        assertNull(registry.find("connector_plugin_events").tag("requestId", "request-123").counter());
        assertThrows(IllegalArgumentException.class,
                () -> recorder.increment("arbitrary_plugin_metric", Map.of()));
    }

    @Test
    void acceptsManagedTransportCounterNamesAndTheirCompleteTagSet() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPluginMetricRecorder recorder = new MicrometerPluginMetricRecorder(registry);
        Map<String, String> tags = Map.of(
                "pluginId", "token-business",
                "pluginVersion", "1.0.0",
                "transportMode", "HOST_MANAGED_MULTI_HTTP",
                "errorCategory", "TRANSPORT_TIMEOUT");

        recorder.increment("connector_managed_transport_subrequests_total", tags);
        recorder.increment("connector_managed_transport_sessions_total", tags);

        assertEquals(1.0, registry.get("connector_managed_transport_subrequests_total")
                .tags("pluginId", "token-business", "pluginVersion", "1.0.0",
                        "transportMode", "HOST_MANAGED_MULTI_HTTP",
                        "errorCategory", "TRANSPORT_TIMEOUT")
                .counter().count());
        assertEquals(1.0, registry.get("connector_managed_transport_sessions_total")
                .tags("pluginId", "token-business", "pluginVersion", "1.0.0",
                        "transportMode", "HOST_MANAGED_MULTI_HTTP",
                        "errorCategory", "TRANSPORT_TIMEOUT")
                .counter().count());
    }
}
