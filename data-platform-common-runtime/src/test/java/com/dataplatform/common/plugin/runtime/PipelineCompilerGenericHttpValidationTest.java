package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineCompilerGenericHttpValidationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineCompiler compiler = new PipelineCompiler(
            new ConnectorPluginRegistry(),
            new DefaultPluginValidationContext(Clock.systemUTC(), "1.0.0", ref -> true), mapper);

    @Test
    void rejectsConditionalAuthRawQueryAndPlaintextCredentialHeadersBeforeStageCreation()
            throws Exception {
        List<JsonNode> invalidConfigs = List.of(
                mapper.readTree("""
                        {"endpoint":"https://vendor.example/api","method":"GET",
                         "auth":{"type":"BEARER"}}
                        """),
                mapper.readTree("""
                        {"endpoint":"https://vendor.example/api?token=plaintext","method":"GET",
                         "auth":{"type":"NONE"}}
                        """),
                mapper.readTree("""
                        {"endpoint":"https://vendor.example/api","method":"GET",
                         "headers":[{"name":"X-Api-Key","value":"plaintext"}],
                         "auth":{"type":"NONE"}}
                        """));

        for (JsonNode config : invalidConfigs) {
            assertThrows(IllegalArgumentException.class,
                    () -> compiler.compile(pipeline(config)));
        }
    }

    private ConnectorPipelineDefinition pipeline(JsonNode config) {
        JsonNode transportConfig = mapper.createObjectNode();
        JsonNode mappingConfig = mapper.createObjectNode().putNull("responseMapping");
        return new ConnectorPipelineDefinition("1", "unverified", List.of(
                stage("connector.request-builder", StageCapability.REQUEST_BUILDER,
                        GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                        100, config),
                stage("connector.request-processor", StageCapability.REQUEST_PROCESSOR,
                        GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                        200, config),
                stage("platform.transport", StageCapability.TRANSPORT,
                        PlatformCoreConnectorMetadata.PLUGIN_ID, PlatformCoreConnectorMetadata.VERSION,
                        300, transportConfig),
                stage("connector.response-parser", StageCapability.RESPONSE_PARSER,
                        GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                        400, config),
                stage("platform.response-normalizer", StageCapability.RESPONSE_NORMALIZER,
                        PlatformCoreConnectorMetadata.PLUGIN_ID, PlatformCoreConnectorMetadata.VERSION,
                        500, mappingConfig)));
    }

    private ConnectorStageDefinition stage(
            String key, StageCapability capability, String pluginId, String version,
            int order, JsonNode config) {
        return new ConnectorStageDefinition(key, capability, pluginId, version, order, true,
                config, compiler.sha256(config));
    }
}
