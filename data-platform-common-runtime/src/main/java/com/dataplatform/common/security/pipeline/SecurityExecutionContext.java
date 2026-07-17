package com.dataplatform.common.security.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public class SecurityExecutionContext {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SecurityDirection direction;
    private final Map<String, Object> params;
    private final Map<String, String> headers;
    private final Map<String, String> query;
    private final Map<String, Object> results = new LinkedHashMap<>();
    private final Map<String, String> secrets;
    private String body;
    private Object lastResult;

    public SecurityExecutionContext(SecurityDirection direction,
                                    Map<String, Object> params,
                                    Map<String, String> headers,
                                    Map<String, String> query,
                                    Map<String, String> secrets) {
        this.direction = direction;
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        this.query = query == null ? new LinkedHashMap<>() : new LinkedHashMap<>(query);
        this.secrets = secrets == null ? Map.of() : new LinkedHashMap<>(secrets);
    }

    public SecurityDirection getDirection() { return direction; }
    public Map<String, Object> getParams() { return params; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getQuery() { return query; }
    public Map<String, Object> getResults() { return results; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Object getLastResult() { return lastResult; }

    public void record(String stepId, Object value) {
        if (stepId != null && !stepId.isBlank()) {
            results.put(stepId, value);
        }
        lastResult = value;
    }

    public String resolveSecret(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new IllegalArgumentException("密钥引用不能为空");
        }
        String value = secrets.get(secretRef);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("未找到密钥引用: " + secretRef);
        }
        return value;
    }

    public Object resolveInput(String inputFrom) {
        if (inputFrom == null || inputFrom.isBlank() || "LAST".equalsIgnoreCase(inputFrom)) {
            return lastResult != null ? lastResult : params;
        }
        int separator = inputFrom.indexOf('.');
        if (separator > 0) {
            String scope = inputFrom.substring(0, separator).toUpperCase();
            String key = inputFrom.substring(separator + 1);
            return switch (scope) {
                case "PARAMS", "MAPPED_PARAMS", "RESPONSE" -> params.get(key);
                case "HEADERS" -> headers.get(key);
                case "QUERY" -> query.get(key);
                case "RESULT" -> results.get(key);
                default -> throw new IllegalArgumentException("不支持的步骤输入范围: " + scope);
            };
        }
        return switch (inputFrom.toUpperCase()) {
            case "PARAMS", "MAPPED_PARAMS", "RESPONSE" -> params;
            case "BODY", "RESPONSE_BODY" -> body == null ? serializeParams() : body;
            case "HEADERS" -> headers;
            case "QUERY" -> query;
            default -> {
                if (!results.containsKey(inputFrom)) {
                    throw new IllegalArgumentException("步骤输入不存在: " + inputFrom);
                }
                yield results.get(inputFrom);
            }
        };
    }

    private String serializeParams() {
        try {
            return OBJECT_MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("安全流水线参数无法序列化为Body", e);
        }
    }
}
