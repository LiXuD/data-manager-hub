package com.dataplatform.common.mapping;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseMappingProcessor {

    public Map<String, Object> mapResponse(Map<String, Object> response, List<ResponseMappingItem> mappings) {
        Map<String, Object> result = new HashMap<>();
        if (mappings == null || mappings.isEmpty() || response == null) {
            return result;
        }

        for (ResponseMappingItem item : mappings) {
            Object value = extractValue(response, item);

            if (value == null) {
                value = item.getDefaultValue();
            }

            if (value != null) {
                value = transformValue(value, item.getTransformType());
            }

            result.put(item.getTargetField(), value);
        }

        return result;
    }

    private Object extractValue(Map<String, Object> response, ResponseMappingItem item) {
        if ("jsonPath".equals(item.getSourceType())) {
            return extractByJsonPath(response, item.getSourcePath());
        } else {
            return getNestedValue(response, item.getSourcePath());
        }
    }

    private Object extractByJsonPath(Map<String, Object> data, String jsonPathExpr) {
        try {
            return JsonPath.read(data, jsonPathExpr);
        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Object getNestedValue(Map<String, Object> map, String path) {
        if (map == null || path == null) {
            return null;
        }

        String[] keys = path.split("\\.");
        Object current = map;

        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else {
                return null;
            }
        }

        return current;
    }

    private Object transformValue(Object value, String transformType) {
        if (value == null || transformType == null || "none".equals(transformType)) {
            return value;
        }
        switch (transformType) {
            case "toString":
                return value.toString();
            case "toNumber":
                return toNumber(value);
            default:
                return value;
        }
    }

    private Object toNumber(Object value) {
        if (value instanceof Number) {
            return value;
        }
        try {
            String str = value.toString().replace(",", "");
            return new BigDecimal(str);
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
