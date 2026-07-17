package com.dataplatform.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 日志敏感字段脱敏工具，避免认证凭据进入 HTTP 日志和操作日志。
 */
public final class SensitiveLogSanitizer {

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword",
            "apikey", "apisecret", "clientsecret", "secret",
            "token", "accesstoken", "refreshtoken", "authorization",
            "privatekey", "signingkey", "encryptionkey", "secretkey",
            "resolvedsecrets"
    );
    private static final String FIELD_PATTERN = SENSITIVE_FIELDS.stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
    private static final Pattern JSON_VALUE_PATTERN = Pattern.compile(
            "(?i)(\\\"(?:" + FIELD_PATTERN + ")\\\"\\s*:\\s*)"
                    + "(\\\"(?:\\\\.|[^\\\"])*\\\"|[^,}\\s]+)");
    private static final Pattern FORM_VALUE_PATTERN = Pattern.compile(
            "(?i)(^|[?&;\\s])((?:" + FIELD_PATTERN + ")=)([^&;\\s]*)");

    private SensitiveLogSanitizer() {
    }

    public static String sanitizeBody(String body, ObjectMapper objectMapper) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root != null) {
                redact(root);
                return objectMapper.writeValueAsString(root);
            }
        } catch (Exception ignored) {
            // 非 JSON 报文继续使用表单和文本规则脱敏。
        }
        return sanitizeText(body);
    }

    public static String sanitizeQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }
        return Arrays.stream(queryString.split("&", -1))
                .map(SensitiveLogSanitizer::sanitizeQueryPart)
                .collect(Collectors.joining("&"));
    }

    private static void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(entry -> {
                if (isSensitiveField(entry.getKey())) {
                    objectNode.put(entry.getKey(), MASK);
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(SensitiveLogSanitizer::redact);
        }
    }

    private static String sanitizeQueryPart(String part) {
        int separator = part.indexOf('=');
        if (separator < 0) {
            return part;
        }
        String rawName = part.substring(0, separator);
        String decodedName;
        try {
            decodedName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            decodedName = rawName;
        }
        return isSensitiveField(decodedName) ? rawName + "=" + MASK : part;
    }

    private static String sanitizeText(String text) {
        Matcher jsonMatcher = JSON_VALUE_PATTERN.matcher(text);
        String jsonSanitized = jsonMatcher.replaceAll("$1\\\"" + MASK + "\\\"");
        Matcher formMatcher = FORM_VALUE_PATTERN.matcher(jsonSanitized);
        return formMatcher.replaceAll("$1$2" + MASK);
    }

    private static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELDS.contains(normalized)
                || normalized.endsWith("secretkey")
                || normalized.endsWith("privatekey")
                || normalized.endsWith("signingkey")
                || normalized.endsWith("encryptionkey");
    }
}
