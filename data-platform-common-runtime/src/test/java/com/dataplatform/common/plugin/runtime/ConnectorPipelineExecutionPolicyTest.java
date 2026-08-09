package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
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
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectorPipelineExecutionPolicyTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
    private PipelineCompiler compiler;
    private ConnectorPipelineExecutor executor;

    @BeforeEach
    void setUp() {
        PolicyPlugin plugin = new PolicyPlugin();
        plugin.initialize(TestPluginContexts.context());
        registry.register(PluginHandle.builtIn(plugin));
        compiler = new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ignored -> true), mapper);
        var context = TestPluginContexts.context();
        executor = new ConnectorPipelineExecutor(Clock.systemUTC(), context.logger(), context.metrics());
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void retryEligibilityComesFromFinalRequestPolicyAndConfiguredKey() throws Exception {
        assertTrue(timeoutOutcome("GET", IdempotencyPolicy.NON_IDEMPOTENT, null)
                .requestRetryPermitted());
        assertTrue(timeoutOutcome("POST", IdempotencyPolicy.IDEMPOTENT, "platform-key")
                .requestRetryPermitted());
        assertTrue(timeoutOutcome("POST", IdempotencyPolicy.IDEMPOTENT_WITH_KEY, "platform-key")
                .requestRetryPermitted());
        assertFalse(timeoutOutcome("POST", IdempotencyPolicy.IDEMPOTENT, null)
                .requestRetryPermitted());
        assertFalse(timeoutOutcome("POST", IdempotencyPolicy.NON_IDEMPOTENT, "plugin-key")
                .requestRetryPermitted());
    }

    @Test
    void businessRejectionKeepsSafeEvidenceAndSeparateTransportStatus() throws Exception {
        try (CompiledConnectorPipeline pipeline = compiler.compile(pipeline(
                requestConfig("POST", IdempotencyPolicy.NON_IDEMPOTENT, null), false, true))) {
            var outcome = executor.executeWithOutcome(pipeline, request());
            var result = outcome.result();

            assertEquals(TransportStatus.SUCCESS, result.transportStatus());
            assertEquals(BusinessStatus.REJECTED, result.businessStatus());
            assertEquals(ErrorCategory.BUSINESS_REJECTED, result.errorCategory());
            assertEquals("VENDOR_DENIED", result.errorCode());
            assertEquals("Vendor rejected by policy", result.safeMessage());
            assertEquals(BillingSignal.INELIGIBLE, result.billingSignal());
            assertEquals(CacheSignal.NOT_CACHEABLE, result.cacheSignal());
            assertEquals(Map.of("accepted", false), result.normalizedData());
            assertFalse(result.successful());
        }
    }

    @Test
    void requestBuildFailureIsNotReportedAsATransportFailure() throws Exception {
        JsonNode config = requestConfig("POST", IdempotencyPolicy.NON_IDEMPOTENT, null);
        ((com.fasterxml.jackson.databind.node.ObjectNode) config).put("failBuild", true);
        try (CompiledConnectorPipeline pipeline = compiler.compile(pipeline(config, false, false))) {
            var result = executor.execute(pipeline, request());

            assertEquals(TransportStatus.NOT_ATTEMPTED, result.transportStatus());
            assertEquals(ErrorCategory.REQUEST_BUILD_ERROR, result.errorCategory());
            assertEquals(RequestDeliveryState.NOT_SENT, result.deliveryState());
        }
    }

    @Test
    void pluginSafeMessageIsSanitizedBeforeItReachesExecutionResult() throws Exception {
        String secret = "resolved-secret-123456";
        JsonNode config = requestConfig("GET", IdempotencyPolicy.IDEMPOTENT, null);
        ((com.fasterxml.jackson.databind.node.ObjectNode) config).put("leakMessage",
                "Authorization: Bearer token-value password=hunter2 response="
                        + secret + " " + "x".repeat(1_000));
        var context = TestPluginContexts.context();
        ConnectorStageSecretScope scope = new ConnectorStageSecretScope() {
            @Override public void enter(JsonNode ignored) { }
            @Override public void leave() { }
            @Override public Iterable<String> sensitiveValues() { return List.of(secret); }
        };
        executor = new ConnectorPipelineExecutor(
                Clock.systemUTC(), context.logger(), context.metrics(), scope);

        try (CompiledConnectorPipeline pipeline = compiler.compile(pipeline(config, true, false))) {
            var result = executor.execute(pipeline, request());
            assertFalse(result.safeMessage().contains(secret));
            assertFalse(result.safeMessage().contains("token-value"));
            assertFalse(result.safeMessage().contains("hunter2"));
            assertTrue(result.safeMessage().contains("[REDACTED]"));
            assertTrue(result.safeMessage().length() <= ConnectorSafeMessageSanitizer.MAX_SAFE_MESSAGE_LENGTH);
        }
    }

    private ConnectorPipelineExecutionOutcome timeoutOutcome(
            String method, IdempotencyPolicy policy, String key) throws Exception {
        try (CompiledConnectorPipeline pipeline = compiler.compile(
                pipeline(requestConfig(method, policy, key), true, false))) {
            ConnectorPipelineExecutionOutcome outcome = executor.executeWithOutcome(pipeline, request());
            assertEquals(ErrorCategory.TRANSPORT_TIMEOUT, outcome.result().errorCategory());
            return outcome;
        }
    }

    private ConnectorExecutionRequest request() {
        return new ConnectorExecutionRequest(Map.of("id", "1"), "DEMO",
                Instant.now().plusSeconds(5), () -> false);
    }

    private JsonNode requestConfig(String method, IdempotencyPolicy policy, String key) {
        var config = mapper.createObjectNode()
                .put("method", method)
                .put("idempotencyPolicy", policy.name());
        if (key != null) config.put("idempotencyKey", key);
        return config;
    }

    private ConnectorPipelineDefinition pipeline(JsonNode requestConfig, boolean timeout, boolean reject) {
        var transportConfig = mapper.createObjectNode().put("timeout", timeout);
        if (requestConfig.has("leakMessage")) {
            transportConfig.put("message", requestConfig.path("leakMessage").asText());
        }
        var normalizerConfig = mapper.createObjectNode().put("reject", reject);
        var stages = new java.util.ArrayList<ConnectorStageDefinition>();
        stages.add(stage("builder", StageCapability.REQUEST_BUILDER, 0, requestConfig));
        stages.add(stage("transport", StageCapability.TRANSPORT, 1, transportConfig));
        if (!timeout) {
            stages.add(stage("normalizer", StageCapability.RESPONSE_NORMALIZER, 2, normalizerConfig));
        }
        return new ConnectorPipelineDefinition("1", "snapshot", stages);
    }

    private ConnectorStageDefinition stage(
            String key, StageCapability capability, int order, JsonNode config) {
        return new ConnectorStageDefinition(key, capability, PolicyPlugin.ID, PolicyPlugin.VERSION,
                order, true, config, compiler.sha256(config));
    }

    private static final class PolicyPlugin implements ConnectorPlugin {
        private static final String ID = "policy-plugin";
        private static final String VERSION = "1.0.0";

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(ID, VERSION, "1.0", "Policy", "test", Set.of(
                    StageCapability.REQUEST_BUILDER,
                    StageCapability.TRANSPORT,
                    StageCapability.RESPONSE_NORMALIZER));
        }
        @Override public void initialize(PluginContext context) { }
        @Override public List<ConnectorStageFactory> stageFactories() {
            return List.of(new Factory(StageCapability.REQUEST_BUILDER),
                    new Factory(StageCapability.TRANSPORT),
                    new Factory(StageCapability.RESPONSE_NORMALIZER));
        }
        @Override public PluginSelfTestResult selfTest() { return PluginSelfTestResult.success(); }
        @Override public void close() { }
    }

    private static final class Factory implements ConnectorStageFactory {
        private final StageCapability capability;
        private Factory(StageCapability capability) { this.capability = capability; }
        @Override public StageCapability capability() { return capability; }
        @Override public void validate(JsonNode config, PluginValidationContext context) { }
        @Override public ConnectorStage create(CompiledStageConfig config) {
            return switch (capability) {
                case REQUEST_BUILDER -> new BuilderStage(config.config());
                case TRANSPORT -> new TransportStage(config.config());
                case RESPONSE_NORMALIZER -> new NormalizerStage(config.config());
                default -> throw new IllegalStateException("Unexpected capability");
            };
        }
    }

    private record BuilderStage(JsonNode config) implements ConnectorStage {
        @Override public StageCapability capability() { return StageCapability.REQUEST_BUILDER; }
        @Override public void execute(ConnectorExchange exchange, StageExecutionContext context)
                throws ConnectorException {
            if (config.path("failBuild").asBoolean()) {
                throw new ConnectorException(ErrorCategory.REQUEST_BUILD_ERROR, "INVALID_INPUT",
                        "Vendor request could not be built", RequestDeliveryState.NOT_SENT);
            }
            IdempotencyPolicy policy = IdempotencyPolicy.valueOf(config.path("idempotencyPolicy").asText());
            String key = config.has("idempotencyKey") ? config.path("idempotencyKey").asText() : null;
            exchange.setRequest(new ConnectorRequest(config.path("method").asText(),
                    URI.create("https://api.example.com/query"), Map.of(), Map.of(), "application/json",
                    "{}".getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(1), Duration.ofSeconds(1),
                    Duration.ofSeconds(2), policy, key, 1024));
        }
    }

    private record TransportStage(JsonNode config) implements ConnectorStage {
        @Override public StageCapability capability() { return StageCapability.TRANSPORT; }
        @Override public void execute(ConnectorExchange exchange, StageExecutionContext context)
                throws ConnectorException {
            if (config.path("timeout").asBoolean()) {
                throw new ConnectorException(ErrorCategory.TRANSPORT_TIMEOUT, "VENDOR_TIMEOUT",
                        config.path("message").asText("Vendor request timed out"),
                        RequestDeliveryState.MAYBE_SENT);
            }
            exchange.setRawResponse(new ConnectorRawResponse(200, Map.of(), "{}".getBytes(StandardCharsets.UTF_8),
                    Duration.ofMillis(2), URI.create("https://api.example.com/query"), 2, 2));
        }
    }

    private record NormalizerStage(JsonNode config) implements ConnectorStage {
        @Override public StageCapability capability() { return StageCapability.RESPONSE_NORMALIZER; }
        @Override public void execute(ConnectorExchange exchange, StageExecutionContext context)
                throws ConnectorException {
            exchange.setNormalizedData(Map.of("accepted", false));
            exchange.setBusinessStatus(BusinessStatus.REJECTED);
            exchange.setBillingSignal(BillingSignal.INELIGIBLE);
            exchange.setCacheSignal(CacheSignal.NOT_CACHEABLE);
            if (config.path("reject").asBoolean()) {
                throw new ConnectorException(ErrorCategory.BUSINESS_REJECTED, "VENDOR_DENIED",
                        "Vendor rejected by policy", RequestDeliveryState.SENT);
            }
        }
    }
}
