package com.dataplatform.access.connector.service;

import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;

/** Access-facing port implemented by the common connector runtime. */
public interface ConnectorPluginRuntimeOperations {

    void preload(PluginArtifactDescriptorDTO artifact);

    boolean release(String pluginId, String pluginVersion);

    boolean isLoaded(String pluginId, String pluginVersion);

    int loadedVersionCount();
}
