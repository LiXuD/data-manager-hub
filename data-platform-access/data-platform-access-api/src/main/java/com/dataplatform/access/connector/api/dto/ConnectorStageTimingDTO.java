package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;

public class ConnectorStageTimingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String stageKey;
    private String capability;
    private String pluginId;
    private String pluginVersion;
    private Long durationMs;

    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }
    public String getCapability() { return capability; }
    public void setCapability(String capability) { this.capability = capability; }
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getPluginVersion() { return pluginVersion; }
    public void setPluginVersion(String pluginVersion) { this.pluginVersion = pluginVersion; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
