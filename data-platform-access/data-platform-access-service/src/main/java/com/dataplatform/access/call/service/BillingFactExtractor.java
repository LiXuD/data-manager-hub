package com.dataplatform.access.call.service;

import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import com.dataplatform.billing.api.dto.BillingAdditionalPlanDTO;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 按 Billing 下发的有限选择器提取最小计量事实。
 * 不支持脚本，只支持对象路径和数组下标，避免在 Access 执行不可信表达式。
 */
@Component
public class BillingFactExtractor {

    private static final Pattern TOKEN = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)]");

    public Map<String, Object> extract(BillingMeteringPolicyDTO policy,
                                       Map<String, Object> normalizedResponse,
                                       Map<String, Object> requestParams) {
        Map<String, Object> facts = new LinkedHashMap<>();
        return extract(policy != null ? policy.getSelectors() : null, normalizedResponse, requestParams);
    }

    public Map<String, Object> extract(BillingAdditionalPlanDTO plan,
                                       Map<String, Object> normalizedResponse,
                                       Map<String, Object> requestParams) {
        return extract(plan != null ? plan.getSelectors() : null, normalizedResponse, requestParams);
    }

    private Map<String, Object> extract(List<BillingMeteringPolicyDTO.SelectorDTO> selectors,
                                        Map<String, Object> normalizedResponse,
                                        Map<String, Object> requestParams) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (selectors == null) return facts;
        for (BillingMeteringPolicyDTO.SelectorDTO selector : selectors) {
            Object root = "REQUEST".equalsIgnoreCase(selector.getSource())
                    ? requestParams : normalizedResponse;
            Lookup lookup = lookup(root, selector.getPath());
            if (!lookup.present()) continue;
            facts.put(selector.getAlias(), extractValue(lookup.value(), selector.getExtraction()));
        }
        return facts;
    }

    private Object extractValue(Object value, String extraction) {
        String mode = extraction == null ? "VALUE" : extraction.toUpperCase();
        return switch (mode) {
            case "ARRAY_SIZE" -> size(value);
            case "EXISTS" -> true;
            default -> scalarValue(value);
        };
    }

    private Object scalarValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Collection<?> collection) return collection.size();
        if (value.getClass().isArray()) return Array.getLength(value);
        throw new IllegalArgumentException("计量VALUE选择器只能提取标量字段");
    }

    private int size(Object value) {
        if (value instanceof Collection<?> collection) return collection.size();
        if (value != null && value.getClass().isArray()) return Array.getLength(value);
        throw new IllegalArgumentException("ARRAY_SIZE选择器目标不是数组");
    }

    @SuppressWarnings("unchecked")
    private Lookup lookup(Object root, String path) {
        if (root == null || path == null || path.isBlank()) return new Lookup(false, null);
        String normalized = path.startsWith("$.") ? path.substring(2)
                : path.startsWith("$") ? path.substring(1) : path;
        Object current = root;
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                if (!(current instanceof Map<?, ?> map) || !map.containsKey(matcher.group(1))) {
                    return new Lookup(false, null);
                }
                current = ((Map<String, Object>) map).get(matcher.group(1));
            } else {
                int index = Integer.parseInt(matcher.group(2));
                if (current instanceof List<?> list) {
                    if (index >= list.size()) return new Lookup(false, null);
                    current = list.get(index);
                } else if (current != null && current.getClass().isArray()) {
                    if (index >= Array.getLength(current)) return new Lookup(false, null);
                    current = Array.get(current, index);
                } else {
                    return new Lookup(false, null);
                }
            }
        }
        return new Lookup(true, current);
    }

    private record Lookup(boolean present, Object value) {
    }
}
