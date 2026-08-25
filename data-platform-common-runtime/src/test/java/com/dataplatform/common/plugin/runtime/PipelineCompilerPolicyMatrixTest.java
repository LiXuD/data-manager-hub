package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.common.plugin.legacy.LegacyHttpConnectorPlugin;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PipelineCompilerPolicyMatrixTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
    private PipelineCompiler compiler;

    @BeforeEach
    void setUp() throws Exception {
        LegacyHttpConnectorPlugin legacy = new LegacyHttpConnectorPlugin();
        legacy.initialize(TestPluginContexts.context());
        registry.register(PluginHandle.builtIn(legacy));
        InvalidStagePlugin invalid = new InvalidStagePlugin();
        invalid.initialize(TestPluginContexts.context());
        registry.register(PluginHandle.builtIn(invalid));
        compiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ignored -> true), mapper);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void rejectsDuplicateOrderStageKeyAndCapabilityOrder() {
        JsonNode builderConfig = mapper.createObjectNode().put("apiUrl", "https://api.example.com");
        JsonNode transportConfig = mapper.createObjectNode();
        ConnectorStageDefinition builder = legacyStage(
                "builder", StageCapability.REQUEST_BUILDER, 0, builderConfig);
        ConnectorStageDefinition transportSameOrder = legacyStage(
                "transport", StageCapability.TRANSPORT, 0, transportConfig);
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new ConnectorPipelineDefinition("1", "snapshot", List.of(builder, transportSameOrder))));

        ConnectorStageDefinition transportSameKey = legacyStage(
                "builder", StageCapability.TRANSPORT, 1, transportConfig);
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new ConnectorPipelineDefinition("1", "snapshot", List.of(builder, transportSameKey))));

        ConnectorStageDefinition transportFirst = legacyStage(
                "transport", StageCapability.TRANSPORT, 0, transportConfig);
        ConnectorStageDefinition builderSecond = legacyStage(
                "builder", StageCapability.REQUEST_BUILDER, 1, builderConfig);
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new ConnectorPipelineDefinition("1", "snapshot", List.of(transportFirst, builderSecond))));
    }

    @Test
    void rejectsStageConfigAboveLimitAndFactoryCapabilityMismatch() {
        JsonNode oversized = mapper.createObjectNode().put(
                "payload", "x".repeat(PipelineCompiler.MAX_STAGE_CONFIG_BYTES));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new ConnectorPipelineDefinition("1", "snapshot", List.of(legacyStage(
                        "transport", StageCapability.TRANSPORT, 0, oversized)))));

        JsonNode empty = mapper.createObjectNode();
        ConnectorStageDefinition invalid = new ConnectorStageDefinition(
                "transport", StageCapability.TRANSPORT, InvalidStagePlugin.ID, InvalidStagePlugin.VERSION,
                0, true, empty, compiler.sha256(empty));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                new ConnectorPipelineDefinition("1", "snapshot", List.of(invalid))));
    }

    @Test
    void hostSchemaRejectsConfigEvenWhenThirdPartyFactoryValidationIsEmpty() {
        JsonNode empty = mapper.createObjectNode();
        ConnectorPluginMetadata metadata = new ConnectorPluginMetadata(
                InvalidStagePlugin.ID, InvalidStagePlugin.VERSION, "a", "b", "c",
                mapper.createObjectNode().put("type", "object")
                        .set("required", mapper.createArrayNode().add("requiredValue")));
        PipelineCompiler hostCompiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ignored -> true), mapper,
                (pluginId, version) -> metadata);
        ConnectorStageDefinition stage = new ConnectorStageDefinition(
                "transport", StageCapability.TRANSPORT, InvalidStagePlugin.ID, InvalidStagePlugin.VERSION,
                0, true, empty, hostCompiler.sha256(empty), "a", "b", "c");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> hostCompiler.compile(new ConnectorPipelineDefinition(
                        "1", "snapshot", ConnectorPipelineDefinition.V2_EMBEDDED,
                        "integrity", List.of(stage))));
        assertTrue(error.getMessage().contains("signed Schema"));
    }

    private ConnectorStageDefinition legacyStage(
            String key, StageCapability capability, int order, JsonNode config) {
        return new ConnectorStageDefinition(key, capability, LegacyHttpConnectorPlugin.PLUGIN_ID,
                LegacyHttpConnectorPlugin.VERSION, order, true, config, compiler.sha256(config));
    }

    private static final class InvalidStagePlugin implements ConnectorPlugin {
        private static final String ID = "invalid-stage";
        private static final String VERSION = "1.0.0";
        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(ID, VERSION, "1.0", "Invalid", "test",
                    Set.of(StageCapability.TRANSPORT));
        }
        @Override public void initialize(PluginContext context) { }
        @Override public List<ConnectorStageFactory> stageFactories() { return List.of(new Factory()); }
        @Override public PluginSelfTestResult selfTest() { return PluginSelfTestResult.success(); }
        @Override public void close() { }
    }

    private static final class Factory implements ConnectorStageFactory {
        @Override public StageCapability capability() { return StageCapability.TRANSPORT; }
        @Override public void validate(JsonNode config, PluginValidationContext context) { }
        @Override public ConnectorStage create(CompiledStageConfig config) {
            return new ConnectorStage() {
                @Override public StageCapability capability() { return StageCapability.RESPONSE_PARSER; }
                @Override public void execute(ConnectorExchange exchange, StageExecutionContext context) { }
            };
        }
    }
}
