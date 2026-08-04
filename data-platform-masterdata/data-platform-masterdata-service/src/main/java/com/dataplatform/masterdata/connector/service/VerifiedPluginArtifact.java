package com.dataplatform.masterdata.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record VerifiedPluginArtifact(
        String pluginId,
        String version,
        String spiVersion,
        String displayName,
        String provider,
        String description,
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
        JsonNode configSchema) {
}
