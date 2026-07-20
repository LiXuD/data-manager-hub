package com.dataplatform.common.security.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;

public class SecurityStepConfig {

    private String id;
    private SecurityDirection direction = SecurityDirection.REQUEST;
    private SecurityStepType stepType;
    private String stepName;
    private Integer sortNo = 100;
    private Boolean enabled = true;
    private Map<String, Object> config = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public SecurityDirection getDirection() { return direction; }
    public void setDirection(SecurityDirection direction) { this.direction = direction; }
    public SecurityStepType getStepType() { return stepType; }
    public void setStepType(SecurityStepType stepType) { this.stepType = stepType; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) {
        this.config = config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }
}
