package com.example.dataplatform.fixture;

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
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal real external plugin used only by the isolated connector E2E fixture.
 * It deliberately implements two stages so packaging, loading and stage execution
 * can be proved without depending on any host implementation classes.
 */
public final class SignedE2eConnectorPlugin implements ConnectorPlugin {

    public static final String PLUGIN_ID = "e2e-signed-connector";
    public static final String VERSION = "1.0.0";

    private final AtomicBoolean initialized = new AtomicBoolean();
    private volatile PluginContext context;
    private volatile List<ConnectorStageFactory> factories = List.of();

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(PLUGIN_ID, VERSION, "1.0", "E2E Signed Connector",
                "test-fixture", Set.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER));
    }

    @Override
    public void initialize(PluginContext pluginContext) throws ConnectorException {
        if (pluginContext == null) {
            throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "FIXTURE_CONTEXT_MISSING",
                    "Fixture plugin context is missing", null);
        }
        if (!initialized.compareAndSet(false, true)) {
            throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "FIXTURE_ALREADY_INITIALIZED",
                    "Fixture plugin is already initialized", null);
        }
        context = pluginContext;
        factories = List.of(new FixtureStageFactory(StageCapability.REQUEST_BUILDER),
                new FixtureStageFactory(StageCapability.RESPONSE_PARSER));
    }

    @Override
    public List<ConnectorStageFactory> stageFactories() {
        return factories;
    }

    @Override
    public PluginSelfTestResult selfTest() {
        return context == null ? PluginSelfTestResult.failure("Fixture plugin is not initialized")
                : PluginSelfTestResult.success();
    }

    @Override
    public void close() {
        factories = List.of();
        context = null;
    }

    private final class FixtureStageFactory implements ConnectorStageFactory {
        private final StageCapability capability;

        private FixtureStageFactory(StageCapability capability) {
            this.capability = capability;
        }

        @Override
        public StageCapability capability() {
            return capability;
        }

        @Override
        public void validate(JsonNode config, PluginValidationContext validationContext) throws ConnectorException {
            if (config == null || !config.isObject()) {
                throw failure(ErrorCategory.CONFIGURATION_ERROR, "FIXTURE_CONFIG_INVALID",
                        "Fixture stage config must be an object", null);
            }
            if (capability == StageCapability.REQUEST_BUILDER) {
                String endpoint = config.path("endpoint").asText();
                try {
                    URI uri = URI.create(endpoint);
                    if (!"https".equalsIgnoreCase(uri.getScheme())
                            || !"localhost".equalsIgnoreCase(uri.getHost())) {
                        throw new IllegalArgumentException("endpoint must be HTTPS localhost");
                    }
                } catch (IllegalArgumentException exception) {
                    throw failure(ErrorCategory.CONFIGURATION_ERROR, "FIXTURE_ENDPOINT_INVALID",
                            "Fixture endpoint must use HTTPS localhost", exception);
                }
            }
        }

        @Override
        public ConnectorStage create(CompiledStageConfig compiled) throws ConnectorException {
            if (compiled.capability() != capability) {
                throw failure(ErrorCategory.CONFIGURATION_ERROR, "FIXTURE_CAPABILITY_MISMATCH",
                        "Fixture compiled stage capability does not match", null);
            }
            JsonNode config = compiled.config();
            return capability == StageCapability.REQUEST_BUILDER
                    ? new RequestBuilderStage(config)
                    : new ResponseParserStage();
        }
    }

    private final class RequestBuilderStage implements ConnectorStage {
        private final JsonNode config;

        private RequestBuilderStage(JsonNode config) {
            this.config = config.deepCopy();
        }

        @Override
        public StageCapability capability() {
            return StageCapability.REQUEST_BUILDER;
        }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext executionContext)
                throws ConnectorException {
            if (executionContext.cancellationRequested() || exchange.cancellationRequested()) {
                throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "FIXTURE_CANCELLED",
                        "Fixture request was cancelled before delivery", null);
            }
            try {
                byte[] body = context.objectCodec().write(exchange.standardParameters());
                ConnectorRequest request = new ConnectorRequest(
                        "POST",
                        URI.create(config.path("endpoint").asText()),
                        Map.of(
                                "Accept", List.of("application/json"),
                                "X-Connector-Fixture", List.of("e2e-signed-connector")),
                        Map.of(),
                        "application/json; charset=utf-8",
                        body,
                        duration("connectTimeoutMs", 2_000),
                        duration("readTimeoutMs", 5_000),
                        duration("totalTimeoutMs", 8_000),
                        IdempotencyPolicy.IDEMPOTENT,
                        null,
                        config.path("maxResponseBytes").asLong(1024 * 1024));
                exchange.setRequest(request);
                exchange.recordStageOutput("fixture.request.endpoint", request.url().toString());
            } catch (IllegalArgumentException exception) {
                throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "FIXTURE_REQUEST_BUILD_FAILED",
                        "Fixture request could not be built", exception);
            }
        }

        private Duration duration(String field, long defaultValue) {
            return Duration.ofMillis(config.path(field).asLong(defaultValue));
        }
    }

    private final class ResponseParserStage implements ConnectorStage {
        @Override
        public StageCapability capability() {
            return StageCapability.RESPONSE_PARSER;
        }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext executionContext)
                throws ConnectorException {
            ConnectorRawResponse response = exchange.rawResponse();
            if (response == null) {
                throw failure(ErrorCategory.RESPONSE_PARSE_ERROR, "FIXTURE_RESPONSE_MISSING",
                        "Fixture response is missing", null);
            }
            Object parsed = context.objectCodec().read(response.body(), Object.class);
            exchange.setParsedResponse(parsed);
            exchange.recordStageOutput("fixture.response.status", response.statusCode());
        }
    }

    private ConnectorException failure(ErrorCategory category, String code, String safeMessage, Throwable cause) {
        return new ConnectorException(category, code, safeMessage, RequestDeliveryState.NOT_SENT, cause);
    }
}
