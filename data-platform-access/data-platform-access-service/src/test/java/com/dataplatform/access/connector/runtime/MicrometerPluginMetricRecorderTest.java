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
}
