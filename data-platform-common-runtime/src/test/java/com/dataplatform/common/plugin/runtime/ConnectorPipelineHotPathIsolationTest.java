package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConnectorPipelineHotPathIsolationTest {

    @Test
    void rejectsMoreThanFiftyStagesBeforeAnyPluginRegistryLookup() {
        ObjectMapper mapper = new ObjectMapper();
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        PipelineCompiler compiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ignored -> true), mapper);
        JsonNode config = mapper.createObjectNode();
        List<ConnectorStageDefinition> stages = new java.util.ArrayList<>();
        for (int index = 0; index < PipelineCompiler.MAX_STAGES; index++) {
            stages.add(stage(compiler, "processor-" + index, StageCapability.REQUEST_PROCESSOR,
                    index, config));
        }
        stages.add(stage(compiler, "transport", StageCapability.TRANSPORT,
                PipelineCompiler.MAX_STAGES, config));

        try (registry) {
            assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                    new ConnectorPipelineDefinition("7", "snapshot", stages)));
        }
    }

    @Test
    void requestHotPathUsesOnlyPrecompiledStagesAndPinnedPluginLeases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CountingPlugin plugin = new CountingPlugin();
        plugin.initialize(TestPluginContexts.context());
        PluginHandle handle = PluginHandle.builtIn(plugin);
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        registry.register(handle);
        PipelineCompiler compiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ignored -> true), mapper);
        JsonNode config = mapper.createObjectNode();
        ConnectorPipelineDefinition definition = new ConnectorPipelineDefinition("7", "snapshot", List.of(
                stage(compiler, "builder", StageCapability.REQUEST_BUILDER, 0, config),
                stage(compiler, "transport", StageCapability.TRANSPORT, 1, config),
                stage(compiler, "normalizer", StageCapability.RESPONSE_NORMALIZER, 2, config)));
        var context = TestPluginContexts.context();
        ConnectorPipelineExecutor executor = new ConnectorPipelineExecutor(
                Clock.systemUTC(), context.logger(), context.metrics());

        try (registry; CompiledConnectorPipeline pipeline = compiler.compile(definition)) {
            assertEquals(3, plugin.validationCount.get());
            assertEquals(3, plugin.creationCount.get());
            assertEquals(3, handle.referenceCount());

            for (int index = 0; index < 1_000; index++) {
                var result = executor.execute(pipeline, new ConnectorExecutionRequest(
                        Map.of("id", index), "HOT_PATH", Instant.now().plusSeconds(5), () -> false));
                assertTrue(result.successful());
                assertEquals(index, result.normalizedData().get("id"));
            }

            assertEquals(3, plugin.validationCount.get(),
                    "request execution must not revalidate stage configuration");
            assertEquals(3, plugin.creationCount.get(),
                    "request execution must not recreate stages or parse plugin metadata");
            assertEquals(3, handle.referenceCount(),
                    "request execution must not reacquire the plugin registry");
            assertEquals(1_000, plugin.builderExecutions.get());
            assertEquals(1_000, plugin.transportExecutions.get());
            assertEquals(1_000, plugin.normalizerExecutions.get());
        }
        assertEquals(0, handle.referenceCount());
    }

    private ConnectorStageDefinition stage(PipelineCompiler compiler, String key,
                                            StageCapability capability, int order, JsonNode config) {
        return new ConnectorStageDefinition(key, capability, CountingPlugin.ID, CountingPlugin.VERSION,
                order, true, config, compiler.sha256(config));
    }

    private static final class CountingPlugin implements ConnectorPlugin {
        private static final String ID = "hot-path-fixture";
        private static final String VERSION = "1.0.0";
        private final AtomicInteger validationCount = new AtomicInteger();
        private final AtomicInteger creationCount = new AtomicInteger();
        private final AtomicInteger builderExecutions = new AtomicInteger();
        private final AtomicInteger transportExecutions = new AtomicInteger();
        private final AtomicInteger normalizerExecutions = new AtomicInteger();

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(ID, VERSION, "1.0", "Hot path fixture", "test", Set.of(
                    StageCapability.REQUEST_BUILDER,
                    StageCapability.TRANSPORT,
                    StageCapability.RESPONSE_NORMALIZER));
        }

        @Override public void initialize(PluginContext context) { }

        @Override public List<ConnectorStageFactory> stageFactories() {
            return List.of(factory(StageCapability.REQUEST_BUILDER, builderExecutions),
                    factory(StageCapability.TRANSPORT, transportExecutions),
                    factory(StageCapability.RESPONSE_NORMALIZER, normalizerExecutions));
        }

        private ConnectorStageFactory factory(StageCapability capability, AtomicInteger executions) {
            return new ConnectorStageFactory() {
                @Override public StageCapability capability() { return capability; }

                @Override public void validate(JsonNode config, PluginValidationContext context) {
                    validationCount.incrementAndGet();
                }

                @Override public ConnectorStage create(CompiledStageConfig config) {
                    creationCount.incrementAndGet();
                    return new ConnectorStage() {
                        @Override public StageCapability capability() { return capability; }

                        @Override
                        public void execute(ConnectorExchange exchange, StageExecutionContext context) {
                            executions.incrementAndGet();
                            switch (capability) {
                                case REQUEST_BUILDER -> exchange.setRequest(new ConnectorRequest(
                                        "GET", URI.create("https://api.example.com/query"), Map.of(), Map.of(),
                                        "application/json", new byte[0], Duration.ofSeconds(1),
                                        Duration.ofSeconds(1), Duration.ofSeconds(2),
                                        IdempotencyPolicy.IDEMPOTENT, null, 4096));
                                case TRANSPORT -> exchange.setRawResponse(new ConnectorRawResponse(
                                        200, Map.of(), "{}".getBytes(StandardCharsets.UTF_8),
                                        Duration.ZERO, URI.create("https://api.example.com/query"), 0, 2));
                                case RESPONSE_NORMALIZER -> {
                                    exchange.setNormalizedData(exchange.standardParameters());
                                    exchange.setBusinessStatus(BusinessStatus.SUCCESS);
                                    exchange.setBillingSignal(BillingSignal.ELIGIBLE);
                                    exchange.setCacheSignal(CacheSignal.CACHEABLE);
                                }
                                default -> throw new IllegalStateException("Unexpected capability: " + capability);
                            }
                        }
                    };
                }
            };
        }

        @Override public PluginSelfTestResult selfTest() { return PluginSelfTestResult.success(); }
        @Override public void close() { }
    }
}
