package com.dataplatform.common.mapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共运行时层参数映射的 Request Mapping Processor。
 * <p>映射处理组件，负责请求或响应字段的规则化转换。</p>
 */
public class RequestMappingProcessor {

    public Map<String, Object> mapRequest(Map<String, Object> request, List<RequestMappingItem> mappings) {
        Map<String, Object> result = new HashMap<>();
        if (mappings == null || mappings.isEmpty()) {
            return result;
        }

        Map<String, Object> safeRequest = request != null ? request : Map.of();

        for (RequestMappingItem item : mappings) {
            Object value = safeRequest.get(item.getSourceVar());

            if (value == null && !safeRequest.containsKey(item.getSourceVar())) {
                if (Boolean.TRUE.equals(item.getRequired()) && item.getDefaultValue() == null) {
                    throw new MappingException("缺少必填参数: " + item.getSourceVar(), item.getSourceVar());
                }
                if (item.getDefaultValue() != null) {
                    value = item.getDefaultValue();
                }
            }

            if (value != null) {
                value = transformValue(value, item.getTransformType());
            }

            if (value != null || safeRequest.containsKey(item.getSourceVar())) {
                result.put(item.getTargetField(), value);
            }
        }

        return result;
    }

    private Object transformValue(Object value, String transformType) {
        if (value == null || transformType == null || "none".equals(transformType)) {
            return value;
        }
        switch (transformType) {
            case "uppercase":
                return value.toString().toUpperCase();
            case "lowercase":
                return value.toString().toLowerCase();
            case "trim":
                return value.toString().trim();
            default:
                return value;
        }
    }
}
