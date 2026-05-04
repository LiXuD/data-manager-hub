package com.dataplatform.common.mapping;

public class RequestMappingItem {

    private String targetField;
    private String sourceVar;
    private String defaultValue;
    private Boolean required = true;
    private String transformType = "none";

    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }
    public String getSourceVar() { return sourceVar; }
    public void setSourceVar(String sourceVar) { this.sourceVar = sourceVar; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public String getTransformType() { return transformType; }
    public void setTransformType(String transformType) { this.transformType = transformType; }
}
