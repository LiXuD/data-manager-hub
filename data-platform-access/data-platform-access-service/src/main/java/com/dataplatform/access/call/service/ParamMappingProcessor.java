package com.dataplatform.access.call.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 请求参数映射处理器
 * 负责将接口级参数定义 + 厂商级映射配置 → 组装为厂商请求参数
 */
@Component
public class ParamMappingProcessor {

    private static final Logger log = LoggerFactory.getLogger(ParamMappingProcessor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 参数映射条目
     */
    public static class MappingEntry {
        private String paramName;
        private String targetField;
        private String transformExpr;

        public String getParamName() { return paramName; }
        public void setParamName(String paramName) { this.paramName = paramName; }
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
        public String getTransformExpr() { return transformExpr; }
        public void setTransformExpr(String transformExpr) { this.transformExpr = transformExpr; }
    }

    /**
     * 参数定义条目
     */
    public static class ParamDefinition {
        private String paramName;
        private String paramType;
        private Boolean required;
        private String defaultValue;
        private String description;

        public String getParamName() { return paramName; }
        public void setParamName(String paramName) { this.paramName = paramName; }
        public String getParamType() { return paramType; }
        public void setParamType(String paramType) { this.paramType = paramType; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * 组装厂商请求参数
     *
     * @param paramDefinitions 接口参数定义列表
     * @param paramMappingJson 映射配置 JSON 字符串 (vendor_config.param_mapping)
     * @param inputParams      调用方输入的原始参数
     * @return 组装好的厂商请求参数
     */
    public Map<String, Object> buildVendorRequest(List<ParamDefinition> paramDefinitions,
                                                   String paramMappingJson,
                                                   Map<String, Object> inputParams) {
        // 1. 解析映射配置
        Map<String, MappingEntry> mappingMap = parseMapping(paramMappingJson);

        // 2. 构建索引：paramName → ParamDefinition
        Map<String, ParamDefinition> paramDefMap = new HashMap<>();
        if (paramDefinitions != null) {
            for (ParamDefinition def : paramDefinitions) {
                paramDefMap.put(def.getParamName(), def);
            }
        }

        // 3. 遍历参数定义，组装请求体
        Map<String, Object> vendorRequest = new LinkedHashMap<>();

        // 先处理参数定义中的字段（按定义顺序）
        if (paramDefinitions != null) {
            for (ParamDefinition def : paramDefinitions) {
                String paramName = def.getParamName();
                Object value = inputParams != null ? inputParams.get(paramName) : null;

                // 填充默认值
                if (value == null && def.getDefaultValue() != null) {
                    value = def.getDefaultValue();
                }

                // 必填校验
                if (value == null && Boolean.TRUE.equals(def.getRequired())) {
                    throw new IllegalArgumentException("缺少必填参数: " + paramName);
                }

                // 决定输出字段名
                MappingEntry mapping = mappingMap.get(paramName);
                String outputField;
                if (mapping != null && mapping.getTargetField() != null && !mapping.getTargetField().isEmpty()) {
                    // 有映射 → 使用 targetField
                    outputField = mapping.getTargetField();
                } else {
                    // 无映射 → 使用原始 paramName
                    outputField = paramName;
                }

                if (value != null) {
                    vendorRequest.put(outputField, value);
                }
            }
        }

        // 处理输入中存在但参数定义中不存在的额外字段（透传）
        if (inputParams != null) {
            for (Map.Entry<String, Object> entry : inputParams.entrySet()) {
                String key = entry.getKey();
                if (!vendorRequest.containsKey(key) && !paramDefMap.containsKey(key)) {
                    vendorRequest.put(key, entry.getValue());
                }
            }
        }

        return vendorRequest;
    }

    /**
     * 解析 param_mapping JSON 字符串为 Map<String, MappingEntry>
     */
    private Map<String, MappingEntry> parseMapping(String paramMappingJson) {
        Map<String, MappingEntry> result = new HashMap<>();
        if (paramMappingJson == null || paramMappingJson.trim().isEmpty()) {
            return result;
        }
        try {
            List<MappingEntry> entries = objectMapper.readValue(paramMappingJson,
                    new TypeReference<List<MappingEntry>>() {});
            if (entries != null) {
                for (MappingEntry entry : entries) {
                    if (entry.getParamName() != null) {
                        result.put(entry.getParamName(), entry);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析参数映射配置失败: {}", e.getMessage());
        }
        return result;
    }
}
