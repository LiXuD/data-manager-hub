package com.dataplatform.plugin.spi;

import java.time.Duration;

public record StageTiming(String stageKey, StageCapability capability, String pluginId,
                          String pluginVersion, Duration duration, boolean successful) {
}
