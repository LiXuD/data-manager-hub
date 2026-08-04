package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;

/** Requests that an Access instance preload and verify one immutable plugin version. */
public class ConnectorPluginStageReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pluginId;
    private String pluginVersion;

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public void setPluginVersion(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }
}
