package com.dataplatform.common.plugin.legacy;

import com.dataplatform.common.adapter.AbstractVendorAdapter;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityExecutionContext;
import com.dataplatform.common.security.pipeline.SecurityPipelineExecutor;
import com.dataplatform.common.security.pipeline.SecurityStepConfig;
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
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Built-in compatibility plugin for the existing HTTP adapter configuration.
 * It is registered explicitly by Access and never loaded from an external JAR.
 */
public final class LegacyHttpConnectorPlugin implements ConnectorPlugin {

    public static final String PLUGIN_ID = "legacy-http";
    public static final String VERSION = "1.0.0";
    private static final Set<StageCapability> CAPABILITIES = Set.of(StageCapability.values());
    private final AtomicBoolean initialized = new AtomicBoolean();
    private PluginContext context;
    private List<ConnectorStageFactory> factories = List.of();

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(PLUGIN_ID, VERSION, "1.0", "Legacy HTTP", "internal", CAPABILITIES);
    }

    @Override
    public void initialize(PluginContext context) throws ConnectorException {
        if (!initialized.compareAndSet(false, true)) {
            throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "PLUGIN_ALREADY_INITIALIZED",
                    "Legacy HTTP plugin is already initialized", RequestDeliveryState.NOT_SENT, null);
        }
        this.context = context;
        EnumMap<StageCapability, ConnectorStageFactory> created = new EnumMap<>(StageCapability.class);
        for (StageCapability capability : StageCapability.values()) {
            created.put(capability, new LegacyStageFactory(capability));
        }
        this.factories = List.copyOf(created.values());
    }

    @Override public List<ConnectorStageFactory> stageFactories() { return factories; }
    @Override public PluginSelfTestResult selfTest() {
        return context == null ? PluginSelfTestResult.failure("Plugin is not initialized")
                : PluginSelfTestResult.success();
    }
    @Override public void close() { factories = List.of(); context = null; }

    private final class LegacyStageFactory implements ConnectorStageFactory {
        private final StageCapability capability;
        private LegacyStageFactory(StageCapability capability) { this.capability = capability; }
        @Override public StageCapability capability() { return capability; }

        @Override
        public void validate(JsonNode config, PluginValidationContext validationContext) throws ConnectorException {
            if (config == null || !config.isObject()) {
                throw failure(ErrorCategory.CONFIGURATION_ERROR, "INVALID_STAGE_CONFIG",
                        "Legacy stage config must be an object", RequestDeliveryState.NOT_SENT, null);
            }
            if (capability == StageCapability.REQUEST_BUILDER) {
                requireText(config, "apiUrl");
                try {
                    URI uri = URI.create(config.path("apiUrl").asText());
                    if (uri.getScheme() == null || uri.getHost() == null) {
                        throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException exception) {
                    throw failure(ErrorCategory.CONFIGURATION_ERROR, "INVALID_VENDOR_URL",
                            "Vendor URL is invalid", RequestDeliveryState.NOT_SENT, exception);
                }
            }
            validateSecretReferences(config, validationContext);
            if (capability == StageCapability.REQUEST_PROCESSOR) {
                validateSecuritySteps(config, SecurityDirection.REQUEST);
            } else if (capability == StageCapability.RESPONSE_PROCESSOR) {
                validateSecuritySteps(config, SecurityDirection.RESPONSE);
            }
        }

        @Override
        public ConnectorStage create(CompiledStageConfig config) {
            JsonNode snapshot = config.config();
            return switch (capability) {
                case REQUEST_BUILDER -> new RequestBuilderStage(snapshot);
                case REQUEST_PROCESSOR -> new RequestProcessorStage(snapshot);
                case TRANSPORT -> new TransportStage();
                case RESPONSE_PROCESSOR -> new ResponseProcessorStage(snapshot);
                case RESPONSE_PARSER -> new ResponseParserStage();
                case RESPONSE_NORMALIZER -> new ResponseNormalizerStage(snapshot);
            };
        }
    }

    private final class RequestBuilderStage implements ConnectorStage {
        private final JsonNode config;
        private RequestBuilderStage(JsonNode config) { this.config = config; }
        @Override public StageCapability capability() { return StageCapability.REQUEST_BUILDER; }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext executionContext)
                throws ConnectorException {
            try {
                String mapping = jsonOrText(config.get("requestMapping"));
                Map<String, Object> mapped = new LegacyMappingBridge(exchange.vendorCode())
                        .transformRequest(exchange.standardParameters(), mapping);
                String method = config.path("method").asText("POST").toUpperCase();
                Map<String, List<String>> headers = stringMultiMap(config.get("headers"));
                Map<String, List<String>> query = "GET".equals(method) ? objectMultiMap(mapped) : Map.of();
                byte[] body = "GET".equals(method) || "HEAD".equals(method) ? new byte[0]
                        : context.objectCodec().write(mapped);
                ConnectorRequest request = new ConnectorRequest(method, URI.create(config.path("apiUrl").asText()),
                        headers, query, config.path("contentType").asText("application/json; charset=utf-8"), body,
                        duration(config, "connectTimeoutMs", 5000), duration(config, "readTimeoutMs", 30000),
                        duration(config, "totalTimeoutMs", 30000), idempotency(config),
                        textOrNull(config, "idempotencyKey"), config.path("maxResponseBytes").asLong(10L * 1024 * 1024));
                exchange.setRequest(request);
                exchange.recordStageOutput("legacy.mappedParams", mapped);
            } catch (ConnectorException exception) {
                throw exception;
            } catch (Exception exception) {
                throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "LEGACY_REQUEST_BUILD_ERROR",
                        "Legacy request could not be built", RequestDeliveryState.NOT_SENT, exception);
            }
        }
    }

    private final class RequestProcessorStage implements ConnectorStage {
        private final JsonNode config;
        private RequestProcessorStage(JsonNode config) { this.config = config; }
        @Override public StageCapability capability() { return StageCapability.REQUEST_PROCESSOR; }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext executionContext)
                throws ConnectorException {
            ConnectorRequest request = requireRequest(exchange);
            Map<String, Object> mapped = mappedParams(exchange);
            Map<String, String> headers = flatten(request.headers());
            Map<String, String> query = flatten(request.query());
            Map<String, String> secrets = resolveAliases(config.path("secretRefs"));
            try {
                SecurityExecutionContext security = new SecurityExecutionContext(SecurityDirection.REQUEST,
                        mapped, headers, query, secrets);
                security.setBody(new String(request.body(), StandardCharsets.UTF_8));
                List<SecurityStepConfig> steps = securitySteps(config);
                if (!steps.isEmpty()) {
                    new SecurityPipelineExecutor().execute(SecurityDirection.REQUEST, steps, security);
                }
                applyLegacySecretPlaceholder(security.getHeaders(), config, secrets);
                applyAuth(security.getHeaders(), security.getQuery(), config.path("authConfig"),
                        config.path("authType").asText("NONE"));
                exchange.setRequest(copyRequest(request, toMultiMap(security.getHeaders()),
                        toMultiMap(security.getQuery()), security.getBody().getBytes(StandardCharsets.UTF_8)));
            } catch (ConnectorException exception) {
                throw exception;
            } catch (Exception exception) {
                throw failure(ErrorCategory.AUTH_SECURITY_ERROR, "LEGACY_REQUEST_SECURITY_ERROR",
                        "Legacy request security processing failed", RequestDeliveryState.NOT_SENT, exception);
            }
        }
    }

    private final class TransportStage implements ConnectorStage {
        @Override public StageCapability capability() { return StageCapability.TRANSPORT; }
        @Override public void execute(ConnectorExchange exchange, StageExecutionContext executionContext)
                throws ConnectorException {
            exchange.setRawResponse(context.managedHttpTransport().execute(requireRequest(exchange), executionContext));
        }
    }

    private final class ResponseProcessorStage implements ConnectorStage {
        private final JsonNode config;
        private ResponseProcessorStage(JsonNode config) { this.config = config; }
        @Override public StageCapability capability() { return StageCapability.RESPONSE_PROCESSOR; }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext executionContext)
                throws ConnectorException {
            ConnectorRawResponse response = requireResponse(exchange);
            Map<String, Object> parsed = tryParseObject(response.body());
            Map<String, String> secrets = resolveAliases(config.path("secretRefs"));
            try {
                SecurityExecutionContext security = new SecurityExecutionContext(SecurityDirection.RESPONSE, parsed,
                        flatten(response.headers()), Map.of(), secrets);
                security.setBody(new String(response.body(), StandardCharsets.UTF_8));
                List<SecurityStepConfig> steps = securitySteps(config);
                if (!steps.isEmpty()) {
                    new SecurityPipelineExecutor().execute(SecurityDirection.RESPONSE, steps, security);
                }
                byte[] processed = security.getBody() == null ? response.body()
                        : security.getBody().getBytes(StandardCharsets.UTF_8);
                exchange.setRawResponse(new ConnectorRawResponse(response.statusCode(), response.headers(), processed,
                        response.latency(), response.remoteEndpoint(), response.bytesSent(), processed.length));
            } catch (Exception exception) {
                throw failure(ErrorCategory.RESPONSE_SECURITY_ERROR, "LEGACY_RESPONSE_SECURITY_ERROR",
                        "Legacy response security processing failed", RequestDeliveryState.SENT, exception);
            }
        }
    }

    private final class ResponseParserStage implements ConnectorStage {
        @Override public StageCapability capability() { return StageCapability.RESPONSE_PARSER; }
        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext executionContext)
                throws ConnectorException {
            ConnectorRawResponse response = requireResponse(exchange);
            if (response.body().length == 0) {
                throw failure(ErrorCategory.RESPONSE_PARSE_ERROR, "EMPTY_RESPONSE",
                        "Vendor response body is empty", RequestDeliveryState.SENT, null);
            }
            try {
                Map<String, Object> parsed = context.objectCodec().read(response.body(), Map.class);
                exchange.setParsedResponse(parsed);
                exchange.setBusinessStatus(BusinessStatus.SUCCESS);
            } catch (ConnectorException exception) {
                throw failure(ErrorCategory.RESPONSE_PARSE_ERROR, "INVALID_JSON_RESPONSE",
                        "Vendor response is not a JSON object", RequestDeliveryState.SENT, exception);
            }
        }
    }

    private final class ResponseNormalizerStage implements ConnectorStage {
        private final JsonNode config;
        private ResponseNormalizerStage(JsonNode config) { this.config = config; }
        @Override public StageCapability capability() { return StageCapability.RESPONSE_NORMALIZER; }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext executionContext)
                throws ConnectorException {
            if (!(exchange.parsedResponse() instanceof Map<?, ?> raw)) {
                throw failure(ErrorCategory.CONTRACT_VIOLATION, "PARSED_RESPONSE_MISSING",
                        "Response parser did not produce an object", RequestDeliveryState.SENT, null);
            }
            Map<String, Object> parsed = new LinkedHashMap<>();
            raw.forEach((key, value) -> parsed.put(String.valueOf(key), value));
            String mapping = jsonOrText(config.get("responseMapping"));
            Map<String, Object> normalized = new LegacyMappingBridge(exchange.vendorCode())
                    .transformResponse(parsed, mapping);
            exchange.setNormalizedData(normalized);
            exchange.setBusinessStatus(BusinessStatus.SUCCESS);
            exchange.setBillingSignal(BillingSignal.ELIGIBLE);
            exchange.setCacheSignal(CacheSignal.CACHEABLE);
        }
    }

    private ConnectorRequest copyRequest(ConnectorRequest source, Map<String, List<String>> headers,
                                         Map<String, List<String>> query, byte[] body) {
        return new ConnectorRequest(source.method(), source.url(), headers, query, source.contentType(), body,
                source.connectTimeout(), source.readTimeout(), source.totalTimeout(), source.idempotencyPolicy(),
                source.idempotencyKey(), source.maxResponseBytes());
    }

    private void applyAuth(Map<String, String> headers, Map<String, String> query,
                           JsonNode authConfig, String authType) throws ConnectorException {
        JsonNode resolved = resolveSecretNodes(authConfig);
        String normalized = authType == null ? "NONE" : authType.toUpperCase();
        switch (normalized) {
            case "", "NONE" -> { }
            case "BEARER" -> headers.put("Authorization", "Bearer " + requiredConfig(resolved, "token"));
            case "BASIC" -> {
                String value = requiredConfig(resolved, "username") + ":" + requiredConfig(resolved, "password");
                headers.put("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(value.getBytes(StandardCharsets.UTF_8)));
            }
            case "API_KEY" -> {
                String name = requiredConfig(resolved, "apiKeyName");
                String value = requiredConfig(resolved, "apiKeyValue");
                if ("query".equalsIgnoreCase(resolved.path("apiKeyLocation").asText("header"))) {
                    query.put(name, value);
                } else {
                    headers.put(name, value);
                }
            }
            default -> throw failure(ErrorCategory.CONFIGURATION_ERROR, "UNKNOWN_AUTH_TYPE",
                    "Legacy authentication type is not supported", RequestDeliveryState.NOT_SENT, null);
        }
    }

    private JsonNode resolveSecretNodes(JsonNode node) throws ConnectorException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new ObjectMapper().createObjectNode();
        }
        if (node.isObject() && node.size() == 1 && node.hasNonNull("secretRef")) {
            try (SecretValue secret = context.secretResolver().resolve(node.path("secretRef").asText())) {
                return new ObjectMapper().getNodeFactory().textNode(secret.materialize());
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                try { result.set(entry.getKey(), resolveSecretNodes(entry.getValue())); }
                catch (ConnectorException exception) { throw new SecretResolutionRuntimeException(exception); }
            });
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            for (JsonNode child : node) {
                result.add(resolveSecretNodes(child));
            }
            return result;
        }
        return node.deepCopy();
    }

    private Map<String, String> resolveAliases(JsonNode aliases) throws ConnectorException {
        if (aliases == null || !aliases.isObject()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        var fields = aliases.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            try (SecretValue secret = context.secretResolver().resolve(entry.getValue().asText())) {
                resolved.put(entry.getKey(), secret.materialize());
            }
        }
        return resolved;
    }

    private void applyLegacySecretPlaceholder(Map<String, String> headers, JsonNode config,
                                              Map<String, String> secrets) {
        String alias = config.path("legacySecretAlias").asText(null);
        if (alias == null || !secrets.containsKey(alias)) {
            return;
        }
        headers.replaceAll((key, value) -> value == null ? null : value.replace("{secretKey}", secrets.get(alias)));
    }

    private void validateSecretReferences(JsonNode node, PluginValidationContext validationContext)
            throws ConnectorException {
        if (node == null) return;
        if (node.isObject()) {
            if (node.size() == 1 && node.hasNonNull("secretRef")) {
                String ref = node.path("secretRef").asText();
                if (!validationContext.secretReferenceExists(ref)) {
                    throw failure(ErrorCategory.CONFIGURATION_ERROR, "SECRET_REF_NOT_FOUND",
                            "A configured secret reference does not exist", RequestDeliveryState.NOT_SENT, null);
                }
            }
            if (node.has("secretRefs") && node.path("secretRefs").isObject()) {
                node.path("secretRefs").forEach(value -> {
                    if (!validationContext.secretReferenceExists(value.asText())) {
                        throw new IllegalArgumentException("secret reference does not exist");
                    }
                });
            }
            var fields = node.fields();
            while (fields.hasNext()) validateSecretReferences(fields.next().getValue(), validationContext);
        } else if (node.isArray()) {
            for (JsonNode child : node) validateSecretReferences(child, validationContext);
        }
    }

    private void validateSecuritySteps(JsonNode config, SecurityDirection direction) throws ConnectorException {
        try {
            List<SecurityStepConfig> steps = securitySteps(config);
            if (!steps.isEmpty()) new SecurityPipelineExecutor().validate(direction, steps);
        } catch (Exception exception) {
            throw failure(ErrorCategory.CONFIGURATION_ERROR, "INVALID_SECURITY_PIPELINE",
                    "Legacy security pipeline is invalid", RequestDeliveryState.NOT_SENT, exception);
        }
    }

    private List<SecurityStepConfig> securitySteps(JsonNode config) {
        JsonNode steps = config.path("securitySteps");
        if (!steps.isArray() || steps.isEmpty()) return List.of();
        return new ObjectMapper().convertValue(steps, new TypeReference<List<SecurityStepConfig>>() { });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mappedParams(ConnectorExchange exchange) {
        Object value = exchange.completedStageOutputs().get("legacy.mappedParams");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : exchange.standardParameters();
    }

    private Map<String, Object> tryParseObject(byte[] bytes) {
        try { return context.objectCodec().read(bytes, Map.class); }
        catch (ConnectorException ignored) { return new LinkedHashMap<>(); }
    }

    private ConnectorRequest requireRequest(ConnectorExchange exchange) throws ConnectorException {
        if (exchange.request() == null) throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "REQUEST_NOT_BUILT",
                "Legacy request was not built", RequestDeliveryState.NOT_SENT, null);
        return exchange.request();
    }

    private ConnectorRawResponse requireResponse(ConnectorExchange exchange) throws ConnectorException {
        if (exchange.rawResponse() == null) throw failure(ErrorCategory.CONTRACT_VIOLATION, "RESPONSE_MISSING",
                "Legacy transport response is missing", RequestDeliveryState.MAYBE_SENT, null);
        return exchange.rawResponse();
    }

    private Map<String, List<String>> stringMultiMap(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(),
                entry.getValue().isArray() ? values(entry.getValue()) : List.of(entry.getValue().asText())));
        return result;
    }

    private List<String> values(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private Map<String, List<String>> objectMultiMap(Map<String, Object> values) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, List.of(String.valueOf(value))));
        return result;
    }

    private Map<String, List<String>> toMultiMap(Map<String, String> values) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, List.of(value)));
        return result;
    }

    private Map<String, String> flatten(Map<String, List<String>> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, list) -> { if (!list.isEmpty()) result.put(key, list.get(list.size() - 1)); });
        return result;
    }

    private Duration duration(JsonNode config, String field, long defaultMillis) {
        long value = config.path(field).asLong(defaultMillis);
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return Duration.ofMillis(value);
    }

    private IdempotencyPolicy idempotency(JsonNode config) {
        try { return IdempotencyPolicy.valueOf(config.path("idempotencyPolicy").asText("IDEMPOTENT")); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid idempotencyPolicy"); }
    }

    private String jsonOrText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return node.isTextual() ? node.asText() : node.toString();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String requiredConfig(JsonNode config, String field) throws ConnectorException {
        String value = config.path(field).asText(null);
        if (value == null || value.isBlank()) throw failure(ErrorCategory.CONFIGURATION_ERROR,
                "AUTH_CONFIG_MISSING", "Legacy authentication configuration is incomplete",
                RequestDeliveryState.NOT_SENT, null);
        return value;
    }

    private void requireText(JsonNode config, String field) throws ConnectorException {
        if (!config.hasNonNull(field) || config.path(field).asText().isBlank()) {
            throw failure(ErrorCategory.CONFIGURATION_ERROR, "MISSING_STAGE_CONFIG",
                    "Legacy stage configuration is incomplete", RequestDeliveryState.NOT_SENT, null);
        }
    }

    private ConnectorException failure(ErrorCategory category, String code, String message,
                                       RequestDeliveryState delivery, Throwable cause) {
        if (cause instanceof SecretResolutionRuntimeException wrapper) cause = wrapper.connectorException;
        return new ConnectorException(category, code, message, delivery, cause);
    }

    private static final class SecretResolutionRuntimeException extends RuntimeException {
        private final ConnectorException connectorException;
        private SecretResolutionRuntimeException(ConnectorException cause) {
            super(cause); this.connectorException = cause;
        }
    }

    private static final class LegacyMappingBridge extends AbstractVendorAdapter {
        private final String vendorCode;
        private LegacyMappingBridge(String vendorCode) { this.vendorCode = vendorCode; }
        @Override public String getVendorCode() { return vendorCode; }
        @Override public boolean supports(String dataTypeCode) { return true; }
        @Override public Map<String, Object> execute(VendorAdapterConfig config, Map<String, Object> params) {
            throw new UnsupportedOperationException("LegacyMappingBridge only exposes mapping behavior");
        }
    }
}
