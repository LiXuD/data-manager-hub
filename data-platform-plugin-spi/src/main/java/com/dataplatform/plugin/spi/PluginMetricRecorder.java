package com.dataplatform.plugin.spi;

import java.time.Duration;
import java.util.Map;

public interface PluginMetricRecorder {

    void increment(String metric, Map<String, String> lowCardinalityTags);

    void recordDuration(String metric, Duration duration, Map<String, String> lowCardinalityTags);
}
