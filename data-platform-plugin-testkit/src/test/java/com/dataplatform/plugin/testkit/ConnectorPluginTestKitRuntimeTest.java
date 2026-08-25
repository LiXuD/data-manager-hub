package com.dataplatform.plugin.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.runtime.ConnectorExecutionRequest;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
import com.dataplatform.common.plugin.runtime.ConnectorStageDefinition;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorPlugin;
import com.dataplatform.common.plugin.runtime.HostIdempotencyContext;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.ManagedTransportSession;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
import com.dataplatform.plugin.spi.VendorParseResult;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.testkit.examples.SingleHttpExampleConnector;
import com.dataplatform.plugin.testkit.examples.TokenBusinessExampleConnector;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectorPluginTestKitRuntimeTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void oneClassSingleHttpRunsThroughPlatformTransportAndStrictHostMapping() throws Exception {
        var transport = ManagedTransportFixtures.scripted(response(
                "https://vendor.example/query", "{\"company\":{\"name\":\"Acme\"}}"));
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(transport, CLOCK)) {
            kit.registerInitialized(new SingleHttpExampleConnector());
            kit.registerInitialized(new PlatformCoreConnectorPlugin());
            JsonNode connectorConfig = kit.mapper().createObjectNode()
                    .put("endpoint", "https://vendor.example/query");
            JsonNode transportConfig = kit.mapper().createObjectNode();
            JsonNode mappingConfig = kit.mapper().readTree("""
                    {"responseMapping":[{"targetField":"legalName","sourcePath":"company.name"}]}
                    """);
            List<ConnectorStageDefinition> stages = List.of(
                    stage(kit, "connector.request-builder", StageCapability.REQUEST_BUILDER,
                            SingleHttpExampleConnector.PLUGIN_ID, 0, connectorConfig),
                    stage(kit, "platform.transport", StageCapability.TRANSPORT,
                            PlatformCoreConnectorPlugin.PLUGIN_ID, 1, transportConfig),
                    stage(kit, "connector.response-parser", StageCapability.RESPONSE_PARSER,
                            SingleHttpExampleConnector.PLUGIN_ID, 2, connectorConfig),
                    stage(kit, "platform.response-normalizer", StageCapability.RESPONSE_NORMALIZER,
                            PlatformCoreConnectorPlugin.PLUGIN_ID, 3, mappingConfig));

            var result = kit.execute(definition(stages), request("request-single", 42L, 1));

            assertTrue(result.successful());
            assertEquals(Map.of("legalName", "Acme"), result.normalizedData());
            assertEquals(RequestDeliveryState.SENT, result.deliveryState());
            assertEquals(1, transport.requests().size());
        }
    }

    @Test
    void tokenBusinessExampleUsesTwoManagedCallsAndPreservesSentOnLaterFailure() throws Exception {
        var successfulTransport = ManagedTransportFixtures.scripted(
                response("https://vendor.example/token", "{\"access_token\":\"token-1\"}"),
                response("https://vendor.example/business", "{\"company\":\"Acme\"}"));
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(successfulTransport, CLOCK)) {
            kit.registerInitialized(new TokenBusinessExampleConnector());
            JsonNode config = multiConfig(kit);
            List<ConnectorStageDefinition> stages = multiStages(kit, config);

            var result = kit.execute(definition(stages), request("request-multi", 84L, 3));

            assertTrue(result.successful());
            assertEquals("Acme", result.normalizedData().get("company"));
            assertEquals(2, successfulTransport.requests().size());
            assertEquals(List.of("Bearer token-1"),
                    successfulTransport.requests().get(1).headers().get("Authorization"));
            assertEquals(RequestDeliveryState.SENT, result.deliveryState());
        }

        ConnectorException notSentFailure = new ConnectorException(
                ErrorCategory.TRANSPORT_CONNECTION_ERROR, "SECOND_CONNECT_FAILED",
                "Business endpoint could not be reached", RequestDeliveryState.NOT_SENT);
        var failingTransport = ManagedTransportFixtures.scripted(
                response("https://vendor.example/token", "{\"access_token\":\"token-1\"}"),
                notSentFailure);
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(failingTransport, CLOCK)) {
            kit.registerInitialized(new TokenBusinessExampleConnector());
            JsonNode config = multiConfig(kit);
            var result = kit.execute(definition(multiStages(kit, config)),
                    request("request-multi-failure", 84L, 1));

            assertEquals(ErrorCategory.TRANSPORT_CONNECTION_ERROR, result.errorCategory());
            assertEquals(RequestDeliveryState.SENT, result.deliveryState());
        }
    }

    @Test
    void platformMappingFailsClosedForUnknownOrMissingPaths() throws Exception {
        var transport = ManagedTransportFixtures.scripted(response(
                "https://vendor.example/query", "{\"company\":{\"name\":\"Acme\"}}"));
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(transport, CLOCK)) {
            kit.registerInitialized(new SingleHttpExampleConnector());
            kit.registerInitialized(new PlatformCoreConnectorPlugin());
            JsonNode connectorConfig = kit.mapper().createObjectNode()
                    .put("endpoint", "https://vendor.example/query");
            JsonNode invalidMapping = kit.mapper().readTree("""
                    {"responseMapping":[{"targetField":"name","sourcePath":"missing.value"}]}
                    """);
            List<ConnectorStageDefinition> stages = List.of(
                    stage(kit, "builder", StageCapability.REQUEST_BUILDER,
                            SingleHttpExampleConnector.PLUGIN_ID, 0, connectorConfig),
                    stage(kit, "transport", StageCapability.TRANSPORT,
                            PlatformCoreConnectorPlugin.PLUGIN_ID, 1, kit.mapper().createObjectNode()),
                    stage(kit, "parser", StageCapability.RESPONSE_PARSER,
                            SingleHttpExampleConnector.PLUGIN_ID, 2, connectorConfig),
                    stage(kit, "normalizer", StageCapability.RESPONSE_NORMALIZER,
                            PlatformCoreConnectorPlugin.PLUGIN_ID, 3, invalidMapping));

            var result = kit.execute(definition(stages), request("request-invalid-map", 42L, 1));
            assertEquals(ErrorCategory.CONFIGURATION_ERROR, result.errorCategory());
            assertEquals("HOST_MAPPING_INVALID", result.errorCode());
            assertEquals(RequestDeliveryState.SENT, result.deliveryState());
        }
    }

    @Test
    void invalidPlatformMappingIsRejectedBeforeAnyNetworkCall() throws Exception {
        var transport = ManagedTransportFixtures.scripted(response(
                "https://vendor.example/query", "{}"));
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(transport, CLOCK)) {
            kit.registerInitialized(new SingleHttpExampleConnector());
            kit.registerInitialized(new PlatformCoreConnectorPlugin());
            JsonNode connectorConfig = kit.mapper().createObjectNode()
                    .put("endpoint", "https://vendor.example/query");
            JsonNode invalidMapping = kit.mapper().readTree("""
                    {"responseMapping":[],"unexpected":true}
                    """);
            List<ConnectorStageDefinition> stages = List.of(
                    stage(kit, "builder", StageCapability.REQUEST_BUILDER,
                            SingleHttpExampleConnector.PLUGIN_ID, 0, connectorConfig),
                    stage(kit, "transport", StageCapability.TRANSPORT,
                            PlatformCoreConnectorPlugin.PLUGIN_ID, 1, kit.mapper().createObjectNode()),
                    stage(kit, "parser", StageCapability.RESPONSE_PARSER,
                            SingleHttpExampleConnector.PLUGIN_ID, 2, connectorConfig),
                    stage(kit, "normalizer", StageCapability.RESPONSE_NORMALIZER,
                            PlatformCoreConnectorPlugin.PLUGIN_ID, 3, invalidMapping));

            ConnectorException failure = assertThrows(ConnectorException.class,
                    () -> kit.execute(definition(stages), request("request-invalid-config", 42L, 1)));

            assertEquals(ErrorCategory.CONFIGURATION_ERROR, failure.category());
            assertEquals("HOST_MAPPING_INVALID", failure.errorCode());
            assertEquals(RequestDeliveryState.NOT_SENT, failure.deliveryState());
            assertEquals(0, transport.requests().size());
        }
    }

    @Test
    void managedSessionEnforcesFiveCallsAndStopsExpiredOrCancelledAttemptsBeforeNetwork() throws Exception {
        Object[] fiveResponses = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> response("https://vendor.example/" + index, "{}"))
                .toArray();
        var transport = ManagedTransportFixtures.scripted(fiveResponses);
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(transport, CLOCK)) {
            SixCallPlugin plugin = new SixCallPlugin();
            kit.registerInitialized(plugin);
            JsonNode config = kit.mapper().createObjectNode()
                    .put("endpoint", "https://vendor.example/0");
            var result = kit.execute(definition(stagesFor(kit, plugin, config)),
                    request("request-limit", 91L, 1));

            assertEquals(ErrorCategory.CONTRACT_VIOLATION, result.errorCategory());
            assertEquals("MANAGED_TRANSPORT_CALL_LIMIT", result.errorCode());
            assertEquals(RequestDeliveryState.SENT, result.deliveryState());
            assertEquals(5, transport.requests().size());
        }

        var cancelledTransport = ManagedTransportFixtures.scripted(response(
                "https://vendor.example/0", "{}"));
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(cancelledTransport, CLOCK)) {
            SixCallPlugin plugin = new SixCallPlugin();
            kit.registerInitialized(plugin);
            JsonNode config = kit.mapper().createObjectNode()
                    .put("endpoint", "https://vendor.example/0");
            ConnectorExecutionRequest cancelled = new ConnectorExecutionRequest(
                    Map.of(), "DEMO", CLOCK.instant().plusSeconds(30), () -> true,
                    "request-cancelled", 91L, 1, HostIdempotencyContext.nonRetryable());
            var cancelledResult = kit.execute(definition(stagesFor(kit, plugin, config)), cancelled);
            assertEquals("REQUEST_CANCELLED", cancelledResult.errorCode());
            assertEquals(RequestDeliveryState.NOT_SENT, cancelledResult.deliveryState());
            assertEquals(0, cancelledTransport.requests().size());
        }

        var expiredTransport = ManagedTransportFixtures.scripted(response(
                "https://vendor.example/0", "{}"));
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(expiredTransport, CLOCK)) {
            SixCallPlugin plugin = new SixCallPlugin();
            kit.registerInitialized(plugin);
            JsonNode config = kit.mapper().createObjectNode()
                    .put("endpoint", "https://vendor.example/0");
            ConnectorExecutionRequest expired = new ConnectorExecutionRequest(
                    Map.of(), "DEMO", CLOCK.instant(), () -> false,
                    "request-expired", 91L, 1, HostIdempotencyContext.nonRetryable());
            var expiredResult = kit.execute(definition(stagesFor(kit, plugin, config)), expired);
            assertEquals("EXECUTION_DEADLINE_EXCEEDED", expiredResult.errorCode());
            assertEquals(RequestDeliveryState.NOT_SENT, expiredResult.deliveryState());
            assertEquals(0, expiredTransport.requests().size());
        }
    }

    @Test
    void invocationKeepsRequestAttemptDeadlineAndCanonicalConfigAcrossEveryConnectorStage() throws Exception {
        var transport = ManagedTransportFixtures.scripted(response(
                "https://vendor.example/0", "{}"));
        JsonNode expected = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("endpoint", "https://vendor.example/0");
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(transport, CLOCK)) {
            InvocationAssertingPlugin plugin = new InvocationAssertingPlugin(
                    "request-context", 7, CLOCK.instant().plusSeconds(30), expected);
            kit.registerInitialized(plugin);
            ConnectorExecutionRequest request = new ConnectorExecutionRequest(
                    Map.of("input", "fixed"), "DEMO", CLOCK.instant().plusSeconds(30), () -> false,
                    "request-context", 99L, 7, HostIdempotencyContext.nonRetryable());

            var result = kit.execute(definition(stagesFor(kit, plugin, expected)), request);

            assertTrue(result.successful());
            assertEquals(1, transport.requests().size());
        }
    }

    @Test
    void genericHttpRunsThroughTheProductionCompilerExecutorAndPlatformTransport() throws Exception {
        var transport = ManagedTransportFixtures.scripted(responseJson(
                "https://vendor.example/query", "{\"data\":{\"name\":\"Acme\"}}"));
        try (ConnectorPluginTestKit kit = new ConnectorPluginTestKit(transport, CLOCK)) {
            kit.registerInitialized(new GenericHttpConnectorPlugin());
            kit.registerInitialized(new PlatformCoreConnectorPlugin());
            JsonNode genericConfig = kit.mapper().readTree("""
                    {"endpoint":"https://vendor.example/query","method":"GET",
                     "requestMapping":[{"sourceField":"companyName","targetField":"q"}],
                     "auth":{"type":"BEARER","tokenRef":"token"},"dataPath":"data"}
                    """);
            JsonNode platformTransport = kit.mapper().createObjectNode();
            JsonNode mapping = kit.mapper().readTree("""
                    {"responseMapping":[{"targetField":"legalName","sourcePath":"name"}]}
                    """);
            List<ConnectorStageDefinition> stages = List.of(
                    stageVersion(kit, "connector.request-builder", StageCapability.REQUEST_BUILDER,
                            GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                            0, genericConfig),
                    stageVersion(kit, "connector.request-processor", StageCapability.REQUEST_PROCESSOR,
                            GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                            1, genericConfig),
                    stageVersion(kit, "platform.transport", StageCapability.TRANSPORT,
                            PlatformCoreConnectorPlugin.PLUGIN_ID, PlatformCoreConnectorPlugin.VERSION,
                            2, platformTransport),
                    stageVersion(kit, "connector.response-parser", StageCapability.RESPONSE_PARSER,
                            GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                            3, genericConfig),
                    stageVersion(kit, "platform.response-normalizer", StageCapability.RESPONSE_NORMALIZER,
                            PlatformCoreConnectorPlugin.PLUGIN_ID, PlatformCoreConnectorPlugin.VERSION,
                            4, mapping));

            var result = kit.execute(definition(stages), request("request-generic", 101L, 1));

            assertTrue(result.successful());
            assertEquals(Map.of("legalName", "Acme"), result.normalizedData());
            assertEquals(List.of("Acme"), transport.requests().getFirst().query().get("q"));
            assertEquals(List.of("Bearer secret:token"),
                    transport.requests().getFirst().headers().get("Authorization"));
        }
    }

    private JsonNode multiConfig(ConnectorPluginTestKit kit) {
        return kit.mapper().createObjectNode()
                .put("tokenEndpoint", "https://vendor.example/token")
                .put("businessEndpoint", "https://vendor.example/business");
    }

    private List<ConnectorStageDefinition> multiStages(ConnectorPluginTestKit kit, JsonNode config) {
        return List.of(
                stage(kit, "connector.request-builder", StageCapability.REQUEST_BUILDER,
                        TokenBusinessExampleConnector.PLUGIN_ID, 0, config),
                stage(kit, "connector.transport", StageCapability.TRANSPORT,
                        TokenBusinessExampleConnector.PLUGIN_ID, 1, config),
                stage(kit, "connector.response-parser", StageCapability.RESPONSE_PARSER,
                        TokenBusinessExampleConnector.PLUGIN_ID, 2, config),
                stage(kit, "connector.response-normalizer", StageCapability.RESPONSE_NORMALIZER,
                        TokenBusinessExampleConnector.PLUGIN_ID, 3, config));
    }

    private ConnectorStageDefinition stage(
            ConnectorPluginTestKit kit, String key, StageCapability capability,
            String pluginId, int order, JsonNode config) {
        return stageVersion(kit, key, capability, pluginId, "1.0.0", order, config);
    }

    private ConnectorStageDefinition stageVersion(
            ConnectorPluginTestKit kit, String key, StageCapability capability,
            String pluginId, String version, int order, JsonNode config) {
        return new ConnectorStageDefinition(key, capability, pluginId, version, order, true,
                config, kit.configHash(config));
    }

    private ConnectorPipelineDefinition definition(List<ConnectorStageDefinition> stages) {
        return new ConnectorPipelineDefinition("1", "testkit-snapshot", stages);
    }

    private ConnectorExecutionRequest request(String id, long vendorConfigId, int attempt) {
        return new ConnectorExecutionRequest(Map.of("companyName", "Acme"), "DEMO",
                CLOCK.instant().plusSeconds(30), () -> false, id, vendorConfigId, attempt,
                HostIdempotencyContext.nonRetryable());
    }

    private ConnectorRawResponse response(String endpoint, String body) {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new ConnectorRawResponse(200, Map.of(), bytes, Duration.ofMillis(5),
                URI.create(endpoint), 0, bytes.length);
    }

    private ConnectorRawResponse responseJson(String endpoint, String body) {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new ConnectorRawResponse(200, Map.of("Content-Type", List.of("application/json")),
                bytes, Duration.ofMillis(5), URI.create(endpoint), 0, bytes.length);
    }

    private List<ConnectorStageDefinition> stagesFor(
            ConnectorPluginTestKit kit, AbstractVendorConnectorPlugin plugin, JsonNode config) {
        return List.of(
                stage(kit, "builder", StageCapability.REQUEST_BUILDER,
                        plugin.descriptor().pluginId(), 0, config),
                stage(kit, "transport", StageCapability.TRANSPORT,
                        plugin.descriptor().pluginId(), 1, config),
                stage(kit, "parser", StageCapability.RESPONSE_PARSER,
                        plugin.descriptor().pluginId(), 2, config),
                stage(kit, "normalizer", StageCapability.RESPONSE_NORMALIZER,
                        plugin.descriptor().pluginId(), 3, config));
    }

    private static class InvocationAssertingPlugin extends AbstractVendorConnectorPlugin {
        private final String expectedRequestId;
        private final int expectedAttempt;
        private final Instant expectedDeadline;
        private final JsonNode expectedConfig;

        InvocationAssertingPlugin(String expectedRequestId, int expectedAttempt,
                                  Instant expectedDeadline, JsonNode expectedConfig) {
            super(ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP,
                    ConnectorOutputMode.PLUGIN_NORMALIZED);
            this.expectedRequestId = expectedRequestId;
            this.expectedAttempt = expectedAttempt;
            this.expectedDeadline = expectedDeadline;
            this.expectedConfig = expectedConfig.deepCopy();
        }

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor("testkit-context-asserting", "1.0.0", "1.1", "Context",
                    "test", Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                    StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER));
        }

        @Override protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation) {
            assertInvocation(invocation);
            return requestFor(invocation.pluginConfig().path("endpoint").asText());
        }

        @Override protected ConnectorRawResponse executeManagedTransport(
                VendorConnectorInvocation invocation, ManagedTransportSession session,
                ConnectorRequest request) throws ConnectorException {
            assertInvocation(invocation);
            return session.execute(request);
        }

        @Override protected VendorParseResult parseResponse(
                VendorConnectorInvocation invocation, ConnectorRawResponse response) {
            assertInvocation(invocation);
            return VendorParseResult.success(Map.of("ok", true));
        }

        @Override protected Map<String, Object> normalizeResponse(
                VendorConnectorInvocation invocation, VendorParseResult parsed) {
            assertInvocation(invocation);
            return parsed.data();
        }

        void assertInvocation(VendorConnectorInvocation invocation) {
            assertEquals(expectedRequestId, invocation.requestId());
            assertEquals(expectedAttempt, invocation.attemptNo());
            assertEquals(expectedDeadline, invocation.deadline().expiresAt());
            assertEquals(expectedConfig, invocation.pluginConfig());
            assertEquals("fixed", invocation.standardInput().path("input").asText());
        }
    }

    private static final class SixCallPlugin extends InvocationAssertingPlugin {
        SixCallPlugin() {
            super("unused", 1, CLOCK.instant().plusSeconds(30),
                    new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                            .put("endpoint", "https://vendor.example/0"));
        }

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor("testkit-six-call", "1.0.0", "1.1", "Six Call",
                    "test", Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                    StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER));
        }

        @Override protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation) {
            return requestFor(invocation.pluginConfig().path("endpoint").asText());
        }

        @Override protected ConnectorRawResponse executeManagedTransport(
                VendorConnectorInvocation invocation, ManagedTransportSession session,
                ConnectorRequest request) throws ConnectorException {
            ConnectorRawResponse response = null;
            for (int index = 0; index < 6; index++) response = session.execute(request);
            return response;
        }

        @Override protected VendorParseResult parseResponse(
                VendorConnectorInvocation invocation, ConnectorRawResponse response) {
            return VendorParseResult.success(Map.of("ok", true));
        }

        @Override protected Map<String, Object> normalizeResponse(
                VendorConnectorInvocation invocation, VendorParseResult parsed) { return parsed.data(); }
    }

    private static ConnectorRequest requestFor(String endpoint) {
        return new ConnectorRequest("GET", URI.create(endpoint), Map.of(), Map.of(),
                "application/json", new byte[0], Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(3), com.dataplatform.plugin.spi.IdempotencyPolicy.IDEMPOTENT,
                null, 1024);
    }
}
