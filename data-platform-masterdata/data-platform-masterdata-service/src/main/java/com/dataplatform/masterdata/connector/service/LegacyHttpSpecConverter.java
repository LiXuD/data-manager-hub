package com.dataplatform.masterdata.connector.service;

import com.dataplatform.common.plugin.runtime.GenericHttpConnectorConfigValidator;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Fail-closed, read-only Legacy HTTP conversion. It never resolves secrets or writes persistence.
 */
@Service
public final class LegacyHttpSpecConverter {
    static final String LEGACY_PLUGIN_ID = "legacy-http";
    static final String LEGACY_PLUGIN_VERSION = "1.0.0";
    static final String GENERIC_PLUGIN_ID = "generic-http";
    static final String GENERIC_PLUGIN_VERSION = "2.0.0";
    private static final int MAX_STEPS = 50;
    private static final Map<String, Integer> CAPABILITY_ORDER = Map.of(
            "REQUEST_BUILDER", 0,
            "REQUEST_PROCESSOR", 1,
            "TRANSPORT", 2,
            "RESPONSE_PROCESSOR", 3,
            "RESPONSE_PARSER", 4,
            "RESPONSE_NORMALIZER", 5);
    private static final Set<String> KNOWN_CONFIG_FIELDS = Set.of(
            "apiUrl", "method", "requestMapping", "headers", "contentType",
            "connectTimeoutMs", "readTimeoutMs", "totalTimeoutMs",
            "idempotencyPolicy", "idempotencyKey", "maxResponseBytes",
            "authType", "authConfig", "secretRefs", "legacySecretAlias",
            "securitySteps", "responseMapping");
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> AUTH_TYPES = Set.of("NONE", "BEARER", "BASIC", "API_KEY");
    private static final Set<String> IDEMPOTENCY_POLICIES = Set.of(
            "IDEMPOTENT", "IDEMPOTENT_WITH_KEY", "NON_IDEMPOTENT");
    private static final long PLATFORM_MAX_RESPONSE_BYTES = 10L * 1024 * 1024;
    private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,127}");
    private static final Set<String> FORBIDDEN_PATH_SEGMENTS = Set.of(
            "__proto__", "prototype", "constructor");
    private static final Set<String> REQUEST_BUILDER_FIELDS = Set.of(
            "apiUrl", "method", "requestMapping", "headers", "contentType",
            "connectTimeoutMs", "readTimeoutMs", "totalTimeoutMs",
            "idempotencyPolicy", "idempotencyKey", "maxResponseBytes");
    private static final Set<String> REQUEST_PROCESSOR_FIELDS = Set.of(
            "authType", "authConfig", "secretRefs", "legacySecretAlias", "securitySteps");
    private static final Set<String> RESPONSE_PROCESSOR_FIELDS = Set.of(
            "secretRefs", "legacySecretAlias", "securitySteps");
    private static final Set<String> RESPONSE_NORMALIZER_FIELDS = Set.of("responseMapping");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public LegacyHttpConversionPreflightResult preflight(List<ConnectorPipelineStepDTO> pipelineSnapshot) {
        return preflight(pipelineSnapshot, null);
    }

    public LegacyHttpConversionPreflightResult preflight(
            List<ConnectorPipelineStepDTO> pipelineSnapshot,
            LegacyHttpConversionPolicy platformPolicy) {
        if (pipelineSnapshot == null || pipelineSnapshot.isEmpty()) {
            return legacy(reason(LegacyHttpConversionReasonCode.EMPTY_PIPELINE, null, null,
                    "流水线为空，无法证明可无损转换"));
        }

        List<LegacyHttpConversionReason> structural = new ArrayList<>();
        List<LegacyHttpConversionReason> dedicated = new ArrayList<>();
        if (pipelineSnapshot.size() > MAX_STEPS) {
            structural.add(reason(LegacyHttpConversionReasonCode.TOO_MANY_STEPS, null, null,
                    "流水线步骤数超过当前上限"));
        }

        Set<String> stageKeys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        List<IndexedStep> enabled = new ArrayList<>();
        for (int index = 0; index < pipelineSnapshot.size(); index++) {
            ConnectorPipelineStepDTO step = pipelineSnapshot.get(index);
            if (step == null) {
                structural.add(reason(LegacyHttpConversionReasonCode.NULL_STEP, index, null,
                        "流水线包含空步骤"));
                continue;
            }
            String stageKey = safeStageKey(step.stageKey());
            if (step.stageKey() == null || step.stageKey().isBlank()) {
                structural.add(reason(LegacyHttpConversionReasonCode.STAGE_KEY_MISSING, index, stageKey,
                        "stageKey 为空"));
            } else if (!stageKeys.add(step.stageKey())) {
                structural.add(reason(LegacyHttpConversionReasonCode.DUPLICATE_STAGE_KEY, index, stageKey,
                        "stageKey 重复"));
            }
            if (step.order() == null || step.order() < 0) {
                structural.add(reason(LegacyHttpConversionReasonCode.ORDER_MISSING_OR_INVALID, index, stageKey,
                        "order 为空或为负数"));
            } else if (!orders.add(step.order())) {
                structural.add(reason(LegacyHttpConversionReasonCode.DUPLICATE_ORDER, index, stageKey,
                        "order 重复"));
            }
            if (!CAPABILITY_ORDER.containsKey(step.capability())) {
                structural.add(reason(LegacyHttpConversionReasonCode.CAPABILITY_UNSUPPORTED, index, stageKey,
                        "capability 不受支持"));
            } else if (!Boolean.FALSE.equals(step.enabled()) && step.order() != null) {
                enabled.add(new IndexedStep(index, step));
            }
            if (step.pluginId() == null || step.pluginId().isBlank()
                    || step.pluginVersion() == null || step.pluginVersion().isBlank()) {
                structural.add(reason(LegacyHttpConversionReasonCode.PLUGIN_BINDING_MISSING, index, stageKey,
                        "插件 ID 或固定版本为空"));
            } else if (!LEGACY_PLUGIN_ID.equals(step.pluginId())) {
                dedicated.add(reason(LegacyHttpConversionReasonCode.NON_LEGACY_PLUGIN, index, stageKey,
                        "存在自定义或混合插件步骤，需要专用连接器插件承接"));
            } else if (!LEGACY_PLUGIN_VERSION.equals(step.pluginVersion())) {
                structural.add(reason(LegacyHttpConversionReasonCode.LEGACY_VERSION_UNSUPPORTED, index, stageKey,
                        "legacy-http 版本不是可转换基线 1.0.0"));
            }
        }

        validateExecutableTopology(enabled, structural);
        if (!structural.isEmpty()) {
            structural.addAll(dedicated);
            return new LegacyHttpConversionPreflightResult(
                    LegacyHttpConversionClassification.MUST_REMAIN_LEGACY, structural);
        }
        if (!dedicated.isEmpty()) {
            return new LegacyHttpConversionPreflightResult(
                    LegacyHttpConversionClassification.REQUIRES_DEDICATED_PLUGIN, dedicated);
        }

        List<LegacyHttpConversionReason> conversionBlockers = new ArrayList<>();
        validateLegacyTopology(enabled, conversionBlockers);
        for (int index = 0; index < pipelineSnapshot.size(); index++) {
            validateLegacyConfig(index, pipelineSnapshot.get(index), platformPolicy, conversionBlockers);
        }
        validateCrossStage(enabled, conversionBlockers);
        if (!conversionBlockers.isEmpty()) {
            return new LegacyHttpConversionPreflightResult(
                    LegacyHttpConversionClassification.MUST_REMAIN_LEGACY, conversionBlockers);
        }
        return new LegacyHttpConversionPreflightResult(
                LegacyHttpConversionClassification.LOSSLESS_CONVERTIBLE, List.of());
    }

    public LegacyHttpConversionResult convert(
            List<ConnectorPipelineStepDTO> pipelineSnapshot,
            LegacyHttpConversionPolicy platformPolicy) {
        LegacyHttpConversionPreflightResult preflight = preflight(pipelineSnapshot, platformPolicy);
        if (!preflight.convertible()) return new LegacyHttpConversionResult(preflight, null);

        Map<String, ConnectorPipelineStepDTO> stages = new HashMap<>();
        pipelineSnapshot.stream().filter(step -> !Boolean.FALSE.equals(step.enabled()))
                .forEach(step -> stages.put(step.capability(), step));
        Map<String, Object> builder = stages.get("REQUEST_BUILDER").config();
        Map<String, Object> requestProcessor = stages.containsKey("REQUEST_PROCESSOR")
                ? stages.get("REQUEST_PROCESSOR").config() : Map.of();
        Map<String, Object> normalizer = stages.containsKey("RESPONSE_NORMALIZER")
                ? stages.get("RESPONSE_NORMALIZER").config() : Map.of();

        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("endpoint", ((String) builder.get("apiUrl")).trim());
        config.put("method", method(builder));
        config.put("contentType", GenericHttpConnectorConfigValidator.JSON_CONTENT_TYPE);
        List<Map<String, Object>> headers = convertedHeaders(builder.get("headers"));
        if (!headers.isEmpty()) config.put("headers", headers);
        List<Map<String, Object>> requestMapping = convertedRequestMapping(builder.get("requestMapping"));
        if (!requestMapping.isEmpty()) config.put("requestMapping", requestMapping);
        config.put("auth", convertedAuth(requestProcessor));
        GenericHttpConnectorConfigValidator.validate(MAPPER.valueToTree(config), ignored -> true);

        List<ConnectorSpecDTO.ResponseMapping> responseMapping = convertedResponseMapping(
                normalizer.get("responseMapping"));
        ConnectorSpecDTO spec = new ConnectorSpecDTO("1",
                new ConnectorSpecDTO.PluginRef(GENERIC_PLUGIN_ID, GENERIC_PLUGIN_VERSION),
                Collections.unmodifiableMap(config), responseMapping.isEmpty() ? null : responseMapping);
        return new LegacyHttpConversionResult(preflight, spec);
    }

    public ConnectorSpecDTO toConnectorSpec(
            List<ConnectorPipelineStepDTO> pipelineSnapshot,
            LegacyHttpConversionPolicy platformPolicy) {
        LegacyHttpConversionResult result = convert(pipelineSnapshot, platformPolicy);
        if (!result.convertible()) {
            throw new IllegalArgumentException("LEGACY_PIPELINE_NOT_CONVERTIBLE");
        }
        return result.connectorSpec();
    }

    private void validateExecutableTopology(List<IndexedStep> enabled,
                                            List<LegacyHttpConversionReason> reasons) {
        List<IndexedStep> ordered = enabled.stream()
                .sorted(Comparator.comparingInt(item -> item.step().order()))
                .toList();
        int previousCapability = -1;
        int transports = 0;
        for (IndexedStep item : ordered) {
            ConnectorPipelineStepDTO step = item.step();
            Integer capability = CAPABILITY_ORDER.get(step.capability());
            if (capability == null) {
                continue;
            }
            if (capability < previousCapability) {
                reasons.add(reason(LegacyHttpConversionReasonCode.CAPABILITY_ORDER_INVALID,
                        item.index(), safeStageKey(step.stageKey()),
                        "启用步骤的 capability 顺序不合法"));
            }
            previousCapability = capability;
            if ("TRANSPORT".equals(step.capability())) {
                transports++;
            }
        }
        if (transports != 1) {
            reasons.add(reason(LegacyHttpConversionReasonCode.TRANSPORT_COUNT_INVALID, null, null,
                    "启用流水线必须恰好包含一个 TRANSPORT"));
        }
    }

    private void validateLegacyTopology(List<IndexedStep> enabled,
                                        List<LegacyHttpConversionReason> reasons) {
        Map<String, Integer> counts = new HashMap<>();
        for (IndexedStep item : enabled) {
            counts.merge(item.step().capability(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (entry.getValue() > 1) {
                reasons.add(reason(LegacyHttpConversionReasonCode.DUPLICATE_ENABLED_CAPABILITY,
                        null, null, "同一 capability 存在多个启用步骤: " + entry.getKey()));
            }
        }
        if (counts.getOrDefault("REQUEST_BUILDER", 0) != 1) {
            reasons.add(reason(LegacyHttpConversionReasonCode.REQUEST_BUILDER_MISSING, null, null,
                    "可转换流水线必须恰好包含一个启用的 REQUEST_BUILDER"));
        }
        if (counts.getOrDefault("RESPONSE_PARSER", 0) != 1) {
            reasons.add(reason(LegacyHttpConversionReasonCode.RESPONSE_PARSER_MISSING, null, null,
                    "可转换流水线必须恰好包含一个启用的 RESPONSE_PARSER"));
        }
        if (counts.getOrDefault("RESPONSE_NORMALIZER", 0) != 1) {
            reasons.add(reason(LegacyHttpConversionReasonCode.RESPONSE_NORMALIZER_MISSING, null, null,
                    "可转换流水线必须恰好包含一个启用的 RESPONSE_NORMALIZER"));
        }
    }

    private void validateLegacyConfig(int index, ConnectorPipelineStepDTO step,
                                      LegacyHttpConversionPolicy platformPolicy,
                                      List<LegacyHttpConversionReason> reasons) {
        Map<String, Object> config = step.config() == null ? Map.of() : step.config();
        String stageKey = safeStageKey(step.stageKey());
        Set<String> capabilityFields = switch (step.capability()) {
            case "REQUEST_BUILDER" -> REQUEST_BUILDER_FIELDS;
            case "REQUEST_PROCESSOR" -> REQUEST_PROCESSOR_FIELDS;
            case "RESPONSE_PROCESSOR" -> RESPONSE_PROCESSOR_FIELDS;
            case "RESPONSE_NORMALIZER" -> RESPONSE_NORMALIZER_FIELDS;
            default -> Set.of();
        };
        config.keySet().stream()
                .filter(field -> field == null || !KNOWN_CONFIG_FIELDS.contains(field)
                        || !capabilityFields.contains(field))
                .sorted(Comparator.nullsFirst(String::compareTo))
                .forEach(field -> reasons.add(reason(
                        LegacyHttpConversionReasonCode.UNKNOWN_CONFIG_FIELD, index, stageKey,
                        "存在无法安全归并的配置字段: " + safeField(field))));
        if (Boolean.FALSE.equals(step.enabled())) {
            return;
        }
        switch (step.capability()) {
            case "REQUEST_BUILDER" -> validateRequestBuilder(
                    index, stageKey, config, platformPolicy, reasons);
            case "REQUEST_PROCESSOR" -> validateRequestProcessor(index, stageKey, config, reasons);
            case "RESPONSE_PROCESSOR" -> validateResponseProcessor(index, stageKey, config, reasons);
            case "RESPONSE_NORMALIZER" -> validateSimpleMapping(
                    index, stageKey, config.get("responseMapping"),
                    "responseMapping", false, reasons);
            default -> {
                // TRANSPORT and RESPONSE_PARSER have no effective legacy configuration.
            }
        }
    }

    private void validateRequestBuilder(int index, String stageKey, Map<String, Object> config,
                                        LegacyHttpConversionPolicy platformPolicy,
                                        List<LegacyHttpConversionReason> reasons) {
        Object endpoint = config.get("apiUrl");
        if (!(endpoint instanceof String value) || value.isBlank() || !isHttpsUri(value)) {
            reasons.add(reason(LegacyHttpConversionReasonCode.REQUEST_ENDPOINT_MISSING,
                    index, stageKey, "apiUrl 缺失或不是无查询参数的绝对 HTTPS 地址"));
        }
        Object method = config.get("method");
        if (method != null && (!(method instanceof String value)
                || !value.equals(value.trim())
                || !HTTP_METHODS.contains(value.toUpperCase(Locale.ROOT)))) {
            unsupported(index, stageKey, "method", reasons);
        }
        validateHeaders(index, stageKey, config.get("headers"), reasons);
        validateJsonContentType(index, stageKey, config.get("contentType"), reasons);
        validateSimpleMapping(index, stageKey, config.get("requestMapping"),
                "requestMapping", true, reasons);
        positiveNumber(index, stageKey, config.get("connectTimeoutMs"), "connectTimeoutMs", reasons);
        positiveNumber(index, stageKey, config.get("readTimeoutMs"), "readTimeoutMs", reasons);
        positiveNumber(index, stageKey, config.get("totalTimeoutMs"), "totalTimeoutMs", reasons);
        positiveNumber(index, stageKey, config.get("maxResponseBytes"), "maxResponseBytes", reasons);
        requireString(index, stageKey, config.get("idempotencyKey"), "idempotencyKey", reasons);
        Object policy = config.get("idempotencyPolicy");
        if (policy != null && (!(policy instanceof String value)
                || !IDEMPOTENCY_POLICIES.contains(value))) {
            unsupported(index, stageKey, "idempotencyPolicy", reasons);
        }
        validatePlatformPolicy(index, stageKey, config, platformPolicy, reasons);
        validateIdempotencySemantics(index, stageKey, config, reasons);
    }

    private void validateRequestProcessor(int index, String stageKey, Map<String, Object> config,
                                          List<LegacyHttpConversionReason> reasons) {
        Object authType = config.get("authType");
        if (authType != null && (!(authType instanceof String value)
                || !AUTH_TYPES.contains(value.toUpperCase()))) {
            unsupported(index, stageKey, "authType", reasons);
        }
        requireMap(index, stageKey, config.get("authConfig"), "authConfig", reasons);
        rejectNonEmpty(index, stageKey, config.get("secretRefs"), "secretRefs", reasons);
        rejectNonEmpty(index, stageKey, config.get("securitySteps"), "securitySteps", reasons);
        rejectNonEmpty(index, stageKey, config.get("legacySecretAlias"), "legacySecretAlias", reasons);
        validateAuthConfig(index, stageKey, config, reasons);
        validateSecretAliases(index, stageKey, config, reasons);
        try {
            GenericHttpConnectorConfigValidator.validate(MAPPER.valueToTree(Map.of(
                    "endpoint", "https://conversion.invalid/", "method", "POST",
                    "auth", convertedAuth(config))), ignored -> true);
        } catch (RuntimeException exception) {
            unsupported(index, stageKey, "authConfig", reasons);
        }
    }

    private void validateResponseProcessor(int index, String stageKey, Map<String, Object> config,
                                           List<LegacyHttpConversionReason> reasons) {
        rejectNonEmpty(index, stageKey, config.get("secretRefs"), "secretRefs", reasons);
        rejectNonEmpty(index, stageKey, config.get("securitySteps"), "securitySteps", reasons);
        rejectNonEmpty(index, stageKey, config.get("legacySecretAlias"), "legacySecretAlias", reasons);
    }

    private void validatePlatformPolicy(int index, String stageKey, Map<String, Object> config,
                                        LegacyHttpConversionPolicy platformPolicy,
                                        List<LegacyHttpConversionReason> reasons) {
        if (platformPolicy == null) {
            reasons.add(reason(LegacyHttpConversionReasonCode.PLATFORM_POLICY_REQUIRED,
                    index, stageKey, "缺少厂商平台 timeout，无法证明删除步骤策略后行为等价"));
        } else if (!matchesTimeout(config.get("connectTimeoutMs"), 5_000, platformPolicy.timeoutMs())
                || !matchesTimeout(config.get("readTimeoutMs"), 30_000, platformPolicy.timeoutMs())
                || !matchesTimeout(config.get("totalTimeoutMs"), 30_000, platformPolicy.timeoutMs())) {
            reasons.add(reason(LegacyHttpConversionReasonCode.PLATFORM_TIMEOUT_MISMATCH,
                    index, stageKey, "步骤 timeout 与平台唯一 timeout 不一致"));
        }
        Object maxResponseBytes = config.get("maxResponseBytes");
        long effectiveLimit = maxResponseBytes instanceof Number number
                ? number.longValue() : PLATFORM_MAX_RESPONSE_BYTES;
        if (effectiveLimit != PLATFORM_MAX_RESPONSE_BYTES) {
            reasons.add(reason(LegacyHttpConversionReasonCode.RESPONSE_LIMIT_UNSUPPORTED,
                    index, stageKey, "响应大小上限不是平台固定值，不能无损删除"));
        }
    }

    private boolean matchesTimeout(Object configured, long defaultValue, int platformTimeoutMs) {
        long effective = configured instanceof Number number ? number.longValue() : defaultValue;
        return effective == platformTimeoutMs;
    }

    private void validateIdempotencySemantics(int index, String stageKey,
                                              Map<String, Object> config,
                                              List<LegacyHttpConversionReason> reasons) {
        String method = config.get("method") instanceof String value
                ? value.toUpperCase(Locale.ROOT) : "POST";
        String policy = config.get("idempotencyPolicy") instanceof String value
                ? value : "IDEMPOTENT";
        String key = config.get("idempotencyKey") instanceof String value ? value : null;
        boolean supported = switch (method) {
            case "GET" -> "IDEMPOTENT".equals(policy) && (key == null || key.isBlank());
            case "POST", "PUT", "PATCH", "DELETE" ->
                    "NON_IDEMPOTENT".equals(policy) && (key == null || key.isBlank());
            default -> false;
        };
        if (!supported) {
            reasons.add(reason(LegacyHttpConversionReasonCode.IDEMPOTENCY_SEMANTICS_UNSUPPORTED,
                    index, stageKey, "步骤幂等语义不能由 HTTP 方法和幂等键安全推导"));
        }
    }

    private void validateAuthConfig(int index, String stageKey, Map<String, Object> config,
                                    List<LegacyHttpConversionReason> reasons) {
        String authType = config.get("authType") instanceof String value
                ? value.toUpperCase() : "NONE";
        if (!(config.get("authConfig") instanceof Map<?, ?> authConfig)) {
            return;
        }
        Set<String> allowed = switch (authType) {
            case "BEARER" -> Set.of("token");
            case "BASIC" -> Set.of("username", "password");
            case "API_KEY" -> Set.of("apiKeyName", "apiKeyValue", "apiKeyLocation");
            default -> Set.of();
        };
        authConfig.keySet().stream()
                .map(String::valueOf)
                .filter(field -> !allowed.contains(field))
                .sorted()
                .forEach(field -> reasons.add(reason(
                        LegacyHttpConversionReasonCode.UNKNOWN_CONFIG_FIELD, index, stageKey,
                        "存在无法安全归并的认证字段: authConfig." + safeField(field))));
        switch (authType) {
            case "BEARER" -> requireSecretReference(
                    index, stageKey, authConfig.get("token"), "authConfig.token", reasons);
            case "BASIC" -> {
                requireSecretReference(
                        index, stageKey, authConfig.get("username"), "authConfig.username", reasons);
                requireSecretReference(
                        index, stageKey, authConfig.get("password"), "authConfig.password", reasons);
            }
            case "API_KEY" -> {
                requireNonBlankText(
                        index, stageKey, authConfig.get("apiKeyName"), "authConfig.apiKeyName", reasons);
                requireSecretReference(
                        index, stageKey, authConfig.get("apiKeyValue"), "authConfig.apiKeyValue", reasons);
                Object location = authConfig.get("apiKeyLocation");
                if (location != null && (!(location instanceof String value)
                        || !Set.of("header", "query").contains(value.toLowerCase()))) {
                    unsupported(index, stageKey, "authConfig.apiKeyLocation", reasons);
                } else if ("query".equalsIgnoreCase(String.valueOf(location))) {
                    unsupported(index, stageKey, "authConfig.apiKeyLocation", reasons);
                }
            }
            default -> {
                // NONE has no effective authentication fields.
            }
        }
    }

    private void validateSecretAliases(int index, String stageKey, Map<String, Object> config,
                                       List<LegacyHttpConversionReason> reasons) {
        if (!(config.get("secretRefs") instanceof Map<?, ?> aliases)) {
            return;
        }
        aliases.forEach((alias, reference) -> {
            if (!(alias instanceof String name) || name.isBlank()
                    || !(reference instanceof String ref) || ref.isBlank()) {
                reasons.add(reason(LegacyHttpConversionReasonCode.SECRET_REFERENCE_REQUIRED,
                        index, stageKey, "secretRefs 必须只包含非空别名和 SecretRef"));
            }
        });
        Object selectedAlias = config.get("legacySecretAlias");
        if (selectedAlias instanceof String value && !value.isBlank() && !aliases.containsKey(value)) {
            reasons.add(reason(LegacyHttpConversionReasonCode.SECRET_REFERENCE_REQUIRED,
                    index, stageKey, "legacySecretAlias 未绑定到当前步骤的 SecretRef"));
        }
    }

    private void requireSecretReference(int index, String stageKey, Object value, String field,
                                        List<LegacyHttpConversionReason> reasons) {
        if (!(value instanceof Map<?, ?> reference) || reference.size() != 1
                || !(reference.get("secretRef") instanceof String ref) || ref.isBlank()) {
            reasons.add(reason(LegacyHttpConversionReasonCode.SECRET_REFERENCE_REQUIRED,
                    index, stageKey, field + " 必须使用 SecretRef，不能携带明文"));
        }
    }

    private void requireNonBlankText(int index, String stageKey, Object value, String field,
                                     List<LegacyHttpConversionReason> reasons) {
        if (!(value instanceof String text) || text.isBlank()) {
            unsupported(index, stageKey, field, reasons);
        }
    }

    private void validateJsonContentType(int index, String stageKey, Object value,
                                         List<LegacyHttpConversionReason> reasons) {
        String contentType = value == null ? GenericHttpConnectorConfigValidator.JSON_CONTENT_TYPE
                : value instanceof String text ? text.trim().toLowerCase(Locale.ROOT) : null;
        if (!Set.of("application/json", GenericHttpConnectorConfigValidator.JSON_CONTENT_TYPE)
                .contains(contentType)) {
            unsupported(index, stageKey, "contentType", reasons);
        }
    }

    private void validateHeaders(int index, String stageKey, Object value,
                                 List<LegacyHttpConversionReason> reasons) {
        if (value == null) return;
        if (!(value instanceof Map<?, ?> headers)) {
            unsupported(index, stageKey, "headers", reasons);
            return;
        }
        LinkedHashMap<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("endpoint", "https://conversion.invalid/");
        candidate.put("method", "POST");
        candidate.put("auth", Map.of("type", "NONE"));
        List<Map<String, Object>> converted = new ArrayList<>();
        headers.entrySet().stream().sorted(Comparator.comparing(
                entry -> String.valueOf(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    if (!(entry.getKey() instanceof String name)
                            || !(entry.getValue() instanceof String headerValue)) {
                        unsupported(index, stageKey, "headers", reasons);
                    } else {
                        converted.add(Map.of("name", name, "value", headerValue));
                    }
                });
        candidate.put("headers", converted);
        try {
            GenericHttpConnectorConfigValidator.validate(MAPPER.valueToTree(candidate), ignored -> true);
        } catch (IllegalArgumentException exception) {
            unsupported(index, stageKey, "headers", reasons);
        }
    }

    private void validateSimpleMapping(int index, String stageKey, Object value, String field,
                                       boolean request, List<LegacyHttpConversionReason> reasons) {
        if (value == null) return;
        if (!(value instanceof Map<?, ?> mapping)) {
            unsupported(index, stageKey, field, reasons);
            return;
        }
        if (!request && mapping.isEmpty()) {
            unsupported(index, stageKey, field, reasons);
            return;
        }
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            if (!(entry.getKey() instanceof String source)
                    || !(entry.getValue() instanceof String target)
                    || !validPath(source) || !validPath(target)
                    || request && (!source.equals(target) || source.contains("."))
                    || !request && target.contains(".")
                    || !sources.add(source) || targets.stream().anyMatch(existing -> overlaps(existing, target))) {
                unsupported(index, stageKey, field, reasons);
                return;
            }
            targets.add(target);
            entries.add(Map.entry(source, target));
        }
        if (request) {
            for (Map.Entry<String, String> left : entries) {
                for (Map.Entry<String, String> right : entries) {
                    if (left == right || left.getKey().equals(left.getValue())) continue;
                    if (overlaps(left.getValue(), right.getKey())) {
                        unsupported(index, stageKey, field, reasons);
                        return;
                    }
                }
            }
        }
    }

    private void rejectNonEmpty(int index, String stageKey, Object value, String field,
                                List<LegacyHttpConversionReason> reasons) {
        boolean empty = value == null
                || value instanceof Map<?, ?> map && map.isEmpty()
                || value instanceof List<?> list && list.isEmpty()
                || value instanceof String text && text.isBlank();
        if (!empty) unsupported(index, stageKey, field, reasons);
    }

    private void requireMap(int index, String stageKey, Object value, String field,
                            List<LegacyHttpConversionReason> reasons) {
        if (value != null && !(value instanceof Map<?, ?>)) {
            unsupported(index, stageKey, field, reasons);
        }
    }

    private void requireString(int index, String stageKey, Object value, String field,
                               List<LegacyHttpConversionReason> reasons) {
        if (value != null && !(value instanceof String)) {
            unsupported(index, stageKey, field, reasons);
        }
    }

    private void positiveNumber(int index, String stageKey, Object value, String field,
                                List<LegacyHttpConversionReason> reasons) {
        if (value != null && (!(value instanceof Number number) || number.longValue() <= 0)) {
            unsupported(index, stageKey, field, reasons);
        }
    }

    private void unsupported(int index, String stageKey, String field,
                             List<LegacyHttpConversionReason> reasons) {
        reasons.add(reason(LegacyHttpConversionReasonCode.CONFIG_VALUE_UNSUPPORTED,
                index, stageKey, "配置字段类型或取值无法安全归并: " + field));
    }

    private boolean isHttpsUri(String value) {
        try {
            if (value.length() > 2048 || containsEncodedControl(value)) return false;
            URI uri = URI.create(value);
            return uri.isAbsolute() && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null && uri.getFragment() == null
                    && uri.getRawQuery() == null && "https".equalsIgnoreCase(uri.getScheme())
                    && value.codePoints().noneMatch(character -> Character.isISOControl(character));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean containsEncodedControl(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (int index = 0; index + 2 < normalized.length(); index++) {
            if (normalized.charAt(index) != '%') continue;
            try {
                int decoded = Integer.parseInt(normalized.substring(index + 1, index + 3), 16);
                if (decoded < 0x20 || decoded == 0x7f) return true;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        return false;
    }

    private void validateCrossStage(List<IndexedStep> enabled,
                                    List<LegacyHttpConversionReason> reasons) {
        ConnectorPipelineStepDTO builder = enabled.stream()
                .map(IndexedStep::step).filter(step -> "REQUEST_BUILDER".equals(step.capability()))
                .findFirst().orElse(null);
        ConnectorPipelineStepDTO processor = enabled.stream()
                .map(IndexedStep::step).filter(step -> "REQUEST_PROCESSOR".equals(step.capability()))
                .findFirst().orElse(null);
        if (builder == null || processor == null) return;
        Map<String, Object> auth = processor.config() == null ? Map.of() : processor.config();
        String type = auth.get("authType") instanceof String text
                ? text.toUpperCase(Locale.ROOT) : "NONE";
        if (!"API_KEY".equals(type) || !(auth.get("authConfig") instanceof Map<?, ?> authConfig)) return;
        Object location = authConfig.get("apiKeyLocation");
        Object keyName = authConfig.get("apiKeyName");
        if (!"header".equalsIgnoreCase(String.valueOf(location)) || !(keyName instanceof String name)) return;
        if (builder.config() != null && builder.config().get("headers") instanceof Map<?, ?> headers
                && headers.keySet().stream().anyMatch(header ->
                header instanceof String text && text.equalsIgnoreCase(name))) {
            reasons.add(reason(LegacyHttpConversionReasonCode.CONFIG_VALUE_UNSUPPORTED,
                    null, safeStageKey(processor.stageKey()),
                    "认证头与固定请求头冲突，无法证明转换等价"));
        }
    }

    private String method(Map<String, Object> builder) {
        return builder.get("method") instanceof String value
                ? value.toUpperCase(Locale.ROOT) : "POST";
    }

    private List<Map<String, Object>> convertedHeaders(Object value) {
        if (!(value instanceof Map<?, ?> headers) || headers.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        headers.entrySet().stream().sorted((left, right) -> {
            int compared = String.CASE_INSENSITIVE_ORDER.compare(
                    (String) left.getKey(), (String) right.getKey());
            return compared != 0 ? compared
                    : ((String) left.getKey()).compareTo((String) right.getKey());
        })
                .forEach(entry -> result.add(Map.of(
                        "name", (String) entry.getKey(), "value", (String) entry.getValue())));
        return List.copyOf(result);
    }

    private List<Map<String, Object>> convertedRequestMapping(Object value) {
        if (!(value instanceof Map<?, ?> mapping) || mapping.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        mapping.entrySet().stream().map(entry -> Map.entry(
                        (String) entry.getKey(), (String) entry.getValue()))
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(Map.of(
                        "sourceField", entry.getKey(),
                        "targetField", entry.getValue(),
                        "required", false,
                        "transformType", "none")));
        return List.copyOf(result);
    }

    private Map<String, Object> convertedAuth(Map<String, Object> requestProcessor) {
        String type = requestProcessor.get("authType") instanceof String text
                ? text.toUpperCase(Locale.ROOT) : "NONE";
        if ("NONE".equals(type)) return Map.of("type", "NONE");
        Map<?, ?> config = (Map<?, ?>) requestProcessor.get("authConfig");
        return switch (type) {
            case "BEARER" -> Map.of("type", "BEARER",
                    "tokenRef", secretRef(config.get("token")));
            case "BASIC" -> Map.of("type", "BASIC",
                    "usernameRef", secretRef(config.get("username")),
                    "passwordRef", secretRef(config.get("password")));
            case "API_KEY" -> Map.of("type", "API_KEY",
                    "keyName", (String) config.get("apiKeyName"),
                    "keyRef", secretRef(config.get("apiKeyValue")),
                    "location", "header");
            default -> throw new IllegalStateException("LEGACY_PIPELINE_NOT_CONVERTIBLE");
        };
    }

    private String secretRef(Object value) {
        return (String) ((Map<?, ?>) value).get("secretRef");
    }

    private List<ConnectorSpecDTO.ResponseMapping> convertedResponseMapping(Object value) {
        if (!(value instanceof Map<?, ?> mapping) || mapping.isEmpty()) return List.of();
        List<ConnectorSpecDTO.ResponseMapping> result = new ArrayList<>();
        mapping.entrySet().stream().map(entry -> Map.entry(
                        (String) entry.getKey(), (String) entry.getValue()))
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(
                        new ConnectorSpecDTO.ResponseMapping(entry.getValue(), entry.getKey(),
                                "field", null, "none")));
        return List.copyOf(result);
    }

    private boolean validPath(String path) {
        if (path.isBlank() || !path.equals(path.trim()) || path.length() > 256) return false;
        String[] segments = path.split("\\.", -1);
        if (segments.length == 0 || segments.length > 32) return false;
        for (String segment : segments) {
            if (!PATH_SEGMENT.matcher(segment).matches()
                    || FORBIDDEN_PATH_SEGMENTS.contains(segment.toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    private boolean overlaps(String left, String right) {
        return left.equals(right) || left.startsWith(right + ".") || right.startsWith(left + ".");
    }

    private LegacyHttpConversionPreflightResult legacy(LegacyHttpConversionReason reason) {
        return new LegacyHttpConversionPreflightResult(
                LegacyHttpConversionClassification.MUST_REMAIN_LEGACY, List.of(reason));
    }

    private LegacyHttpConversionReason reason(LegacyHttpConversionReasonCode code,
                                              Integer index, String stageKey, String detail) {
        return new LegacyHttpConversionReason(code, index, stageKey, detail);
    }

    private String safeStageKey(String stageKey) {
        if (stageKey == null) {
            return null;
        }
        String normalized = stageKey.replaceAll("[^A-Za-z0-9_.:-]", "?");
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private String safeField(String field) {
        if (field == null) {
            return "<null>";
        }
        String normalized = field.replaceAll("[^A-Za-z0-9_.-]", "?");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private record IndexedStep(int index, ConnectorPipelineStepDTO step) {
    }
}
