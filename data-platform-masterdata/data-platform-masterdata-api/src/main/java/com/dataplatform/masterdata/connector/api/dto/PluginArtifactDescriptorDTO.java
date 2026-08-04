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
        String status) implements Serializable {
}
