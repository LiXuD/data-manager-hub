package com.dataplatform.access.connector.runtime;

import com.dataplatform.access.connector.artifact.ConnectorPluginArtifactCache;
import com.dataplatform.access.connector.service.ConnectorPluginRuntimeOperations;
import com.dataplatform.common.plugin.artifact.PluginArtifactCoordinates;
import com.dataplatform.common.plugin.runtime.PluginRuntimeManager;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;

public class DefaultConnectorPluginRuntimeOperations implements ConnectorPluginRuntimeOperations {

    private final ConnectorPluginArtifactCache artifactCache;
    private final PluginRuntimeManager runtimeManager;
    private final MasterdataConnectorPluginMetadataResolver metadataResolver;

    public DefaultConnectorPluginRuntimeOperations(
            ConnectorPluginArtifactCache artifactCache, PluginRuntimeManager runtimeManager) {
        this(artifactCache, runtimeManager, null);
    }

    public DefaultConnectorPluginRuntimeOperations(
            ConnectorPluginArtifactCache artifactCache,
            PluginRuntimeManager runtimeManager,
            MasterdataConnectorPluginMetadataResolver metadataResolver) {
        this.artifactCache = artifactCache;
        this.runtimeManager = runtimeManager;
        this.metadataResolver = metadataResolver;
    }

    @Override
    public void preload(PluginArtifactDescriptorDTO artifact) {
        boolean generic = GenericHttpConnectorMetadata.PLUGIN_ID.equals(artifact.pluginId());
        if (generic) {
            if (metadataResolver == null) {
                throw new IllegalStateException("Generic HTTP metadata validator is unavailable");
            }
            metadataResolver.validateGenericBuiltin(artifact);
        }
        if (runtimeManager.isLoaded(artifact.pluginId(), artifact.version())) {
            return;
        }
        if (generic) {
            throw new IllegalStateException("Generic HTTP built-in runtime is unavailable");
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

    @Override
    public int isolatedClassLoaderCount() {
        return runtimeManager.isolatedClassLoaderCount();
    }
}
