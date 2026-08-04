package com.dataplatform.access.connector.runtime;

import com.dataplatform.access.connector.artifact.ConnectorPluginArtifactCache;
import com.dataplatform.access.connector.service.ConnectorPluginRuntimeOperations;
import com.dataplatform.common.plugin.artifact.PluginArtifactCoordinates;
import com.dataplatform.common.plugin.runtime.PluginRuntimeManager;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;

public class DefaultConnectorPluginRuntimeOperations implements ConnectorPluginRuntimeOperations {

    private final ConnectorPluginArtifactCache artifactCache;
    private final PluginRuntimeManager runtimeManager;

    public DefaultConnectorPluginRuntimeOperations(
            ConnectorPluginArtifactCache artifactCache, PluginRuntimeManager runtimeManager) {
        this.artifactCache = artifactCache;
        this.runtimeManager = runtimeManager;
    }

    @Override
    public void preload(PluginArtifactDescriptorDTO artifact) {
        if (runtimeManager.isLoaded(artifact.pluginId(), artifact.version())) {
            return;
        }
        var path = artifactCache.resolve(artifact);
        runtimeManager.preload(new PluginArtifactCoordinates(
                artifact.pluginId(), artifact.version(), path, artifact.artifactSha256(),
                artifact.detachedSignature(), artifact.signingKeyId()));
    }

    @Override
    public boolean release(String pluginId, String pluginVersion) {
        return runtimeManager.release(pluginId, pluginVersion);
    }

    @Override
    public boolean isLoaded(String pluginId, String pluginVersion) {
        return runtimeManager.isLoaded(pluginId, pluginVersion);
    }

    @Override
    public int loadedVersionCount() {
        return runtimeManager.registry().states().size();
    }
}
