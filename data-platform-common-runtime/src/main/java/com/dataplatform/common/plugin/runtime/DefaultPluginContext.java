package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.ManagedHttpTransport;
import com.dataplatform.plugin.spi.ManagedTaskExecutor;
import com.dataplatform.plugin.spi.ObjectCodec;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.SecretResolver;
import java.time.Clock;
import java.util.Objects;

public record DefaultPluginContext(
        ManagedHttpTransport managedHttpTransport,
        SecretResolver secretResolver,
        Clock clock,
        PluginLogger logger,
        PluginMetricRecorder metrics,
        ObjectCodec objectCodec,
        ManagedTaskExecutor taskExecutor) implements PluginContext {

    public DefaultPluginContext {
        Objects.requireNonNull(managedHttpTransport, "managedHttpTransport");
        Objects.requireNonNull(secretResolver, "secretResolver");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(objectCodec, "objectCodec");
        Objects.requireNonNull(taskExecutor, "taskExecutor");
    }
}
