package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.StageExecutionContext;
import java.time.Clock;
import java.time.Instant;
import java.util.function.BooleanSupplier;

record DefaultStageExecutionContext(Clock clock, Instant deadline,
                                    BooleanSupplier cancellation,
                                    PluginLogger logger,
                                    PluginMetricRecorder metrics)
        implements StageExecutionContext {

    @Override
    public boolean cancellationRequested() {
        return cancellation.getAsBoolean();
    }
}
