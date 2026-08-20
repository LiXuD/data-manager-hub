package com.dataplatform.plugin.testkit;

import com.dataplatform.common.plugin.runtime.CompiledConnectorPipeline;
import com.dataplatform.common.plugin.runtime.ConnectorExecutionRequest;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutor;
import com.dataplatform.common.plugin.runtime.ConnectorPluginRegistry;
import com.dataplatform.common.plugin.runtime.DefaultPluginContext;
import com.dataplatform.common.plugin.runtime.DefaultPluginValidationContext;
import com.dataplatform.common.plugin.runtime.JacksonObjectCodec;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.common.plugin.runtime.PluginHandle;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ManagedHttpTransport;
import com.dataplatform.plugin.spi.ManagedTaskExecutor;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.SecretValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;

/** Executes a connector plugin through the production compiler/executor and a managed fake transport. */
public final class ConnectorPluginTestKit implements AutoCloseable {

    private final ObjectMapper mapper;
    private final ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
    private final PipelineCompiler compiler;
    private final ConnectorPipelineExecutor executor;
    private final PluginContext context;

    public ConnectorPluginTestKit(ManagedHttpTransport transport, Clock clock) {
        this.mapper = new ObjectMapper();
        PluginLogger logger = new NoOpLogger();
        PluginMetricRecorder metrics = new NoOpMetrics();
        ManagedTaskExecutor tasks = new ManagedTaskExecutor() {
            @Override
            public <T> java.util.concurrent.CompletionStage<T> submit(
                    java.util.concurrent.Callable<T> task) {
                java.util.concurrent.CompletableFuture<T> result = new java.util.concurrent.CompletableFuture<>();
                try { result.complete(task.call()); }
                catch (Exception exception) { result.completeExceptionally(exception); }
                return result;
            }
        };
        this.context = new DefaultPluginContext(transport,
                ref -> new SecretValue(("secret:" + ref).toCharArray()), clock,
                logger, metrics, new JacksonObjectCodec(mapper), tasks);
        this.compiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(clock, "2.1.0", ignored -> true), mapper);
        this.executor = new ConnectorPipelineExecutor(clock, logger, metrics);
    }

    public PluginContext context() { return context; }
    public ObjectMapper mapper() { return mapper; }

    public void registerInitialized(ConnectorPlugin plugin) throws ConnectorException {
        plugin.initialize(context);
        registry.register(PluginHandle.builtIn(plugin, context));
    }

    public ConnectorExecutionResult execute(
            ConnectorPipelineDefinition definition,
            ConnectorExecutionRequest request) throws ConnectorException {
        try (CompiledConnectorPipeline pipeline = compiler.compile(definition)) {
            return executor.execute(pipeline, request);
        }
    }

    public String configHash(com.fasterxml.jackson.databind.JsonNode config) {
        return compiler.sha256(config);
    }

    @Override public void close() { registry.close(); }

    private static final class NoOpLogger implements PluginLogger {
        @Override public void debug(String event, Map<String, ?> safeFields) { }
        @Override public void info(String event, Map<String, ?> safeFields) { }
        @Override public void warn(String event, Map<String, ?> safeFields) { }
        @Override public void error(String event, Map<String, ?> safeFields) { }
    }

    private static final class NoOpMetrics implements PluginMetricRecorder {
        @Override public void increment(String metric, Map<String, String> lowCardinalityTags) { }
        @Override public void recordDuration(String metric, java.time.Duration duration,
                                             Map<String, String> lowCardinalityTags) { }
    }
}
