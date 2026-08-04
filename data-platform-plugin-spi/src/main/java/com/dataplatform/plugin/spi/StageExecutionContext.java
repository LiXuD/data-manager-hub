package com.dataplatform.plugin.spi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public interface StageExecutionContext {

    Clock clock();

    Instant deadline();

    boolean cancellationRequested();

    default Duration remainingTime() {
        Duration remaining = Duration.between(clock().instant(), deadline());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    PluginLogger logger();

    PluginMetricRecorder metrics();
}
