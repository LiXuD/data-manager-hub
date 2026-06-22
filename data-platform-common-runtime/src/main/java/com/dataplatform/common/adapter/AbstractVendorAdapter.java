package com.dataplatform.common.adapter;

import com.dataplatform.common.mapping.RequestMappingItem;
import com.dataplatform.common.mapping.RequestMappingProcessor;
import com.dataplatform.common.mapping.ResponseMappingItem;
import com.dataplatform.common.mapping.ResponseMappingProcessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共运行时层厂商适配的 Abstract Vendor Adapter。
 * <p>厂商适配组件，封装外部数据源调用、认证和结果转换逻辑。</p>
 */
public abstract class AbstractVendorAdapter implements VendorAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper = new ObjectMapper();

    private final RequestMappingProcessor requestMappingProcessor = new RequestMappingProcessor();
    private final ResponseMappingProcessor responseMappingProcessor = new ResponseMappingProcessor();

    @Override
    public Map<String, Object> transformRequest(Map<String, Object> params, String mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return new HashMap<>(params);
        }

        try {
            // 先尝试解析为复杂包裹格式 {"requestMapping": [...], ...}
            Map<String, Object> wrapper = objectMapper.readValue(mapping,
                new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawItems = (List<Map<String, Object>>) wrapper.get("requestMapping");
            if (rawItems != null) {
                // 将 requestMapping 数组转换为 RequestMappingItem 列表
                List<RequestMappingItem> items = objectMapper.convertValue(rawItems,
                    new TypeReference<List<RequestMappingItem>>() {});
                return requestMappingProcessor.mapRequest(params, items);
            }
            if (wrapper.values().stream().allMatch(String.class::isInstance)) {
                return applySimpleFieldMapping(params, objectMapper.convertValue(wrapper,
                    new TypeReference<Map<String, String>>() {}));
            }
            // 对只有 body/contentType 等模板元数据、没有 requestMapping 的配置，保持原始参数
            return new HashMap<>(params);
        } catch (Exception e1) {
            try {
                List<RequestMappingItem> items = objectMapper.readValue(mapping,
                    new TypeReference<List<RequestMappingItem>>() {});
                return requestMappingProcessor.mapRequest(params, items);
            } catch (Exception e2) {
                try {
                    Map<String, String> fieldMapping = objectMapper.readValue(mapping,
                        new TypeReference<Map<String, String>>() {});
                    return applySimpleFieldMapping(params, fieldMapping);
                } catch (Exception e3) {
                    log.warn("请求参数转换失败, 使用原始参数: {}", e3.getMessage());
                    return new HashMap<>(params);
                }
            }
        }
    }

    @Override
    public Map<String, Object> transformResponse(Map<String, Object> response, String mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return new HashMap<>(response);
        }

        try {
            List<ResponseMappingItem> items = objectMapper.readValue(mapping,
                new TypeReference<List<ResponseMappingItem>>() {});
            return responseMappingProcessor.mapResponse(response, items);
        } catch (Exception e1) {
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
            } catch (Exception e2) {
                log.warn("响应数据转换失败, 使用原始响应: {}", e2.getMessage());
                return new HashMap<>(response);
            }
        }
    }

    private Map<String, Object> applySimpleFieldMapping(Map<String, Object> params, Map<String, String> fieldMapping) {
        Map<String, Object> transformed = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String targetField = fieldMapping.getOrDefault(entry.getKey(), entry.getKey());
            transformed.put(targetField, entry.getValue());
        }
        return transformed;
    }

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
