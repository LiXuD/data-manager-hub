package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.PluginMetricRecorder;
import java.time.Duration;
import java.util.Map;

public final class NoOpPluginMetricRecorder implements PluginMetricRecorder {
    @Override public void increment(String metric, Map<String, String> lowCardinalityTags) { }
    @Override public void recordDuration(String metric, Duration duration,
                                         Map<String, String> lowCardinalityTags) { }
}
