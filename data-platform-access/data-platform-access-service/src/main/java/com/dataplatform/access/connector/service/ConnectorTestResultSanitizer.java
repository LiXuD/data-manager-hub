package com.dataplatform.access.connector.service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Produces a bounded management-safe view without exposing secrets or raw transport objects. */
@Component
public class ConnectorTestResultSanitizer {

    private static final int MAX_DEPTH = 8;
    private static final int MAX_COLLECTION_SIZE = 200;
    private static final int MAX_STRING_LENGTH = 1_024;

    public Map<String, Object> sanitize(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Object sanitized = sanitizeValue(source, 0, null);
        if (sanitized instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return Collections.unmodifiableMap(result);
        }
        return Map.of();
    }

    private Object sanitizeValue(Object value, int depth, String fieldName) {
        if (sensitive(fieldName)) return "***";
        if (value == null) return "[null]";
        if (value instanceof Number || value instanceof Boolean) return value;
        if (depth >= MAX_DEPTH) return "[truncated]";
        if (value instanceof CharSequence chars) {
            String text = chars.toString();
            return text.length() <= MAX_STRING_LENGTH ? text : text.substring(0, MAX_STRING_LENGTH) + "…";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_COLLECTION_SIZE) break;
                String key = String.valueOf(entry.getKey());
                result.put(key, sanitizeValue(entry.getValue(), depth + 1, key));
            }
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                if (result.size() >= MAX_COLLECTION_SIZE) break;
                result.add(sanitizeValue(item, depth + 1, fieldName));
            }
            return result;
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int index = 0; index < Math.min(Array.getLength(value), MAX_COLLECTION_SIZE); index++) {
                result.add(sanitizeValue(Array.get(value, index), depth + 1, fieldName));
            }
            return result;
        }
        return "[unsupported]";
    }

    private boolean sensitive(String fieldName) {
        if (fieldName == null) return false;
        String key = fieldName.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return key.contains("password") || key.contains("passwd") || key.contains("secret")
                || key.contains("token") || key.contains("authorization") || key.contains("credential")
                || key.contains("privatekey") || key.equals("apikey") || key.endsWith("apikey");
    }
}
