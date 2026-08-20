package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.VendorParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformCoreConnectorPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PluginValidationContext validation = new DefaultPluginValidationContext(
            Clock.systemUTC(), "2.1.0", ref -> Set.of("alpha", "beta", "hmac.key").contains(ref));

    @Test
    void descriptorAndFactoriesExposeExactlyFourHostCapabilities() throws Exception {
        PlatformCoreConnectorPlugin plugin = initialized(TestPluginContexts.context());

        assertEquals(PlatformCoreConnectorMetadata.descriptor(), plugin.descriptor());
        assertEquals(Set.of(StageCapability.REQUEST_PROCESSOR, StageCapability.TRANSPORT,
                        StageCapability.RESPONSE_PROCESSOR, StageCapability.RESPONSE_NORMALIZER),
                plugin.stageFactories().stream().map(ConnectorStageFactory::capability)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void factoryValidationRejectsUnknownDirectionAndSecretReferenceDrift() throws Exception {
        PlatformCoreConnectorPlugin plugin = initialized(TestPluginContexts.context());
        ConnectorStageFactory request = factory(plugin, StageCapability.REQUEST_PROCESSOR);
        request.validate(security("REQUEST", List.of(), List.of()), validation);
        request.validate(mapper.readTree("""
                {"direction":"REQUEST","securitySteps":[
                  {"id":"first","direction":"REQUEST","stepType":"HMAC","sortNo":1,
                   "config":{"inputFrom":"BODY","secretRef":"hmac.key","algorithm":"HMAC_SHA256"}},
                  {"id":"second","direction":"REQUEST","stepType":"HMAC","sortNo":2,
                   "config":{"inputFrom":"BODY","secretRef":"hmac.key","algorithm":"HMAC_SHA256"}}
                ],"secretRefs":["hmac.key"]}
                """), validation);

        assertInvalid(request, "{\"direction\":\"REQUEST\",\"securitySteps\":[],"
                + "\"secretRefs\":[],\"unexpected\":true}");
        assertInvalid(request, "{\"direction\":\"RESPONSE\",\"securitySteps\":[],\"secretRefs\":[]}");
        assertInvalid(request, "{\"direction\":\"REQUEST\",\"securitySteps\":["
                + hmacStep("alpha") + "],\"secretRefs\":[]}");
        assertInvalid(request, "{\"direction\":\"REQUEST\",\"securitySteps\":["
                + hmacStep("alpha") + "],\"secretRefs\":[\"alpha\",\"beta\"]}");
        assertInvalid(request, "{\"direction\":\"REQUEST\",\"securitySteps\":["
                + hmacStep("alpha") + "],\"secretRefs\":[\"alpha\",\"alpha\"]}");
        assertInvalid(request, "{\"direction\":\"REQUEST\",\"securitySteps\":["
                + hmacStep("alpha") + "," + hmacStep("beta")
                + "],\"secretRefs\":[\"beta\",\"alpha\"]}");
        assertInvalid(request, "{\"direction\":\"REQUEST\",\"securitySteps\":["
                + hmacStep("missing") + "],\"secretRefs\":[\"missing\"]}");
        assertInvalid(request, """
                {"direction":"REQUEST","securitySteps":[
                  {"id":"unknown-field","direction":"REQUEST","stepType":"HMAC","sortNo":1,
                   "config":{"inputFrom":"BODY","secretRef":"hmac.key","algorithm":"HMAC_SHA256"},
                   "unexpected":true}
                ],"secretRefs":["hmac.key"]}
                """);
        assertInvalid(request, "{\"direction\":\"REQUEST\",\"securitySteps\":["
                + "{\"id\":\"bad\",\"direction\":\"REQUEST\",\"stepType\":\"HMAC\","
                + "\"sortNo\":1,\"config\":{}}],\"secretRefs\":[]}");
    }

    @Test
    void transportAndMappingConfigsAreCapabilityStrictAndCreateRejectsMismatch() throws Exception {
        PlatformCoreConnectorPlugin plugin = initialized(TestPluginContexts.context());
        ConnectorStageFactory transport = factory(plugin, StageCapability.TRANSPORT);
        transport.validate(mapper.createObjectNode(), validation);
        assertThrows(ConnectorException.class,
                () -> transport.validate(mapper.readTree("{\"unexpected\":true}"), validation));
        assertThrows(ConnectorException.class, () -> transport.create(compiled(
                StageCapability.RESPONSE_PROCESSOR, mapper.createObjectNode())));

        ConnectorStageFactory mapping = factory(plugin, StageCapability.RESPONSE_NORMALIZER);
        mapping.validate(mapper.createObjectNode(), validation);
        mapping.validate(mapper.readTree("{\"responseMapping\":null}"), validation);
        mapping.validate(mapper.readTree("{\"responseMapping\":[{\"targetField\":\"name\","
                + "\"sourcePath\":\"company.name\"}]}"), validation);
        assertThrows(ConnectorException.class, () -> mapping.validate(
                mapper.readTree("{\"responseMapping\":[]}"), validation));
    }

    @Test
    void requestSecurityMutatesRealHeadersQueryAndBodyAndResolvesOnlyDeclaredRefs() throws Exception {
        List<String> resolved = new ArrayList<>();
        PluginContext context = context(resolved, ref -> "secret-material");
        PlatformCoreConnectorPlugin plugin = initialized(context);
        JsonNode config = mapper.readTree("""
                {"direction":"REQUEST","securitySteps":[
                  {"id":"signature","direction":"REQUEST","stepType":"HMAC","sortNo":1,
                   "config":{"inputFrom":"BODY","secretRef":"hmac.key","algorithm":"HMAC_SHA256"}},
                  {"id":"header","direction":"REQUEST","stepType":"INJECT","sortNo":2,
                   "config":{"inputFrom":"RESULT.signature","location":"HEADER","fieldName":"X-Signature"}},
                  {"id":"query","direction":"REQUEST","stepType":"INJECT","sortNo":3,
                   "config":{"inputFrom":"RESULT.signature","location":"QUERY","fieldName":"sig"}},
                  {"id":"body","direction":"REQUEST","stepType":"INJECT","sortNo":4,
                   "config":{"inputFrom":"RESULT.signature","location":"BODY"}}
                ],"secretRefs":["hmac.key"]}
                """);
        ConnectorStageFactory factory = factory(plugin, StageCapability.REQUEST_PROCESSOR);
        factory.validate(config, validation);
        ConnectorStage stage = factory.create(compiled(StageCapability.REQUEST_PROCESSOR, config));
        DefaultConnectorExchange exchange = exchange();
        exchange.enter(StageCapability.REQUEST_BUILDER);
        exchange.setRequest(request("{\"name\":\"Acme\"}"));
        exchange.leave();

        exchange.enter(StageCapability.REQUEST_PROCESSOR);
        stage.execute(exchange, execution());
        exchange.leave();

        String signature = exchange.request().headers().get("X-Signature").getFirst();
        assertEquals(signature, exchange.request().query().get("sig").getFirst());
        assertArrayEquals(signature.getBytes(StandardCharsets.UTF_8), exchange.request().body());
        assertEquals(List.of("hmac.key"), resolved);
    }

    @Test
    void responseSecurityMutatesResponseHeaderAndBodyAndFailuresHaveSentSafeMessage() throws Exception {
        PlatformCoreConnectorPlugin plugin = initialized(context(new ArrayList<>(), ref -> "unused"));
        JsonNode config = mapper.readTree("""
                {"direction":"RESPONSE","securitySteps":[
                  {"id":"encoded","direction":"RESPONSE","stepType":"ENCODE","sortNo":1,
                   "config":{"inputFrom":"BODY","encoding":"BASE64"}},
                  {"id":"header","direction":"RESPONSE","stepType":"INJECT","sortNo":2,
                   "config":{"inputFrom":"RESULT.encoded","location":"HEADER","fieldName":"X-Encoded"}},
                  {"id":"body","direction":"RESPONSE","stepType":"INJECT","sortNo":3,
                   "config":{"inputFrom":"RESULT.encoded","location":"BODY"}}
                ],"secretRefs":[]}
                """);
        ConnectorStageFactory factory = factory(plugin, StageCapability.RESPONSE_PROCESSOR);
        factory.validate(config, validation);
        DefaultConnectorExchange exchange = exchange();
        exchange.enter(StageCapability.TRANSPORT);
        exchange.setRawResponse(response("{\"ok\":true}"));
        exchange.leave();
        exchange.enter(StageCapability.RESPONSE_PROCESSOR);
        factory.create(compiled(StageCapability.RESPONSE_PROCESSOR, config)).execute(exchange, execution());
        exchange.leave();
        String encoded = java.util.Base64.getEncoder().encodeToString(
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        assertEquals(encoded, exchange.rawResponse().headers().get("X-Encoded").getFirst());
        assertArrayEquals(encoded.getBytes(StandardCharsets.UTF_8), exchange.rawResponse().body());
        assertEquals(encoded.length(), exchange.rawResponse().bytesReceived());

        JsonNode failing = mapper.readTree("""
                {"direction":"RESPONSE","securitySteps":[
                  {"id":"decode","direction":"RESPONSE","stepType":"DECODE","sortNo":1,
                   "config":{"inputFrom":"BODY","encoding":"BASE64"}}
                ],"secretRefs":[]}
                """);
        factory.validate(failing, validation);
        DefaultConnectorExchange failedExchange = exchange();
        failedExchange.enter(StageCapability.TRANSPORT);
        failedExchange.setRawResponse(response("not base64 $$$ secret-material"));
        failedExchange.leave();
        failedExchange.enter(StageCapability.RESPONSE_PROCESSOR);
        ConnectorException error = assertThrows(ConnectorException.class,
                () -> factory.create(compiled(StageCapability.RESPONSE_PROCESSOR, failing))
                        .execute(failedExchange, execution()));
        assertEquals(RequestDeliveryState.SENT, error.deliveryState());
        assertEquals("PLATFORM_RESPONSE_SECURITY_ERROR", error.errorCode());
        assertFalse(error.safeMessage().contains("secret-material"));
    }

    @Test
    void requestSecretFailureIsNotSentAndDoesNotLeakSecretOrOriginalError() throws Exception {
        PlatformCoreConnectorPlugin plugin = initialized(context(new ArrayList<>(), ref -> {
            throw new IllegalStateException("secret-material resolver failure");
        }));
        JsonNode config = mapper.readTree("{\"direction\":\"REQUEST\",\"securitySteps\":["
                + hmacStep("hmac.key") + "],\"secretRefs\":[\"hmac.key\"]}");
        ConnectorStageFactory factory = factory(plugin, StageCapability.REQUEST_PROCESSOR);
        factory.validate(config, validation);
        DefaultConnectorExchange exchange = exchange();
        exchange.enter(StageCapability.REQUEST_BUILDER);
        exchange.setRequest(request("{}"));
        exchange.leave();
        exchange.enter(StageCapability.REQUEST_PROCESSOR);

        ConnectorException error = assertThrows(ConnectorException.class,
                () -> factory.create(compiled(StageCapability.REQUEST_PROCESSOR, config))
                        .execute(exchange, execution()));

        assertEquals(RequestDeliveryState.NOT_SENT, error.deliveryState());
        assertEquals("PLATFORM_REQUEST_SECURITY_ERROR", error.errorCode());
        assertFalse(error.safeMessage().contains("secret-material"));
    }

    @Test
    void parameterSecurityChangesAreSerializedBackToRequestAndResponseBodies() throws Exception {
        PlatformCoreConnectorPlugin plugin = initialized(TestPluginContexts.context());
        JsonNode requestConfig = removeFieldConfig("REQUEST");
        ConnectorStageFactory requestFactory = factory(plugin, StageCapability.REQUEST_PROCESSOR);
        requestFactory.validate(requestConfig, validation);
        DefaultConnectorExchange requestExchange = exchange();
        requestExchange.enter(StageCapability.REQUEST_BUILDER);
        requestExchange.setRequest(request("{\"keep\":\"yes\",\"remove\":\"gone\"}"));
        requestExchange.leave();
        requestExchange.enter(StageCapability.REQUEST_PROCESSOR);
        requestFactory.create(compiled(StageCapability.REQUEST_PROCESSOR, requestConfig))
                .execute(requestExchange, execution());
        requestExchange.leave();
        JsonNode requestBody = mapper.readTree(requestExchange.request().body());
        assertEquals("yes", requestBody.path("keep").asText());
        assertFalse(requestBody.has("remove"));

        JsonNode responseConfig = removeFieldConfig("RESPONSE");
        ConnectorStageFactory responseFactory = factory(plugin, StageCapability.RESPONSE_PROCESSOR);
        responseFactory.validate(responseConfig, validation);
        DefaultConnectorExchange responseExchange = exchange();
        responseExchange.enter(StageCapability.TRANSPORT);
        responseExchange.setRawResponse(response("{\"keep\":\"yes\",\"remove\":\"gone\"}"));
        responseExchange.leave();
        responseExchange.enter(StageCapability.RESPONSE_PROCESSOR);
        responseFactory.create(compiled(StageCapability.RESPONSE_PROCESSOR, responseConfig))
                .execute(responseExchange, execution());
        responseExchange.leave();
        JsonNode responseBody = mapper.readTree(responseExchange.rawResponse().body());
        assertEquals("yes", responseBody.path("keep").asText());
        assertFalse(responseBody.has("remove"));
        assertEquals(responseExchange.rawResponse().body().length,
                responseExchange.rawResponse().bytesReceived());
    }

    @Test
    void closeInvalidatesRetainedFactoriesAndEveryExistingStageWithoutSideEffects() throws Exception {
        List<String> transportCalls = new ArrayList<>();
        PlatformCoreConnectorPlugin plugin = initialized(TestPluginContexts.context((request, execution) -> {
            transportCalls.add(request.url().toString());
            return response("{}");
        }));
        JsonNode requestConfig = security("REQUEST", List.of(), List.of());
        JsonNode responseConfig = security("RESPONSE", List.of(), List.of());
        ConnectorStageFactory requestFactory = factory(plugin, StageCapability.REQUEST_PROCESSOR);
        ConnectorStageFactory transportFactory = factory(plugin, StageCapability.TRANSPORT);
        ConnectorStageFactory responseFactory = factory(plugin, StageCapability.RESPONSE_PROCESSOR);
        ConnectorStageFactory mappingFactory = factory(plugin, StageCapability.RESPONSE_NORMALIZER);
        ConnectorStage requestStage = requestFactory.create(
                compiled(StageCapability.REQUEST_PROCESSOR, requestConfig));
        ConnectorStage transportStage = transportFactory.create(
                compiled(StageCapability.TRANSPORT, mapper.createObjectNode()));
        ConnectorStage responseStage = responseFactory.create(
                compiled(StageCapability.RESPONSE_PROCESSOR, responseConfig));
        ConnectorStage mappingStage = mappingFactory.create(
                compiled(StageCapability.RESPONSE_NORMALIZER, mapper.createObjectNode()));

        DefaultConnectorExchange requestExchange = exchange();
        requestExchange.enter(StageCapability.REQUEST_BUILDER);
        requestExchange.setRequest(request("{}"));
        requestExchange.leave();
        requestExchange.enter(StageCapability.REQUEST_PROCESSOR);
        DefaultConnectorExchange transportExchange = exchange();
        transportExchange.enter(StageCapability.REQUEST_BUILDER);
        transportExchange.setRequest(request("{}"));
        transportExchange.leave();
        transportExchange.enter(StageCapability.TRANSPORT);
        DefaultConnectorExchange responseExchange = exchange();
        responseExchange.enter(StageCapability.TRANSPORT);
        responseExchange.setRawResponse(response("{}"));
        responseExchange.leave();
        responseExchange.enter(StageCapability.RESPONSE_PROCESSOR);
        DefaultConnectorExchange mappingExchange = exchange();
        mappingExchange.enter(StageCapability.RESPONSE_PARSER);
        mappingExchange.setParsedResponse(VendorParseResult.success(Map.of("value", "safe")));
        mappingExchange.leave();
        mappingExchange.enter(StageCapability.RESPONSE_NORMALIZER);

        plugin.close();

        assertClosed(() -> requestFactory.create(
                compiled(StageCapability.REQUEST_PROCESSOR, requestConfig)));
        assertClosed(() -> requestStage.execute(requestExchange, execution()));
        assertClosed(() -> transportStage.execute(transportExchange, execution()));
        assertClosed(() -> responseStage.execute(responseExchange, execution()));
        assertClosed(() -> mappingStage.execute(mappingExchange, execution()));
        assertTrue(transportCalls.isEmpty());
        assertFalse(mappingExchange.normalizedDataProduced());
    }

    @Test
    void transportAndNormalizerPreserveExistingExecutionAndPassthroughSemantics() throws Exception {
        PlatformCoreConnectorPlugin plugin = initialized(context(new ArrayList<>(), ref -> "unused"));
        DefaultConnectorExchange exchange = exchange();
        exchange.enter(StageCapability.REQUEST_BUILDER);
        exchange.setRequest(request("{}"));
        exchange.leave();
        exchange.enter(StageCapability.TRANSPORT);
        factory(plugin, StageCapability.TRANSPORT).create(compiled(
                StageCapability.TRANSPORT, mapper.createObjectNode())).execute(exchange, execution());
        exchange.leave();
        assertEquals(200, exchange.rawResponse().statusCode());

        exchange.enter(StageCapability.RESPONSE_PARSER);
        exchange.setParsedResponse(VendorParseResult.success(Map.of("company", Map.of("name", "Acme"))));
        exchange.leave();
        ConnectorStageFactory normalizer = factory(plugin, StageCapability.RESPONSE_NORMALIZER);
        exchange.enter(StageCapability.RESPONSE_NORMALIZER);
        normalizer.create(compiled(StageCapability.RESPONSE_NORMALIZER, mapper.createObjectNode()))
                .execute(exchange, execution());
        exchange.leave();
        assertEquals(Map.of("company", Map.of("name", "Acme")), exchange.normalizedData());

        JsonNode mapping = mapper.readTree("{\"responseMapping\":[{\"targetField\":\"name\","
                + "\"sourcePath\":\"company.name\"}]}" );
        DefaultConnectorExchange mapped = exchange();
        mapped.enter(StageCapability.RESPONSE_PARSER);
        mapped.setParsedResponse(VendorParseResult.success(Map.of("company", Map.of("name", "Acme"))));
        mapped.leave();
        mapped.enter(StageCapability.RESPONSE_NORMALIZER);
        normalizer.create(compiled(StageCapability.RESPONSE_NORMALIZER, mapping))
                .execute(mapped, execution());
        mapped.leave();
        assertEquals(Map.of("name", "Acme"), mapped.normalizedData());
    }

    private PlatformCoreConnectorPlugin initialized(PluginContext context) throws ConnectorException {
        PlatformCoreConnectorPlugin plugin = new PlatformCoreConnectorPlugin();
        plugin.initialize(context);
        return plugin;
    }

    private ConnectorStageFactory factory(PlatformCoreConnectorPlugin plugin, StageCapability capability) {
        return plugin.stageFactories().stream().filter(item -> item.capability() == capability)
                .findFirst().orElseThrow();
    }

    private CompiledStageConfig compiled(StageCapability capability, JsonNode config) {
        return new CompiledStageConfig("stage", PlatformCoreConnectorPlugin.PLUGIN_ID,
                PlatformCoreConnectorPlugin.VERSION, capability, config, "a".repeat(64));
    }

    private void assertInvalid(ConnectorStageFactory factory, String json) throws Exception {
        ConnectorException error = assertThrows(ConnectorException.class,
                () -> factory.validate(mapper.readTree(json), validation));
        assertEquals(RequestDeliveryState.NOT_SENT, error.deliveryState());
        assertEquals("PLATFORM_CORE_CONFIG_INVALID", error.errorCode());
    }

    private JsonNode security(String direction, List<JsonNode> steps, List<String> refs) {
        var config = mapper.createObjectNode().put("direction", direction);
        var stepArray = config.putArray("securitySteps");
        steps.forEach(stepArray::add);
        var refArray = config.putArray("secretRefs");
        refs.forEach(refArray::add);
        return config;
    }

    private JsonNode removeFieldConfig(String direction) throws Exception {
        return mapper.readTree("{\"direction\":\"" + direction + "\",\"securitySteps\":["
                + "{\"id\":\"remove\",\"direction\":\"" + direction
                + "\",\"stepType\":\"REMOVE_FIELD\",\"sortNo\":1,"
                + "\"config\":{\"location\":\"BODY_FIELD\",\"fieldName\":\"remove\"}}],"
                + "\"secretRefs\":[]}");
    }

    private void assertClosed(ThrowingOperation operation) {
        ConnectorException error = assertThrows(ConnectorException.class, operation::run);
        assertEquals("PLATFORM_CORE_CLOSED", error.errorCode());
        assertEquals(RequestDeliveryState.NOT_SENT, error.deliveryState());
    }

    private String hmacStep(String ref) {
        return "{\"id\":\"sign-" + ref + "\",\"direction\":\"REQUEST\","
                + "\"stepType\":\"HMAC\",\"sortNo\":1,\"config\":{\"inputFrom\":\"BODY\","
                + "\"secretRef\":\"" + ref + "\",\"algorithm\":\"HMAC_SHA256\"}}";
    }

    private PluginContext context(List<String> resolved, SecretSupplier supplier) {
        var base = TestPluginContexts.context((request, execution) -> response("{\"ok\":true}"));
        return new DefaultPluginContext(base.managedHttpTransport(), ref -> {
            resolved.add(ref);
            try {
                return new SecretValue(supplier.value(ref).toCharArray());
            } catch (ConnectorException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new ConnectorException(com.dataplatform.plugin.spi.ErrorCategory.AUTH_SECURITY_ERROR,
                        "SECRET_RESOLUTION_FAILED", "Secret resolution failed",
                        RequestDeliveryState.NOT_SENT, exception);
            }
        }, base.clock(), base.logger(), base.metrics(), base.objectCodec(), base.taskExecutor());
    }

    private ConnectorRequest request(String body) {
        return new ConnectorRequest("POST", URI.create("https://vendor.example/query"),
                Map.of("Existing", List.of("one")), Map.of("page", List.of("1")),
                "application/json", body.getBytes(StandardCharsets.UTF_8),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3),
                IdempotencyPolicy.IDEMPOTENT, null, 1024);
    }

    private ConnectorRawResponse response(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new ConnectorRawResponse(200, Map.of("Existing", List.of("one")), bytes,
                Duration.ofMillis(5), URI.create("https://vendor.example/query"), 2, bytes.length);
    }

    private DefaultConnectorExchange exchange() {
        return new DefaultConnectorExchange(new ConnectorExecutionRequest(
                Map.of(), "VENDOR", Instant.now().plusSeconds(30), () -> false),
                new ConnectorPipelineDefinition("1", "snapshot", List.of()));
    }

    private DefaultStageExecutionContext execution() {
        return new DefaultStageExecutionContext(Clock.systemUTC(), Instant.now().plusSeconds(30),
                () -> false, TestPluginContexts.context().logger(), new NoOpPluginMetricRecorder());
    }

    @FunctionalInterface
    private interface SecretSupplier {
        String value(String ref) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ConnectorException;
    }
}
