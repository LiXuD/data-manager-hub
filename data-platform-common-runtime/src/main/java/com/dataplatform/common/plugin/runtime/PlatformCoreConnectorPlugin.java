package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.BillingSignal;
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
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.dataplatform.plugin.spi.VendorParseResult;
import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityExecutionContext;
import com.dataplatform.common.security.pipeline.SecurityPipelineExecutor;
import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fixed host plugin owning single HTTP transport and strict HOST_MAPPING normalization. */
public final class PlatformCoreConnectorPlugin implements ConnectorPlugin {

    public static final String PLUGIN_ID = "platform-core";
    public static final String VERSION = "1.0.0";
    private static final Set<String> MAPPING_FIELDS = Set.of(
            "targetField", "sourcePath", "sourceType", "defaultValue", "transformType");
    private PluginContext context;

    @Override
    public PluginDescriptor descriptor() {
        return PlatformCoreConnectorMetadata.descriptor();
    }

    @Override
    public void initialize(PluginContext context) {
        if (this.context != null) throw new IllegalStateException("platform-core is already initialized");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public List<ConnectorStageFactory> stageFactories() {
        requireInitialized();
        return List.of(new PlatformFactory(StageCapability.REQUEST_PROCESSOR),
                new PlatformFactory(StageCapability.TRANSPORT),
                new PlatformFactory(StageCapability.RESPONSE_PROCESSOR),
                new PlatformFactory(StageCapability.RESPONSE_NORMALIZER));
    }

    @Override public PluginSelfTestResult selfTest() {
        return context == null ? PluginSelfTestResult.failure("platform-core is not initialized")
                : PluginSelfTestResult.success();
    }

    @Override public void close() { context = null; }

    private void requireInitialized() {
        if (context == null) throw new IllegalStateException("platform-core is not initialized");
    }

    private final class PlatformFactory implements ConnectorStageFactory {
        private final StageCapability capability;
        private PlatformFactory(StageCapability capability) { this.capability = capability; }
        @Override public StageCapability capability() { return capability; }

        @Override
        public void validate(JsonNode config, PluginValidationContext validation) throws ConnectorException {
            if (config == null || !config.isObject()) {
                throw failure(ErrorCategory.CONFIGURATION_ERROR, "PLATFORM_CORE_CONFIG_INVALID",
                        "Platform connector config must be an object", RequestDeliveryState.NOT_SENT, null);
            }
            switch (capability) {
                case REQUEST_PROCESSOR -> validateSecurity(config, validation, SecurityDirection.REQUEST);
                case TRANSPORT -> {
                    if (!config.isEmpty()) throw invalidConfiguration();
                }
                case RESPONSE_PROCESSOR -> validateSecurity(config, validation, SecurityDirection.RESPONSE);
                case RESPONSE_NORMALIZER -> validateMapping(config);
                default -> throw invalidConfiguration();
            }
        }

        @Override
        public ConnectorStage create(CompiledStageConfig config) throws ConnectorException {
            if (context == null) throw pluginClosed();
            if (config == null || config.capability() != capability
                    || !PLUGIN_ID.equals(config.pluginId()) || !VERSION.equals(config.pluginVersion())) {
                throw invalidConfiguration();
            }
            return switch (capability) {
                case REQUEST_PROCESSOR -> new SecurityStage(config.config(), SecurityDirection.REQUEST);
                case TRANSPORT -> new TransportStage();
                case RESPONSE_PROCESSOR -> new SecurityStage(config.config(), SecurityDirection.RESPONSE);
                case RESPONSE_NORMALIZER -> new MappingStage(config.config());
                default -> throw invalidConfiguration();
            };
        }
    }

    private final class SecurityStage implements ConnectorStage {
        private final SecurityDirection direction;
        private final List<SecurityStepConfig> steps;
        private final List<String> secretRefs;

        private SecurityStage(JsonNode config, SecurityDirection direction) throws ConnectorException {
            this.direction = direction;
            this.steps = readSecuritySteps(config);
            this.secretRefs = readSecretRefs(config);
        }

        @Override public StageCapability capability() {
            return direction == SecurityDirection.REQUEST
                    ? StageCapability.REQUEST_PROCESSOR : StageCapability.RESPONSE_PROCESSOR;
        }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext execution)
                throws ConnectorException {
            if (context == null) {
                throw pluginClosed();
            }
            try {
                if (direction == SecurityDirection.REQUEST) {
                    executeRequestSecurity(exchange);
                } else {
                    executeResponseSecurity(exchange);
                }
            } catch (ConnectorException exception) {
                throw securityFailure(direction, exception);
            } catch (Exception exception) {
                throw securityFailure(direction, exception);
            }
        }

        private void executeRequestSecurity(ConnectorExchange exchange) throws ConnectorException {
            ConnectorRequest request = exchange.request();
            if (request == null) throw securityFailure(direction, null);
            SecurityExecutionContext security = securityContext(direction, request.body(),
                    request.headers(), request.query());
            Map<String, Object> originalParams = new LinkedHashMap<>(security.getParams());
            String originalBody = security.getBody();
            new SecurityPipelineExecutor().execute(direction, steps, security);
            byte[] body = processedBody(request.body(), originalBody, originalParams, security);
            exchange.setRequest(new ConnectorRequest(request.method(), request.url(),
                    toMultiMap(security.getHeaders()), toMultiMap(security.getQuery()),
                    request.contentType(), body, request.connectTimeout(), request.readTimeout(),
                    request.totalTimeout(), request.idempotencyPolicy(), request.idempotencyKey(),
                    request.maxResponseBytes()));
        }

        private void executeResponseSecurity(ConnectorExchange exchange) throws ConnectorException {
            ConnectorRawResponse response = exchange.rawResponse();
            if (response == null) throw securityFailure(direction, null);
            SecurityExecutionContext security = securityContext(direction, response.body(),
                    response.headers(), Map.of());
            Map<String, Object> originalParams = new LinkedHashMap<>(security.getParams());
            String originalBody = security.getBody();
            new SecurityPipelineExecutor().execute(direction, steps, security);
            byte[] body = processedBody(response.body(), originalBody, originalParams, security);
            exchange.setRawResponse(new ConnectorRawResponse(response.statusCode(),
                    toMultiMap(security.getHeaders()), body, response.latency(),
                    response.remoteEndpoint(), response.bytesSent(), body.length));
        }

        private SecurityExecutionContext securityContext(
                SecurityDirection securityDirection,
                byte[] body,
                Map<String, List<String>> headers,
                Map<String, List<String>> query) throws ConnectorException {
            SecurityExecutionContext security = new SecurityExecutionContext(securityDirection,
                    parseJsonObject(body), flatten(headers), flatten(query), resolveSecrets());
            security.setBody(new String(body, StandardCharsets.UTF_8));
            return security;
        }

        private Map<String, String> resolveSecrets() throws ConnectorException {
            Map<String, String> resolved = new LinkedHashMap<>();
            for (String secretRef : secretRefs) {
                try (SecretValue secret = context.secretResolver().resolve(secretRef)) {
                    resolved.put(secretRef, secret.materialize());
                }
            }
            return resolved;
        }

        private byte[] processedBody(
                byte[] original,
                String originalBody,
                Map<String, Object> originalParams,
                SecurityExecutionContext security) throws ConnectorException {
            if (!Objects.equals(originalBody, security.getBody())) {
                return security.getBody() == null ? new byte[0]
                        : security.getBody().getBytes(StandardCharsets.UTF_8);
            }
            if (!originalParams.equals(security.getParams())) {
                return context.objectCodec().write(security.getParams());
            }
            return original.clone();
        }
    }

    private final class TransportStage implements ConnectorStage {
        @Override public StageCapability capability() { return StageCapability.TRANSPORT; }
        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext execution)
                throws ConnectorException {
            if (context == null) throw pluginClosed();
            if (exchange.request() == null) {
                throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "REQUEST_NOT_BUILT",
                        "Platform transport requires a request", RequestDeliveryState.NOT_SENT, null);
            }
            exchange.setRawResponse(context.managedHttpTransport().execute(exchange.request(), execution));
        }
    }

    private final class MappingStage implements ConnectorStage {
        private final JsonNode mappings;
        private MappingStage(JsonNode config) {
            JsonNode value = config.get("responseMapping");
            this.mappings = value == null ? null : value.deepCopy();
        }
        @Override public StageCapability capability() { return StageCapability.RESPONSE_NORMALIZER; }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext execution)
                throws ConnectorException {
            if (context == null) throw pluginClosed();
            if (!(exchange.parsedResponse() instanceof VendorParseResult parsed)) {
                throw failure(ErrorCategory.CONTRACT_VIOLATION, "HOST_MAPPING_INPUT_MISSING",
                        "Host mapping requires a vendor parse result", RequestDeliveryState.SENT, null);
            }
            Map<String, Object> normalized = mappings == null || mappings.isNull()
                    ? parsed.data() : applyMapping(parsed.data(), mappings);
            exchange.setNormalizedData(normalized);
            exchange.setBusinessStatus(parsed.businessStatus());
            exchange.setBillingSignal(parsed.billingSignal());
            exchange.setCacheSignal(parsed.cacheSignal());
        }
    }

    private static void validateMapping(JsonNode config) throws ConnectorException {
        var configFields = config.fieldNames();
        while (configFields.hasNext()) {
            String field = configFields.next();
            if (!"responseMapping".equals(field)) {
                throw invalidMappingBeforeDelivery(null);
            }
        }
        JsonNode mapping = config.get("responseMapping");
        if (mapping == null || mapping.isNull()) return;
        if (!mapping.isArray() || mapping.isEmpty()) throw invalidMappingBeforeDelivery(null);
        Set<String> targets = new java.util.HashSet<>();
        for (JsonNode item : mapping) {
            if (!item.isObject()) throw invalidMappingBeforeDelivery(null);
            var itemFields = item.fieldNames();
            while (itemFields.hasNext()) {
                if (!MAPPING_FIELDS.contains(itemFields.next())) throw invalidMappingBeforeDelivery(null);
            }
            String target = validateRequiredText(item, "targetField");
            validateRequiredText(item, "sourcePath");
            String sourceType = item.path("sourceType").asText("field");
            String transform = item.path("transformType").asText("none");
            if (!Set.of("field", "jsonPath").contains(sourceType)
                    || !Set.of("none", "toString", "toNumber").contains(transform)
                    || !targets.add(target)) throw invalidMappingBeforeDelivery(null);
        }
    }

    private static void validateSecurity(
            JsonNode config,
            PluginValidationContext validation,
            SecurityDirection direction) throws ConnectorException {
        try {
            Set<String> allowed = Set.of("direction", "securitySteps", "secretRefs");
            var fields = config.fieldNames();
            while (fields.hasNext()) if (!allowed.contains(fields.next())) throw invalidConfiguration();
            if (config.size() != allowed.size()
                    || !config.path("direction").isTextual()
                    || !direction.name().equals(config.path("direction").asText())
                    || !config.path("securitySteps").isArray()
                    || !config.path("secretRefs").isArray()) {
                throw invalidConfiguration();
            }
            List<SecurityStepConfig> steps = readSecuritySteps(config);
            validateSecurityStepFields(config.path("securitySteps"));
            List<String> refs = readSecretRefs(config);
            String previous = null;
            Set<String> unique = new LinkedHashSet<>();
            for (String ref : refs) {
                if (ref.isBlank() || !ref.equals(ref.trim())
                        || previous != null && previous.compareTo(ref) >= 0
                        || !unique.add(ref) || !validation.secretReferenceExists(ref)) {
                    throw invalidConfiguration();
                }
                previous = ref;
            }
            if (!unique.equals(referencedSecrets(config.path("securitySteps")))) {
                throw invalidConfiguration();
            }
            new SecurityPipelineExecutor().validate(direction, steps);
        } catch (ConnectorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidConfiguration();
        }
    }

    private static List<SecurityStepConfig> readSecuritySteps(JsonNode config)
            throws ConnectorException {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(
                    config.path("securitySteps"),
                    new TypeReference<List<SecurityStepConfig>>() { });
        } catch (Exception exception) {
            throw invalidConfiguration();
        }
    }

    private static void validateSecurityStepFields(JsonNode steps) throws ConnectorException {
        Set<String> allowed = Set.of("id", "direction", "stepType", "stepName",
                "sortNo", "enabled", "config");
        for (JsonNode step : steps) {
            if (!step.isObject()) throw invalidConfiguration();
            var fields = step.fieldNames();
            while (fields.hasNext()) if (!allowed.contains(fields.next())) throw invalidConfiguration();
        }
    }

    private static List<String> readSecretRefs(JsonNode config) throws ConnectorException {
        List<String> result = new ArrayList<>();
        JsonNode values = config.path("secretRefs");
        if (!values.isArray()) throw invalidConfiguration();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) throw invalidConfiguration();
            result.add(value.asText());
        }
        return List.copyOf(result);
    }

    private static Set<String> referencedSecrets(JsonNode node) throws ConnectorException {
        Set<String> result = new HashSet<>();
        collectReferencedSecrets(node, result);
        return Set.copyOf(result);
    }

    private static void collectReferencedSecrets(JsonNode node, Set<String> result)
            throws ConnectorException {
        if (node == null) return;
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if ("secretRef".equals(entry.getKey())) {
                    if (!entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                        throw invalidConfiguration();
                    }
                    result.add(entry.getValue().asText());
                }
                collectReferencedSecrets(entry.getValue(), result);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) collectReferencedSecrets(child, result);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(byte[] body) {
        if (body == null || body.length == 0) return new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = context.objectCodec().read(body, Map.class);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (ConnectorException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, String> flatten(Map<String, List<String>> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, list) -> {
            if (list != null && !list.isEmpty()) result.put(key, list.get(list.size() - 1));
        });
        return result;
    }

    private static Map<String, List<String>> toMultiMap(Map<String, String> values) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, List.of(value)));
        return result;
    }

    private static Map<String, Object> applyMapping(Map<String, Object> source, JsonNode mappings)
            throws ConnectorException {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        try {
            for (JsonNode item : mappings) {
                String target = requiredText(item, "targetField");
                String path = requiredText(item, "sourcePath");
                String sourceType = item.path("sourceType").asText("field");
                Object value = "jsonPath".equals(sourceType)
                        ? JsonPath.read(source, path) : nested(source, path);
                if (value == null && item.has("defaultValue")) {
                    value = jsonValue(item.get("defaultValue"));
                }
                value = transform(value, item.path("transformType").asText("none"));
                result.put(target, value);
            }
            return Map.copyOf(result);
        } catch (ConnectorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidMapping(exception);
        }
    }

    private static Object nested(Map<String, Object> source, String path) throws ConnectorException {
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (segment.isBlank() || !(current instanceof Map<?, ?> map)
                    || !map.containsKey(segment)) throw invalidMapping(null);
            current = map.get(segment);
        }
        return current;
    }

    private static Object transform(Object value, String transform) throws ConnectorException {
        if (value == null || "none".equals(transform)) return value;
        if ("toString".equals(transform)) return String.valueOf(value);
        if ("toNumber".equals(transform)) {
            try { return value instanceof Number ? value : new BigDecimal(String.valueOf(value)); }
            catch (NumberFormatException exception) { throw invalidMapping(exception); }
        }
        throw invalidMapping(null);
    }

    private static Object jsonValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isTextual()) return value.asText();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isFloatingPointNumber()) return value.decimalValue();
        return value.deepCopy();
    }

    private static String requiredText(JsonNode node, String field) throws ConnectorException {
        if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) throw invalidMapping(null);
        return node.path(field).asText();
    }

    private static String validateRequiredText(JsonNode node, String field) throws ConnectorException {
        if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) {
            throw invalidMappingBeforeDelivery(null);
        }
        return node.path(field).asText();
    }

    private static ConnectorException invalidMapping(Throwable cause) {
        return failure(ErrorCategory.CONFIGURATION_ERROR, "HOST_MAPPING_INVALID",
                "Host response mapping is invalid", RequestDeliveryState.SENT, cause);
    }

    private static ConnectorException invalidMappingBeforeDelivery(Throwable cause) {
        return failure(ErrorCategory.CONFIGURATION_ERROR, "HOST_MAPPING_INVALID",
                "Host response mapping is invalid", RequestDeliveryState.NOT_SENT, cause);
    }

    private static ConnectorException invalidConfiguration() {
        return failure(ErrorCategory.CONFIGURATION_ERROR, "PLATFORM_CORE_CONFIG_INVALID",
                "Platform connector config is invalid", RequestDeliveryState.NOT_SENT, null);
    }

    private static ErrorCategory expectedCategory(SecurityDirection direction) {
        return direction == SecurityDirection.REQUEST
                ? ErrorCategory.AUTH_SECURITY_ERROR : ErrorCategory.RESPONSE_SECURITY_ERROR;
    }

    private static RequestDeliveryState expectedDelivery(SecurityDirection direction) {
        return direction == SecurityDirection.REQUEST
                ? RequestDeliveryState.NOT_SENT : RequestDeliveryState.SENT;
    }

    private static ConnectorException securityFailure(SecurityDirection direction, Throwable cause) {
        return failure(expectedCategory(direction), direction == SecurityDirection.REQUEST
                        ? "PLATFORM_REQUEST_SECURITY_ERROR" : "PLATFORM_RESPONSE_SECURITY_ERROR",
                direction == SecurityDirection.REQUEST
                        ? "Platform request security processing failed"
                        : "Platform response security processing failed",
                expectedDelivery(direction), cause);
    }

    private static ConnectorException pluginClosed() {
        return failure(ErrorCategory.PLUGIN_NOT_READY, "PLATFORM_CORE_CLOSED",
                "Platform connector core is not available", RequestDeliveryState.NOT_SENT, null);
    }

    private static ConnectorException failure(ErrorCategory category, String code, String message,
                                              RequestDeliveryState delivery, Throwable cause) {
        return new ConnectorException(category, code, message, delivery, cause);
    }
}
