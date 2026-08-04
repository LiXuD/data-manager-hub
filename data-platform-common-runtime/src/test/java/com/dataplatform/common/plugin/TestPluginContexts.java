package com.dataplatform.common.plugin;

import com.dataplatform.common.plugin.runtime.DefaultManagedTaskExecutor;
import com.dataplatform.common.plugin.runtime.DefaultPluginContext;
import com.dataplatform.common.plugin.runtime.JacksonObjectCodec;
import com.dataplatform.common.plugin.runtime.NoOpPluginMetricRecorder;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ManagedHttpTransport;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;

public final class TestPluginContexts {
    private TestPluginContexts() { }

    public static PluginContext context(ManagedHttpTransport transport) {
        PluginLogger logger = new PluginLogger() {
            @Override public void debug(String event, Map<String, ?> safeFields) { }
            @Override public void info(String event, Map<String, ?> safeFields) { }
            @Override public void warn(String event, Map<String, ?> safeFields) { }
            @Override public void error(String event, Map<String, ?> safeFields) { }
        };
        return new DefaultPluginContext(transport, ref -> new SecretValue("test-secret".toCharArray()),
                Clock.systemUTC(), logger, new NoOpPluginMetricRecorder(),
                new JacksonObjectCodec(new ObjectMapper()), new DefaultManagedTaskExecutor(Runnable::run));
    }

    public static PluginContext context() {
        return context((request, execution) -> { throw new AssertionError("transport should not execute"); });
    }
}
