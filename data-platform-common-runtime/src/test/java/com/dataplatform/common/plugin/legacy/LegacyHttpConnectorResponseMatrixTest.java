package com.dataplatform.common.plugin.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.TransportStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyHttpConnectorResponseMatrixTest {

    @Test
    void distinguishesEmptyNonJsonAndHttpErrorResponses() throws Exception {
        ConnectorExecutionResult empty = execute(200, new byte[0]);
        assertEquals(ErrorCategory.RESPONSE_PARSE_ERROR, empty.errorCategory());
        assertEquals("EMPTY_RESPONSE", empty.errorCode());
        assertEquals(TransportStatus.SUCCESS, empty.transportStatus());

        ConnectorExecutionResult nonJson = execute(200, "plain-text".getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCategory.RESPONSE_PARSE_ERROR, nonJson.errorCategory());
        assertEquals("INVALID_JSON_RESPONSE", nonJson.errorCode());
        assertEquals(TransportStatus.SUCCESS, nonJson.transportStatus());

        ConnectorExecutionResult httpError = execute(503, "{\"message\":\"down\"}"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCategory.TRANSPORT_HTTP_ERROR, httpError.errorCategory());
        assertEquals(TransportStatus.HTTP_ERROR, httpError.transportStatus());
    }

    private ConnectorExecutionResult execute(int status, byte[] body) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var context = TestPluginContexts.context((request, executionContext) ->
                new ConnectorRawResponse(status, Map.of(), body, Duration.ofMillis(1),
                        URI.create("https://api.example.com/query"), request.body().length, body.length));
        LegacyHttpConnectorPlugin plugin = new LegacyHttpConnectorPlugin();
        plugin.initialize(context);
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        registry.register(PluginHandle.builtIn(plugin));
        PipelineCompiler compiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ignored -> true), mapper);
        List<ConnectorStageDefinition> definitions = List.of(
                stage(compiler, "builder", StageCapability.REQUEST_BUILDER, 0,
                        mapper.createObjectNode().put("apiUrl", "https://api.example.com/query")
                                .put("method", "GET")),
                stage(compiler, "transport", StageCapability.TRANSPORT, 1, mapper.createObjectNode()),
                stage(compiler, "parser", StageCapability.RESPONSE_PARSER, 2, mapper.createObjectNode()),
                stage(compiler, "normalizer", StageCapability.RESPONSE_NORMALIZER, 3,
                        mapper.createObjectNode()));
        ConnectorPipelineExecutor executor = new ConnectorPipelineExecutor(
                Clock.systemUTC(), context.logger(), new NoOpPluginMetricRecorder());
        try (registry; CompiledConnectorPipeline pipeline = compiler.compile(
                new ConnectorPipelineDefinition("1", "snapshot", definitions))) {
            return executor.execute(pipeline, new ConnectorExecutionRequest(Map.of("id", "1"), "DEMO",
                    Instant.now().plusSeconds(5), () -> false));
        }
    }

    private ConnectorStageDefinition stage(PipelineCompiler compiler, String key,
                                            StageCapability capability, int order, JsonNode config) {
        return new ConnectorStageDefinition(key, capability, LegacyHttpConnectorPlugin.PLUGIN_ID,
                LegacyHttpConnectorPlugin.VERSION, order, true, config, compiler.sha256(config));
    }
}
