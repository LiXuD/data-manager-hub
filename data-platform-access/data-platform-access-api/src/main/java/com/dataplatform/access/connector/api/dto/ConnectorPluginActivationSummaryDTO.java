package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Aggregated Access activation state for a plugin version. */
public class ConnectorPluginActivationSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pluginId;
    private String pluginVersion;
    private Boolean ready;
    private List<ConnectorPluginActivationDTO> instances = new ArrayList<>();

    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getPluginVersion() { return pluginVersion; }
    public void setPluginVersion(String pluginVersion) { this.pluginVersion = pluginVersion; }
    public Boolean getReady() { return ready; }
    public void setReady(Boolean ready) { this.ready = ready; }
    public List<ConnectorPluginActivationDTO> getInstances() { return instances; }
    public void setInstances(List<ConnectorPluginActivationDTO> instances) {
        this.instances = instances != null ? instances : new ArrayList<>();
    }
}
