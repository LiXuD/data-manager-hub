package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
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
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConnectorPipelineAuditIdentityTest {

    private static final String VENDOR_ID = "vendor-simple";
    private static final String VENDOR_VERSION = "2.0.0";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorPluginRegistry registry = new ConnectorPluginRegistry();

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void simpleHostSingleAlwaysAttributesSuccessAndEveryFailureToVendorPlugin() throws Exception {
        register(new TestVendorPlugin());
        registerPlatformCore(Failure.NONE);
        assertVendorIdentity(executeSimple(Failure.NONE));
        assertVendorIdentity(executeSimple(Failure.BUILDER));
        assertVendorIdentity(executeSimple(Failure.TRANSPORT));
        assertVendorIdentity(executeSimple(Failure.HTTP));
        assertVendorIdentity(executeSimple(Failure.RESPONSE));
        assertVendorIdentity(executeSimple(Failure.BUSINESS));
    }

    @Test
    void legacySuccessAndFailureKeepTransportAndCurrentStageAttribution() throws Exception {
        TestPipelinePlugin legacy = new TestPipelinePlugin("legacy", "1.0.0");
        register(legacy);

        var success = executeLegacy(legacy, Failure.NONE);
        assertEquals("legacy", success.pluginId());
        assertEquals("1.0.0", success.pluginVersion());

        var failure = executeLegacy(legacy, Failure.RESPONSE);
        assertEquals("legacy", failure.pluginId());
        assertEquals("1.0.0", failure.pluginVersion());
    }

    @Test
    void matchingStageKeyWithoutAbstractVendorPluginDoesNotSpoofVendorIdentity() throws Exception {
        TestPipelinePlugin fakeVendor = new TestPipelinePlugin("fake-vendor", "9.0.0");
        register(fakeVendor);
        registerPlatformCore();
        try (CompiledConnectorPipeline pipeline = compile(List.of(
                stage("connector.request-builder", StageCapability.REQUEST_BUILDER,
                        fakeVendor.descriptor().pluginId(), fakeVendor.descriptor().version(), 1,
                        config(Failure.NONE)),
                stage("platform.transport", StageCapability.TRANSPORT,
                        PlatformCoreConnectorMetadata.PLUGIN_ID,
                        PlatformCoreConnectorMetadata.VERSION, 2, mapper.createObjectNode()),
                stage("platform.response-normalizer", StageCapability.RESPONSE_NORMALIZER,
                        PlatformCoreConnectorMetadata.PLUGIN_ID,
                        PlatformCoreConnectorMetadata.VERSION, 3,
                        mapper.createObjectNode().putNull("responseMapping"))))) {
            var result = executor().execute(pipeline, request());
            assertEquals(PlatformCoreConnectorMetadata.PLUGIN_ID, result.pluginId());
            assertEquals(PlatformCoreConnectorMetadata.VERSION, result.pluginVersion());
        }
    }

    private com.dataplatform.plugin.spi.ConnectorExecutionResult executeSimple(Failure failure)
            throws Exception {
        platformFailure = failure;
        List<ConnectorStageDefinition> steps = new ArrayList<>();
        steps.add(stage("connector.request-builder", StageCapability.REQUEST_BUILDER,
                VENDOR_ID, VENDOR_VERSION, 1, config(failure)));
        steps.add(stage("platform.transport", StageCapability.TRANSPORT,
                PlatformCoreConnectorMetadata.PLUGIN_ID, PlatformCoreConnectorMetadata.VERSION,
                2, mapper.createObjectNode()));
        if (failure == Failure.RESPONSE) {
            steps.add(stage("platform.response-processor", StageCapability.RESPONSE_PROCESSOR,
                    PlatformCoreConnectorMetadata.PLUGIN_ID, PlatformCoreConnectorMetadata.VERSION,
                    3, responseFailureConfig()));
        }
        steps.add(stage("connector.response-parser", StageCapability.RESPONSE_PARSER,
                VENDOR_ID, VENDOR_VERSION, 4, config(failure)));
        steps.add(stage("platform.response-normalizer", StageCapability.RESPONSE_NORMALIZER,
                PlatformCoreConnectorMetadata.PLUGIN_ID, PlatformCoreConnectorMetadata.VERSION,
                5, mapper.createObjectNode().putNull("responseMapping")));
        try (CompiledConnectorPipeline pipeline = compile(steps)) {
            return executor().execute(pipeline, request());
        }
    }

    private com.dataplatform.plugin.spi.ConnectorExecutionResult executeLegacy(
            TestPipelinePlugin plugin, Failure failure) throws Exception {
        try (CompiledConnectorPipeline pipeline = compile(List.of(
                stage("builder", StageCapability.REQUEST_BUILDER,
                        plugin.descriptor().pluginId(), plugin.descriptor().version(), 1,
                        config(Failure.NONE)),
                stage("transport", StageCapability.TRANSPORT,
                        plugin.descriptor().pluginId(), plugin.descriptor().version(), 2,
                        config(Failure.NONE)),
                stage("normalizer", StageCapability.RESPONSE_NORMALIZER,
                        plugin.descriptor().pluginId(), plugin.descriptor().version(), 3,
                        config(failure))))) {
            return executor().execute(pipeline, request());
        }
    }

    private void assertVendorIdentity(com.dataplatform.plugin.spi.ConnectorExecutionResult result) {
        assertEquals(VENDOR_ID, result.pluginId());
        assertEquals(VENDOR_VERSION, result.pluginVersion());
    }

    private void register(ConnectorPlugin plugin) throws ConnectorException {
        PluginContext context = TestPluginContexts.context();
        plugin.initialize(context);
        registry.register(PluginHandle.builtIn(plugin, context));
    }

    private void registerPlatformCore() throws ConnectorException {
        registerPlatformCore(Failure.NONE);
    }

    private void registerPlatformCore(Failure failure) throws ConnectorException {
        PluginContext base = TestPluginContexts.context((request, execution) -> {
            if (platformFailure == Failure.TRANSPORT) {
                throw new ConnectorException(ErrorCategory.TRANSPORT_CONNECTION_ERROR,
                        "VENDOR_UNAVAILABLE", "Vendor is unavailable",
                        RequestDeliveryState.MAYBE_SENT);
            }
            int status = platformFailure == Failure.HTTP ? 503 : 200;
            byte[] body = (platformFailure == Failure.RESPONSE ? "not-base64" : "{\"accepted\":true}")
                    .getBytes(StandardCharsets.UTF_8);
            return new ConnectorRawResponse(status, Map.of(), body, Duration.ofMillis(1),
                    request.url(), request.body().length, body.length);
        });
        PlatformCoreConnectorPlugin plugin = new PlatformCoreConnectorPlugin();
        plugin.initialize(base);
        registry.register(PluginHandle.builtIn(plugin, base));
    }

    private Failure platformFailure = Failure.NONE;

    private CompiledConnectorPipeline compile(List<ConnectorStageDefinition> stages)
            throws ConnectorException {
        return compiler().compile(new ConnectorPipelineDefinition("1", "snapshot", stages));
    }

    private PipelineCompiler compiler() {
        return new PipelineCompiler(registry,
                new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ignored -> true), mapper);
    }

    private ConnectorStageDefinition stage(
            String key, StageCapability capability, String pluginId, String version,
            int order, JsonNode config) {
        return new ConnectorStageDefinition(key, capability, pluginId, version, order, true,
                config, compiler().sha256(config));
    }

    private JsonNode config(Failure failure) {
        return mapper.createObjectNode().put("failure", failure.name());
    }

    private JsonNode responseFailureConfig() throws Exception {
        return mapper.readTree("""
                {"direction":"RESPONSE","securitySteps":[
                  {"id":"decode","direction":"RESPONSE","stepType":"DECODE","sortNo":1,
                   "config":{"inputFrom":"BODY","encoding":"BASE64"}}
                ],"secretRefs":[]}
                """);
    }

    private ConnectorPipelineExecutor executor() {
        PluginContext context = TestPluginContexts.context();
        return new ConnectorPipelineExecutor(Clock.systemUTC(), context.logger(), context.metrics());
    }

    private ConnectorExecutionRequest request() {
        return new ConnectorExecutionRequest(Map.of("id", "1"), "VENDOR",
                Instant.now().plusSeconds(10), () -> false);
    }

    private enum Failure { NONE, BUILDER, TRANSPORT, HTTP, RESPONSE, BUSINESS }

    private static final class TestVendorPlugin extends AbstractVendorConnectorPlugin {
        private TestVendorPlugin() {
            super(ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING);
        }

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(VENDOR_ID, VENDOR_VERSION, "1.1", "Vendor", "test",
                    Set.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER));
        }

        @Override protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation)
                throws ConnectorException {
            if (Failure.BUILDER.name().equals(invocation.pluginConfig().path("failure").asText())) {
                throw new ConnectorException(ErrorCategory.REQUEST_BUILD_ERROR, "BUILD_FAILED",
                        "Vendor request build failed", RequestDeliveryState.NOT_SENT);
            }
            return requestObject();
        }

        @Override protected VendorParseResult parseResponse(
                VendorConnectorInvocation invocation, ConnectorRawResponse response) {
            if (Failure.BUSINESS.name().equals(invocation.pluginConfig().path("failure").asText())) {
                return VendorParseResult.rejected(Map.of("accepted", false), "VENDOR_DENIED",
                        com.dataplatform.plugin.spi.BillingSignal.INELIGIBLE,
                        CacheSignal.NOT_CACHEABLE, "Vendor rejected request");
            }
            return VendorParseResult.success(Map.of("accepted", true));
        }

        private static ConnectorRequest requestObject() {
            return new ConnectorRequest("POST", URI.create("https://vendor.example/query"),
                    Map.of(), Map.of(), "application/json", "{}".getBytes(StandardCharsets.UTF_8),
                    Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(2),
                    IdempotencyPolicy.IDEMPOTENT, null, 1024);
        }
    }

    private static final class TestPipelinePlugin implements ConnectorPlugin {
        private final String id;
        private final String version;

        private TestPipelinePlugin(String id, String version) {
            this.id = id;
            this.version = version;
        }

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(id, version, "1.0", "Legacy", "test", Set.of(
                    StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                    StageCapability.RESPONSE_NORMALIZER));
        }

        @Override public void initialize(PluginContext context) { }

        @Override public List<ConnectorStageFactory> stageFactories() {
            return List.of(factory(StageCapability.REQUEST_BUILDER),
                    factory(StageCapability.TRANSPORT), factory(StageCapability.RESPONSE_NORMALIZER));
        }

        private ConnectorStageFactory factory(StageCapability capability) {
            return new ConnectorStageFactory() {
                @Override public StageCapability capability() { return capability; }
                @Override public void validate(JsonNode config, PluginValidationContext context) { }
                @Override public ConnectorStage create(CompiledStageConfig config) {
                    return new PipelineStage(capability, config.config());
                }
            };
        }

        @Override public PluginSelfTestResult selfTest() { return PluginSelfTestResult.success(); }
        @Override public void close() { }
    }

    private record PipelineStage(StageCapability capability, JsonNode config)
            implements ConnectorStage {
        @Override public void execute(ConnectorExchange exchange, StageExecutionContext context)
                throws ConnectorException {
            if (capability == StageCapability.REQUEST_BUILDER) {
                exchange.setRequest(TestVendorPlugin.requestObject());
            } else if (capability == StageCapability.TRANSPORT) {
                exchange.setRawResponse(new ConnectorRawResponse(200, Map.of(), new byte[0],
                        Duration.ZERO, URI.create("https://vendor.example/query"), 0, 0));
            } else if (Failure.RESPONSE.name().equals(config.path("failure").asText())) {
                throw new ConnectorException(ErrorCategory.RESPONSE_PARSE_ERROR, "PARSE_FAILED",
                        "Legacy response failed", RequestDeliveryState.SENT);
            } else {
                exchange.setNormalizedData(Map.of("accepted", true));
                exchange.setBusinessStatus(BusinessStatus.SUCCESS);
            }
        }
    }
}
