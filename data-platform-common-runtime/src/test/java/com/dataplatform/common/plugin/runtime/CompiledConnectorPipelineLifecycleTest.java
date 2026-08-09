package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.RequestScopedConnectorStage;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.dataplatform.plugin.spi.StageLifecycle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompiledConnectorPipelineLifecycleTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorPluginRegistry registry = new ConnectorPluginRegistry();

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void requestScopedStagesAreCreatedAndClosedForEveryExecution() throws Exception {
        TestPlugin plugin = register("scoped", StageLifecycle.REQUEST_SCOPED, false,
                new CountDownLatch(0), new CountDownLatch(0));
        try (CompiledConnectorPipeline pipeline = compile(plugin, "value", false)) {
            assertEquals("value", execute(pipeline).normalizedData().get("value"));
            assertEquals("value", execute(pipeline).normalizedData().get("value"));
        }

        assertEquals(6, plugin.created.get());
        assertEquals(6, plugin.closed.get());
        assertEquals(0, registryHandle(plugin).referenceCount());
    }

    @Test
    void retiredPipelineLetsOldRequestFinishAndClosesOnlyAfterLastRelease() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestPlugin plugin = register("switch", StageLifecycle.SHARED, false, entered, release);
        CompiledConnectorPipeline oldPipeline = compile(plugin, "old", true);
        CompiledConnectorPipeline nextPipeline = compile(plugin, "new", false);
        CompiledConnectorPipeline.RequestLease oldRequest = oldPipeline.acquire();
        var pool = Executors.newSingleThreadExecutor();
        try {
            var oldFuture = pool.submit(() -> executor().executeWithOutcome(oldRequest, request()).result());
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            oldPipeline.close();
            assertTrue(oldPipeline.retired());
            assertFalse(oldPipeline.destroyed());
            assertEquals("new", execute(nextPipeline).normalizedData().get("value"));
            assertEquals(0, plugin.closed.get(), "old shared stages must remain open for the leased request");

            release.countDown();
            assertEquals("old", oldFuture.get(2, TimeUnit.SECONDS).normalizedData().get("value"));
            assertFalse(oldPipeline.destroyed(), "the caller still owns the selection lease");
            oldRequest.close();
            assertTrue(oldPipeline.destroyed());
            assertEquals(3, plugin.closed.get());
        } finally {
            release.countDown();
            oldRequest.close();
            nextPipeline.close();
            pool.shutdownNow();
        }
        assertEquals(6, plugin.closed.get());
        assertEquals(0, registryHandle(plugin).referenceCount());
    }

    @Test
    void faultyStageCloseDoesNotPinPluginLeaseOrBlockNextPipeline() throws Exception {
        TestPlugin plugin = register("close-failure", StageLifecycle.SHARED, true,
                new CountDownLatch(0), new CountDownLatch(0));
        CompiledConnectorPipeline first = compile(plugin, "first", false);
        first.close();
        assertTrue(first.destroyed());
        assertEquals(0, registryHandle(plugin).referenceCount());

        try (CompiledConnectorPipeline second = compile(plugin, "second", false)) {
            assertEquals("second", execute(second).normalizedData().get("value"));
        }
        assertEquals(0, registryHandle(plugin).referenceCount());
    }

    @Test
    void oneHundredRetireCyclesLeaveNoPipelineOrPluginReferences() throws Exception {
        TestPlugin plugin = register("cycles", StageLifecycle.SHARED, false,
                new CountDownLatch(0), new CountDownLatch(0));
        for (int cycle = 0; cycle < 100; cycle++) {
            CompiledConnectorPipeline pipeline = compile(plugin, "v" + cycle, false);
            try (CompiledConnectorPipeline.RequestLease ignored = pipeline.acquire()) {
                pipeline.close();
                assertFalse(pipeline.destroyed());
            }
            assertTrue(pipeline.destroyed());
        }
        assertEquals(300, plugin.created.get());
        assertEquals(300, plugin.closed.get());
        assertEquals(0, registryHandle(plugin).referenceCount());
    }

    private TestPlugin register(String id, StageLifecycle lifecycle, boolean failClose,
                                CountDownLatch entered, CountDownLatch release) {
        TestPlugin plugin = new TestPlugin(id, lifecycle, failClose, entered, release);
        plugin.initialize(TestPluginContexts.context());
        registry.register(PluginHandle.builtIn(plugin));
        return plugin;
    }

    private PluginHandle registryHandle(TestPlugin plugin) throws Exception {
        try (PluginHandle.Lease lease = registry.acquire(plugin.id, TestPlugin.VERSION)) {
            return lease.handle();
        }
    }

    private CompiledConnectorPipeline compile(TestPlugin plugin, String value, boolean block) throws Exception {
        PipelineCompiler compiler = compiler();
        var config = mapper.createObjectNode().put("value", value).put("block", block);
        List<ConnectorStageDefinition> stages = List.of(
                stage(compiler, plugin, "builder", StageCapability.REQUEST_BUILDER, 0, config),
                stage(compiler, plugin, "transport", StageCapability.TRANSPORT, 1, config),
                stage(compiler, plugin, "normalizer", StageCapability.RESPONSE_NORMALIZER, 2, config));
        return compiler.compile(new ConnectorPipelineDefinition(value, "snapshot-" + value, stages));
    }

    private ConnectorStageDefinition stage(PipelineCompiler compiler, TestPlugin plugin, String key,
                                             StageCapability capability, int order, JsonNode config) {
        return new ConnectorStageDefinition(key, capability, plugin.id, TestPlugin.VERSION,
                order, true, config, compiler.sha256(config));
    }

    private PipelineCompiler compiler() {
        return new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ignored -> true), mapper);
    }

    private ConnectorPipelineExecutor executor() {
        var context = TestPluginContexts.context();
        return new ConnectorPipelineExecutor(Clock.systemUTC(), context.logger(), context.metrics());
    }

    private com.dataplatform.plugin.spi.ConnectorExecutionResult execute(CompiledConnectorPipeline pipeline) {
        return executor().execute(pipeline, request());
    }

    private ConnectorExecutionRequest request() {
        return new ConnectorExecutionRequest(Map.of(), "DEMO", Instant.now().plusSeconds(5), () -> false);
    }

    private static final class TestPlugin implements ConnectorPlugin {
        private static final String VERSION = "1.0.0";
        private final String id;
        private final StageLifecycle lifecycle;
        private final boolean failClose;
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();

        private TestPlugin(String id, StageLifecycle lifecycle, boolean failClose,
                           CountDownLatch entered, CountDownLatch release) {
            this.id = id;
            this.lifecycle = lifecycle;
            this.failClose = failClose;
            this.entered = entered;
            this.release = release;
        }

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(id, VERSION, "1.0", id, "test", Set.of(
                    StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                    StageCapability.RESPONSE_NORMALIZER));
        }
        @Override public void initialize(PluginContext context) { }
        @Override public List<ConnectorStageFactory> stageFactories() {
            return List.of(factory(StageCapability.REQUEST_BUILDER), factory(StageCapability.TRANSPORT),
                    factory(StageCapability.RESPONSE_NORMALIZER));
        }
        private ConnectorStageFactory factory(StageCapability capability) {
            return new ConnectorStageFactory() {
                @Override public StageCapability capability() { return capability; }
                @Override public StageLifecycle lifecycle() { return lifecycle; }
                @Override public void validate(JsonNode config, PluginValidationContext context) { }
                @Override public ConnectorStage create(CompiledStageConfig config) {
                    created.incrementAndGet();
                    return new TestStage(capability, config.config(), failClose, entered, release, closed);
                }
            };
        }
        @Override public PluginSelfTestResult selfTest() { return PluginSelfTestResult.success(); }
        @Override public void close() { }
    }

    private record TestStage(StageCapability capability, JsonNode config, boolean failClose,
                             CountDownLatch entered, CountDownLatch release,
                             AtomicInteger closed) implements RequestScopedConnectorStage {
        @Override public void execute(ConnectorExchange exchange, StageExecutionContext context)
                throws ConnectorException {
            if (capability == StageCapability.REQUEST_BUILDER) {
                if (config.path("block").asBoolean()) {
                    entered.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR, "TEST_TIMEOUT",
                                    "test release timed out", RequestDeliveryState.NOT_SENT);
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR, "TEST_INTERRUPTED",
                                "test interrupted", RequestDeliveryState.NOT_SENT, exception);
                    }
                }
                exchange.setRequest(new ConnectorRequest("GET", URI.create("https://api.example.com"),
                        Map.of(), Map.of(), null, new byte[0], Duration.ofSeconds(1), Duration.ofSeconds(1),
                        Duration.ofSeconds(2), IdempotencyPolicy.IDEMPOTENT, null, 1024));
            } else if (capability == StageCapability.TRANSPORT) {
                exchange.setRawResponse(new ConnectorRawResponse(200, Map.of(), new byte[0],
                        Duration.ZERO, URI.create("https://api.example.com"), 0, 0));
            } else if (capability == StageCapability.RESPONSE_NORMALIZER) {
                exchange.setNormalizedData(Map.of("value", config.path("value").asText()));
            }
        }
        @Override public void close() {
            closed.incrementAndGet();
            if (failClose) throw new IllegalStateException("close failed");
        }
    }
}
