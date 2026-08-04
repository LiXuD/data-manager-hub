package com.dataplatform.plugin.spi;

import java.time.Clock;

public interface PluginContext {

    ManagedHttpTransport managedHttpTransport();

    SecretResolver secretResolver();

    Clock clock();

    PluginLogger logger();

    PluginMetricRecorder metrics();

    ObjectCodec objectCodec();

    ManagedTaskExecutor taskExecutor();
}
