package com.dataplatform.common.plugin.legacy;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.common.plugin.runtime.CompiledConnectorPipeline;
import com.dataplatform.common.plugin.runtime.ConnectorExecutionRequest;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutor;
import com.dataplatform.common.plugin.runtime.ConnectorPluginRegistry;
import com.dataplatform.common.plugin.runtime.ConnectorStageDefinition;
import com.dataplatform.common.plugin.runtime.DefaultPluginValidationContext;
import com.dataplatform.common.plugin.runtime.NoOpPluginMetricRecorder;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.common.plugin.runtime.PluginHandle;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ManagedHttpTransport;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LegacyHttpConnectorPluginTest {

    @Test
    void executesSixStageLegacyPipelineWithMappingAuthAndStrongResultSignals() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<ConnectorRequest> captured = new AtomicReference<>();
        ManagedHttpTransport transport = (request, context) -> {
            captured.set(request);
            byte[] response = "{\"vendor_name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8);
            return new ConnectorRawResponse(200, Map.of("Content-Type", List.of("application/json")),
                    response, Duration.ofMillis(10), URI.create("https://api.example.com/query"),
                    request.body().length, response.length);
        };
        PluginContext pluginContext = TestPluginContexts.context(transport);
        LegacyHttpConnectorPlugin plugin = new LegacyHttpConnectorPlugin();
        plugin.initialize(pluginContext);
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        registry.register(PluginHandle.builtIn(plugin));
        PipelineCompiler compiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ref -> true), mapper);

        List<JsonNode> configs = List.of(
                mapper.readTree("""
                        {"apiUrl":"https://api.example.com/query","method":"POST",
                         "requestMapping":{"name":"vendor_name"},"totalTimeoutMs":30000}
                        """),
                mapper.readTree("""
                        {"authType":"BEARER","authConfig":{"token":{"secretRef":"token-ref"}}}
                        """),
                mapper.createObjectNode(), mapper.createObjectNode(), mapper.createObjectNode(),
                mapper.readTree("{\"responseMapping\":{\"vendor_name\":\"name\"}}"));
        List<ConnectorStageDefinition> definitions = new ArrayList<>();
        StageCapability[] capabilities = StageCapability.values();
        for (int index = 0; index < capabilities.length; index++) {
            JsonNode config = configs.get(index);
            definitions.add(new ConnectorStageDefinition("stage-" + index, capabilities[index],
                    LegacyHttpConnectorPlugin.PLUGIN_ID, LegacyHttpConnectorPlugin.VERSION,
                    index, true, config, compiler.sha256(config)));
        }
        ConnectorPipelineDefinition definition = new ConnectorPipelineDefinition("1", "snapshot-1", definitions);
        PluginLogger logger = pluginContext.logger();
        ConnectorPipelineExecutor executor = new ConnectorPipelineExecutor(Clock.systemUTC(), logger,
                new NoOpPluginMetricRecorder());

        try (CompiledConnectorPipeline pipeline = compiler.compile(definition)) {
            ConnectorExecutionResult result = executor.execute(pipeline,
                    new ConnectorExecutionRequest(Map.of("name", "Alice"), "vendor-a",
                            Instant.now().plusSeconds(5), () -> false));

            assertTrue(result.successful(), () -> result.errorCode() + ": " + result.safeMessage());
            assertEquals("Alice", result.normalizedData().get("name"));
            assertEquals(BillingSignal.ELIGIBLE, result.billingSignal());
            assertEquals(CacheSignal.CACHEABLE, result.cacheSignal());
            assertEquals("Bearer test-secret", captured.get().headers().get("Authorization").getFirst());
            assertTrue(new String(captured.get().body(), StandardCharsets.UTF_8).contains("vendor_name"));
            assertEquals(6, result.stageTimings().size());
        }
        assertTrue(registry.release(LegacyHttpConnectorPlugin.PLUGIN_ID, LegacyHttpConnectorPlugin.VERSION));
    }

    @Test
    void compilerRejectsMissingTransportAndTamperedConfigHash() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LegacyHttpConnectorPlugin plugin = new LegacyHttpConnectorPlugin();
        plugin.initialize(TestPluginContexts.context());
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        registry.register(PluginHandle.builtIn(plugin));
        PipelineCompiler compiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ref -> true), mapper);
        JsonNode config = mapper.createObjectNode().put("apiUrl", "https://api.example.com");
        ConnectorStageDefinition builder = new ConnectorStageDefinition("builder", StageCapability.REQUEST_BUILDER,
                LegacyHttpConnectorPlugin.PLUGIN_ID, LegacyHttpConnectorPlugin.VERSION, 0, true,
                config, compiler.sha256(config));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new ConnectorPipelineDefinition("1", "hash", List.of(builder))));

        JsonNode transportConfig = mapper.createObjectNode();
        ConnectorStageDefinition transport = new ConnectorStageDefinition("transport", StageCapability.TRANSPORT,
                LegacyHttpConnectorPlugin.PLUGIN_ID, LegacyHttpConnectorPlugin.VERSION, 1, true,
                transportConfig, "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new ConnectorPipelineDefinition("1", "hash", List.of(builder, transport))));
        registry.close();
    }
}
