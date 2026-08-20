package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.CancellationToken;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.Deadline;
import com.dataplatform.plugin.spi.IdempotencyContext;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.ManagedTransportSession;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
import com.dataplatform.plugin.spi.VendorConnectorStageAdapters;
import com.dataplatform.plugin.spi.VendorParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GenericHttpConnectorPluginTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);
    private static final PluginLogger LOGGER = new PluginLogger() {
        @Override public void debug(String event, Map<String, ?> safeFields) { }
        @Override public void info(String event, Map<String, ?> safeFields) { }
        @Override public void warn(String event, Map<String, ?> safeFields) { }
        @Override public void error(String event, Map<String, ?> safeFields) { }
    };
    private static final PluginMetricRecorder METRICS = new PluginMetricRecorder() {
        @Override public void increment(String metric, Map<String, String> tags) { }
        @Override public void recordDuration(String metric, Duration duration, Map<String, String> tags) { }
    };

    @Test
    void metadataIsCanonicalV2AndCarriesNoDirectNetworkAuthority() throws Exception {
        var manifest = new PluginManifestReader(new ObjectMapper()).read(
                GenericHttpConnectorMetadata.canonicalManifestJson().getBytes());

        assertEquals("generic-http", manifest.pluginId());
        assertEquals("2.0.0", manifest.version());
        assertEquals(GenericHttpConnectorMetadata.CAPABILITIES, manifest.capabilities());
        assertTrue(manifest.permissions().networkProtocols().isEmpty());
        assertTrue(manifest.permissions().networkHosts().isEmpty());
        assertEquals(64, GenericHttpConnectorMetadata.artifactSha256().length());
        assertEquals(64, GenericHttpConnectorMetadata.manifestSha256().length());
        assertEquals(64, GenericHttpConnectorMetadata.schemaSha256().length());
        assertEquals(GenericHttpConnectorMetadata.canonicalManifestJson(),
                new String(new PluginManifestReader(new ObjectMapper()).canonicalize(
                        GenericHttpConnectorMetadata.canonicalManifestJson().getBytes())));
        assertFalse(GenericHttpConnectorMetadata.configSchema().path("additionalProperties").asBoolean(true));
        assertFalse(GenericHttpConnectorMetadata.configSchema().at(
                "/properties/auth/additionalProperties").asBoolean(true));
        assertTrue(GenericHttpConnectorMetadata.configSchema().at("/properties/auth/allOf").isArray());
        assertEquals(GenericHttpConnectorMetadata.metadata(), GenericHttpConnectorMetadata.metadata());
        assertEquals("{\"dataTypeCodes\":[\"*\"],\"vendorCodes\":[\"*\"]}",
                GenericHttpConnectorMetadata.canonicalCompatibilityJson());
        assertEquals(GenericHttpConnectorMetadata.CAPABILITIES,
                new GenericHttpConnectorPlugin().stageFactories().stream()
                        .map(factory -> factory.capability()).collect(Collectors.toSet()));
    }

    @Test
    void validatorRejectsConditionalUnknownSecretAndUnsafePaths() throws Exception {
        JsonNode bearerWithoutRef = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"BEARER"}}
                """);
        assertThrows(IllegalArgumentException.class,
                () -> GenericHttpConnectorConfigValidator.validate(bearerWithoutRef, ignored -> true));

        JsonNode unknown = config("NONE").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) unknown).put("timeout", 1000);
        assertThrows(IllegalArgumentException.class,
                () -> GenericHttpConnectorConfigValidator.validate(unknown, ignored -> true));

        JsonNode pollution = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"NONE"},
                 "requestMapping":[{"sourceField":"user.__proto__","targetField":"x"}]}
                """);
        assertThrows(IllegalArgumentException.class,
                () -> GenericHttpConnectorConfigValidator.validate(pollution, ignored -> true));

        JsonNode unowned = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET",
                 "auth":{"type":"BEARER","tokenRef":"vendor.token"}}
                """);
        assertThrows(IllegalArgumentException.class,
                () -> GenericHttpConnectorConfigValidator.validate(unowned, ignored -> false));
        assertThrows(IllegalArgumentException.class,
                () -> GenericHttpConnectorConfigValidator.validate(unowned, ignored -> { throw new RuntimeException(); }));

        JsonNode plaintextCredentialHeader = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"NONE"},
                 "headers":[{"name":"X-Api-Key","value":"plaintext-secret"}]}
                """);
        assertThrows(IllegalArgumentException.class, () -> GenericHttpConnectorConfigValidator.validate(
                plaintextCredentialHeader, ignored -> true));
        JsonNode ordinaryHeaders = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"NONE"},
                 "headers":[{"name":"Accept","value":"application/json"},{"name":"X-Client-Version","value":"2"}]}
                """);
        assertEquals(2, GenericHttpConnectorConfigValidator.validate(ordinaryHeaders, ignored -> true)
                .headers().size());
        JsonNode rawQuery = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api?token=plaintext","method":"GET","auth":{"type":"NONE"}}
                """);
        assertThrows(IllegalArgumentException.class,
                () -> GenericHttpConnectorConfigValidator.validate(rawQuery, ignored -> true));
        JsonNode encodedControl = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api%0d%0aInjected","method":"GET",
                 "auth":{"type":"NONE"}}
                """);
        assertThrows(IllegalArgumentException.class,
                () -> GenericHttpConnectorConfigValidator.validate(encodedControl, ignored -> true));
    }

    @Test
    void buildsGetQueryAndPostBodiesWithHostGovernedDeadlineAndIdempotency() throws Exception {
        JsonNode get = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"NONE"},
                 "headers":[{"name":"X-Fixed","value":"fixed"}],
                 "requestMapping":[{"sourceField":"name","targetField":"vendorName","required":true,"transformType":"uppercase"}]}
                """);
        TestExchange exchange = executeBuild(get,
                MAPPER.readTree("{\"name\":\" acme \",\"tags\":[\"a\",\"b\"],\"extra\":1}"), Map.of());
        assertEquals("ACME", exchange.request.query().get("vendorName").getFirst().trim());
        assertEquals(List.of("a", "b"), exchange.request.query().get("tags"));
        assertEquals(List.of("fixed"), exchange.request.headers().get("X-Fixed"));
        assertEquals(IdempotencyPolicy.IDEMPOTENT, exchange.request.idempotencyPolicy());
        assertEquals(Duration.ofSeconds(30), exchange.request.totalTimeout());
        assertEquals(10L * 1024 * 1024, exchange.request.maxResponseBytes());

        JsonNode head = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"HEAD","auth":{"type":"NONE"}}
                """);
        TestExchange headExchange = executeBuild(head, MAPPER.readTree("{\"q\":\"value\"}"), Map.of());
        assertEquals(List.of("value"), headExchange.request.query().get("q"));
        assertEquals(0, headExchange.request.body().length);
        assertEquals(IdempotencyPolicy.IDEMPOTENT, headExchange.request.idempotencyPolicy());

        JsonNode json = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"POST","auth":{"type":"NONE"}}
                """);
        TestExchange jsonExchange = executeBuild(json, MAPPER.readTree("{\"b\":2,\"a\":1}"), Map.of());
        assertEquals("{\"a\":1,\"b\":2}", new String(jsonExchange.request.body()));
        assertEquals(IdempotencyPolicy.NON_IDEMPOTENT, jsonExchange.request.idempotencyPolicy());
        assertEquals(null, jsonExchange.request.idempotencyKey());

        JsonNode form = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"DELETE",
                 "contentType":"application/x-www-form-urlencoded","auth":{"type":"NONE"}}
                """);
        TestExchange formExchange = executeBuild(form, MAPPER.readTree("{\"q\":\"a b\",\"x\":[1,2]}"), Map.of());
        assertEquals("q=a+b&x=1&x=2", new String(formExchange.request.body()));
        assertEquals(IdempotencyPolicy.NON_IDEMPOTENT, formExchange.request.idempotencyPolicy());
    }

    @Test
    void requestMappingsReadImmutableSourceRemainOrderIndependentAndPassThroughUnmappedFields()
            throws Exception {
        JsonNode first = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"NONE"},
                 "requestMapping":[{"sourceField":"a","targetField":"x"},{"sourceField":"x","targetField":"y"}]}
                """);
        JsonNode reversed = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"NONE"},
                 "requestMapping":[{"sourceField":"x","targetField":"y"},{"sourceField":"a","targetField":"x"}]}
                """);
        JsonNode input = MAPPER.readTree("{\"a\":\"from-a\",\"x\":\"from-x\",\"extra\":\"kept\"}");

        Map<String, List<String>> firstQuery = executeBuild(first, input, Map.of()).request.query();
        Map<String, List<String>> reversedQuery = executeBuild(reversed, input, Map.of()).request.query();

        assertEquals(firstQuery, reversedQuery);
        assertEquals(List.of("from-a"), firstQuery.get("x"));
        assertEquals(List.of("from-x"), firstQuery.get("y"));
        assertEquals(List.of("kept"), firstQuery.get("extra"));
        assertFalse(firstQuery.containsKey("a"));
    }

    @Test
    void appliesExactAuthSecretsWithoutLeakingThem() throws Exception {
        Map<String, String> secrets = Map.of("token", "top-secret", "user", "alice", "pass", "pwd", "key", "k-secret");
        JsonNode bearer = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET",
                 "auth":{"type":"BEARER","tokenRef":"token"}}
                """);
        TestExchange bearerExchange = executeBuildAndProcess(bearer, MAPPER.createObjectNode(), secrets);
        assertEquals("Bearer top-secret", bearerExchange.request.headers().get("Authorization").getFirst());

        JsonNode basic = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET",
                 "auth":{"type":"BASIC","usernameRef":"user","passwordRef":"pass"}}
                """);
        assertTrue(executeBuildAndProcess(basic, MAPPER.createObjectNode(), secrets)
                .request.headers().get("Authorization").getFirst().startsWith("Basic "));

        JsonNode apiKey = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET",
                 "auth":{"type":"API_KEY","keyName":"api_key","keyRef":"key","location":"query"}}
                """);
        assertEquals(List.of("k-secret"), executeBuildAndProcess(apiKey, MAPPER.createObjectNode(), secrets)
                .request.query().get("api_key"));
        assertThrows(ConnectorException.class, () -> executeBuildAndProcess(
                apiKey, MAPPER.readTree("{\"api_key\":\"existing\"}"), secrets));

        JsonNode apiKeyHeader = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET",
                 "auth":{"type":"API_KEY","keyName":"X-Api-Key","keyRef":"key","location":"header"}}
                """);
        assertEquals(List.of("k-secret"), executeBuildAndProcess(
                apiKeyHeader, MAPPER.createObjectNode(), secrets).request.headers().get("X-Api-Key"));

        ConnectorException failure = assertThrows(ConnectorException.class,
                () -> executeBuildAndProcess(bearer, MAPPER.createObjectNode(), Map.of()));
        assertEquals(RequestDeliveryState.NOT_SENT, failure.deliveryState());
        assertFalse(failure.safeMessage().contains("token"));
        assertFalse(failure.safeMessage().contains("top-secret"));
    }

    @Test
    void cancellationIsPreservedAndNeverRewrittenAsAuthenticationFailure() throws Exception {
        JsonNode config = config("NONE");
        GenericHttpConnectorPlugin plugin = new GenericHttpConnectorPlugin();
        TestExchange exchange = new TestExchange();
        execute(plugin, StageCapability.REQUEST_BUILDER, config, MAPPER.createObjectNode(), Map.of(), exchange);

        ConnectorException cancelled = assertThrows(ConnectorException.class, () -> execute(
                plugin, StageCapability.REQUEST_PROCESSOR, config, MAPPER.createObjectNode(),
                Map.of(), exchange, true));

        assertEquals("REQUEST_CANCELLED", cancelled.errorCode());
        assertEquals(RequestDeliveryState.NOT_SENT, cancelled.deliveryState());
        assertNotEquals("GENERIC_HTTP_AUTH_ERROR", cancelled.errorCode());
    }

    @Test
    void expiredDeadlineFailsBeforeARequestCanBeBuilt() throws Exception {
        GenericHttpConnectorPlugin plugin = new GenericHttpConnectorPlugin();
        ConnectorException expired = assertThrows(ConnectorException.class, () -> execute(
                plugin, StageCapability.REQUEST_BUILDER, config("NONE"), MAPPER.createObjectNode(),
                Map.of(), new TestExchange(), false, Duration.ZERO));

        assertEquals("EXECUTION_DEADLINE_EXCEEDED", expired.errorCode());
        assertEquals(RequestDeliveryState.NOT_SENT, expired.deliveryState());
    }

    @Test
    void separatesHttpBusinessAndParseFailuresAndHardensXml() throws Exception {
        JsonNode config = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"NONE"},
                 "businessCodePath":"meta.code","successBusinessCodes":["OK"],"dataPath":"data"}
                """);
        ConnectorException http = assertThrows(ConnectorException.class,
                () -> executeParse(config, 503, "application/json", "{}"));
        assertEquals("GENERIC_HTTP_STATUS_ERROR", http.errorCode());
        assertEquals(RequestDeliveryState.SENT, http.deliveryState());

        VendorParseResult rejected = executeParse(config, 200, "application/json",
                "{\"meta\":{\"code\":\"NO\"},\"data\":{\"ok\":false}}" );
        assertEquals(BusinessStatus.REJECTED, rejected.businessStatus());
        assertEquals(BillingSignal.INELIGIBLE, rejected.billingSignal());
        assertEquals(CacheSignal.NOT_CACHEABLE, rejected.cacheSignal());

        VendorParseResult success = executeParse(config, 200, "application/json",
                "{\"meta\":{\"code\":\"OK\"},\"data\":{\"ok\":true}}" );
        assertEquals(Boolean.TRUE, success.data().get("ok"));

        JsonNode xmlConfig = config("NONE");
        VendorParseResult xml = executeParse(xmlConfig, 200, "application/xml",
                "<root><name>acme</name><items><id>1</id></items></root>");
        assertEquals("acme", xml.data().get("name"));
        assertThrows(ConnectorException.class, () -> executeParse(xmlConfig, 200, "application/xml",
                "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><root><name>&e;</name></root>"));
        assertThrows(ConnectorException.class, () -> executeParse(xmlConfig, 200, "text/plain", "value"));
        assertThrows(ConnectorException.class,
                () -> executeParse(xmlConfig, 200, "application/jsonp", "{}"));
        assertThrows(ConnectorException.class, () -> executeParse(xmlConfig, 200, "application/json", "[]"));
        assertThrows(ConnectorException.class, () -> executeParse(xmlConfig, 200, "application/json", ""));
        JsonNode missingPath = MAPPER.readTree("""
                {"endpoint":"https://vendor.example/api","method":"GET","auth":{"type":"NONE"},
                 "dataPath":"missing"}
                """);
        assertThrows(ConnectorException.class,
                () -> executeParse(missingPath, 200, "application/json", "{\"ok\":true}"));
        byte[] tooLarge = new byte[(int) GenericHttpConnectorConfigValidator.MAX_RESPONSE_BYTES + 1];
        assertThrows(ConnectorException.class,
                () -> executeParse(xmlConfig, 200, "application/json", tooLarge));
    }

    private TestExchange executeBuild(JsonNode config, JsonNode input, Map<String, String> secrets) throws Exception {
        GenericHttpConnectorPlugin plugin = new GenericHttpConnectorPlugin();
        TestExchange exchange = new TestExchange();
        execute(plugin, StageCapability.REQUEST_BUILDER, config, input, secrets, exchange);
        return exchange;
    }

    private TestExchange executeBuildAndProcess(JsonNode config, JsonNode input, Map<String, String> secrets)
            throws Exception {
        GenericHttpConnectorPlugin plugin = new GenericHttpConnectorPlugin();
        TestExchange exchange = new TestExchange();
        execute(plugin, StageCapability.REQUEST_BUILDER, config, input, secrets, exchange);
        execute(plugin, StageCapability.REQUEST_PROCESSOR, config, input, secrets, exchange);
        return exchange;
    }

    private VendorParseResult executeParse(JsonNode config, int status, String contentType, String body)
            throws Exception {
        return executeParse(config, status, contentType, body.getBytes());
    }

    private VendorParseResult executeParse(JsonNode config, int status, String contentType, byte[] body)
            throws Exception {
        GenericHttpConnectorPlugin plugin = new GenericHttpConnectorPlugin();
        TestExchange exchange = new TestExchange();
        exchange.rawResponse = new ConnectorRawResponse(status, Map.of("Content-Type", List.of(contentType)),
                body, Duration.ofMillis(5), URI.create("https://vendor.example/api"), 0, body.length);
        execute(plugin, StageCapability.RESPONSE_PARSER, config, MAPPER.createObjectNode(), Map.of(), exchange);
        return (VendorParseResult) exchange.parsedResponse;
    }

    private void execute(GenericHttpConnectorPlugin plugin, StageCapability capability, JsonNode config,
                         JsonNode input, Map<String, String> secrets, TestExchange exchange) throws Exception {
        execute(plugin, capability, config, input, secrets, exchange, false);
    }

    private void execute(GenericHttpConnectorPlugin plugin, StageCapability capability, JsonNode config,
                         JsonNode input, Map<String, String> secrets, TestExchange exchange,
                         boolean cancelled) throws Exception {
        execute(plugin, capability, config, input, secrets, exchange, cancelled, Duration.ofSeconds(30));
    }

    private void execute(GenericHttpConnectorPlugin plugin, StageCapability capability, JsonNode config,
                         JsonNode input, Map<String, String> secrets, TestExchange exchange,
                         boolean cancelled, Duration remaining) throws Exception {
        var factory = plugin.stageFactories().stream().filter(item -> item.capability() == capability)
                .findFirst().orElseThrow();
        var stage = factory.create(new CompiledStageConfig("test", GenericHttpConnectorMetadata.PLUGIN_ID,
                GenericHttpConnectorMetadata.VERSION, capability, config, "hash"));
        stage.execute(exchange, host(config, input, secrets, cancelled, remaining));
    }

    private TestHostContext host(
            JsonNode config, JsonNode input, Map<String, String> secrets,
            boolean cancelled, Duration remaining) {
        Deadline deadline = new Deadline() {
            @Override public Instant expiresAt() { return CLOCK.instant().plus(remaining); }
            @Override public Duration remaining() { return remaining; }
            @Override public boolean isExpired() { return remaining.isZero() || remaining.isNegative(); }
        };
        CancellationToken cancellation = new CancellationToken() {
            @Override public boolean isCancelled() { return cancelled; }
            @Override public void throwIfCancelled() throws ConnectorException {
                if (cancelled) throw new ConnectorException(
                        com.dataplatform.plugin.spi.ErrorCategory.PLUGIN_INTERNAL_ERROR,
                        "REQUEST_CANCELLED", "Connector execution was cancelled",
                        RequestDeliveryState.NOT_SENT);
            }
        };
        IdempotencyContext idempotency = new HostIdempotencyContext(IdempotencyPolicy.IDEMPOTENT, null, true);
        VendorConnectorInvocation invocation = VendorConnectorInvocation.immutable(
                "request-1", 7L, input, config, deadline, cancellation, 1, idempotency,
                ref -> {
                    String value = secrets.get(ref);
                    if (value == null) throw new ConnectorException(
                            com.dataplatform.plugin.spi.ErrorCategory.AUTH_SECURITY_ERROR,
                            "SECRET_UNAVAILABLE", "Secret is unavailable", RequestDeliveryState.NOT_SENT);
                    return new SecretValue(value.toCharArray());
                }, new JacksonObjectCodec(MAPPER), CLOCK, LOGGER, METRICS);
        return new TestHostContext(invocation, deadline, cancelled);
    }

    private JsonNode config(String authType) throws Exception {
        return MAPPER.readTree("{\"endpoint\":\"https://vendor.example/api\",\"method\":\"GET\",\"auth\":{\"type\":\""
                + authType + "\"}}");
    }

    private record TestHostContext(
            VendorConnectorInvocation vendorInvocation, Deadline connectorDeadline, boolean cancelled)
            implements VendorConnectorStageAdapters.HostContext {
        @Override public ManagedTransportSession managedTransportSession() { return null; }
        @Override public Clock clock() { return CLOCK; }
        @Override public Instant deadline() { return connectorDeadline.expiresAt(); }
        @Override public boolean cancellationRequested() { return cancelled; }
        @Override public PluginLogger logger() { return LOGGER; }
        @Override public PluginMetricRecorder metrics() { return METRICS; }
    }

    private static final class TestExchange implements ConnectorExchange {
        private ConnectorRequest request;
        private ConnectorRawResponse rawResponse;
        private Object parsedResponse;
        private Map<String, Object> normalized = Map.of();
        @Override public Map<String, Object> standardParameters() { return Map.of(); }
        @Override public String vendorCode() { return "vendor"; }
        @Override public String pipelineVersion() { return "1"; }
        @Override public String snapshotHash() { return "hash"; }
        @Override public Instant deadline() { return CLOCK.instant().plusSeconds(30); }
        @Override public boolean cancellationRequested() { return false; }
        @Override public ConnectorRequest request() { return request; }
        @Override public ConnectorRawResponse rawResponse() { return rawResponse; }
        @Override public Object parsedResponse() { return parsedResponse; }
        @Override public Map<String, Object> normalizedData() { return normalized; }
        @Override public Map<String, Object> completedStageOutputs() { return Map.of(); }
        @Override public void setRequest(ConnectorRequest value) { request = value; }
        @Override public void setRawResponse(ConnectorRawResponse value) { rawResponse = value; }
        @Override public void setParsedResponse(Object value) { parsedResponse = value; }
        @Override public void setNormalizedData(Map<String, Object> value) { normalized = value; }
        @Override public void recordStageOutput(String key, Object value) { }
        @Override public void setBusinessStatus(BusinessStatus status) { }
        @Override public void setBillingSignal(BillingSignal signal) { }
        @Override public void setCacheSignal(CacheSignal signal) { }
    }
}
