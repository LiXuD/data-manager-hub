package com.dataplatform.common.mapping;

/**
 * 公共运行时层参数映射的 Response Mapping Item。
 * <p>映射处理组件，负责请求或响应字段的规则化转换。</p>
 */
public class ResponseMappingItem {

    private String targetField;
    private String sourcePath;
    private String sourceType = "field";
    private Object defaultValue;
    private String transformType = "none";

    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Object getDefaultValue() { return defaultValue; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
    public String getTransformType() { return transformType; }
    public void setTransformType(String transformType) { this.transformType = transformType; }
}
