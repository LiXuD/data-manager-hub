package com.dataplatform.access.connector.runtime;

import com.dataplatform.plugin.spi.PluginMetricRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MicrometerPluginMetricRecorder implements PluginMetricRecorder {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "pluginId", "pluginVersion", "capability", "transportMode",
            "errorCategory", "instanceId");

    private final MeterRegistry registry;

    public MicrometerPluginMetricRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void increment(String metric, Map<String, String> lowCardinalityTags) {
        validateMetric(metric);
        registry.counter(metric, tags(lowCardinalityTags)).increment();
    }

    @Override
    public void recordDuration(String metric, Duration duration,
                               Map<String, String> lowCardinalityTags) {
        validateMetric(metric);
        registry.timer(metric, tags(lowCardinalityTags)).record(duration);
    }

    private Iterable<Tag> tags(Map<String, String> values) {
        List<Tag> tags = new ArrayList<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (ALLOWED_TAGS.contains(key) && value != null) {
                    tags.add(Tag.of(key, value.length() <= 128 ? value : value.substring(0, 128)));
                }
            });
        }
        return tags;
    }

    private void validateMetric(String metric) {
        if (metric == null || !metric.matches("connector_[a-zA-Z0-9_:]{1,120}")) {
            throw new IllegalArgumentException("Plugin metric name is outside the connector namespace");
        }
    }
}
