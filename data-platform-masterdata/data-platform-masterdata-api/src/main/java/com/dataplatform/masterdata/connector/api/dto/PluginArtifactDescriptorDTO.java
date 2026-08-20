package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

public record PluginArtifactDescriptorDTO(
        String pluginId,
        String version,
        String spiVersion,
        String entryClass,
        String artifactUri,
        String artifactSha256,
        String detachedSignature,
        String signingKeyId,
        String manifestJson,
        String configSchemaJson,
        List<String> capabilities,
        String permissionManifestJson,
        String minHostVersion,
        String status,
        String manifestVersion,
        String authoringModel,
        String connectorKind,
        String transportMode,
        String outputMode,
        String compatibilityJson) implements Serializable {

    /** Compatibility constructor retained for all original v1 API callers. */
    public PluginArtifactDescriptorDTO(
            String pluginId,
            String version,
            String spiVersion,
            String entryClass,
            String artifactUri,
            String artifactSha256,
            String detachedSignature,
            String signingKeyId,
            String manifestJson,
            String configSchemaJson,
            List<String> capabilities,
            String permissionManifestJson,
            String minHostVersion,
            String status) {
        this(pluginId, version, spiVersion, entryClass, artifactUri, artifactSha256,
                detachedSignature, signingKeyId, manifestJson, configSchemaJson, capabilities,
                permissionManifestJson, minHostVersion, status, "1", "ADVANCED_PIPELINE",
                null, null, null, "{}");
    }
}
