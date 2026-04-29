package com.dataplatform.common.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 厂商适配器抽象基类
 * 提供通用的参数转换和响应处理逻辑
 */
public abstract class AbstractVendorAdapter implements VendorAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> transformRequest(Map<String, Object> params, String mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return new HashMap<>(params);
        }

        try {
            Map<String, String> fieldMapping = objectMapper.readValue(mapping,
                new TypeReference<Map<String, String>>() {});

            Map<String, Object> transformed = new HashMap<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String targetField = fieldMapping.getOrDefault(entry.getKey(), entry.getKey());
                transformed.put(targetField, entry.getValue());
            }
            return transformed;
        } catch (Exception e) {
            log.warn("请求参数转换失败, 使用原始参数: {}", e.getMessage());
            return new HashMap<>(params);
        }
    }

    @Override
    public Map<String, Object> transformResponse(Map<String, Object> response, String mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return new HashMap<>(response);
        }

        try {
            Map<String, String> fieldMapping = objectMapper.readValue(mapping,
                new TypeReference<Map<String, String>>() {});

            Map<String, Object> transformed = new HashMap<>();
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                String sourcePath = entry.getKey();
                String targetField = entry.getValue();
                Object value = getNestedValue(response, sourcePath);
                if (value != null) {
                    transformed.put(targetField, value);
                }
            }
            return transformed;
        } catch (Exception e) {
            log.warn("响应数据转换失败, 使用原始响应: {}", e.getMessage());
            return new HashMap<>(response);
        }
    }

    /**
     * 从嵌套Map中获取值
     * 支持路径: "data.result.name"
     */
    protected Object getNestedValue(Map<String, Object> map, String path) {
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
}
