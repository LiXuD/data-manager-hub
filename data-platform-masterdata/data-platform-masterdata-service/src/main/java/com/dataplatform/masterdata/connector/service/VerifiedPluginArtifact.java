package com.dataplatform.masterdata.connector.service;

import com.dataplatform.common.plugin.artifact.PluginCompatibility;
import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
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
        JsonNode configSchema,
        String manifestVersion,
        ConnectorAuthoringModel authoringModel,
        ConnectorKind connectorKind,
        ConnectorTransportMode transportMode,
        ConnectorOutputMode outputMode,
        PluginCompatibility compatibility,
        String compatibilityJson) {

    public VerifiedPluginArtifact {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        configSchema = configSchema == null ? null : configSchema.deepCopy();
        compatibility = compatibility == null ? PluginCompatibility.empty() : compatibility;
        compatibilityJson = compatibilityJson == null ? "{}" : compatibilityJson;
    }

    /** Compatibility constructor retained for existing v1 catalog and test callers. */
    public VerifiedPluginArtifact(
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
        this(pluginId, version, spiVersion, displayName, provider, description, entryClass,
                artifactUri, artifactSha256, detachedSignature, signingKeyId, manifestJson,
                configSchemaJson, capabilities, permissionManifestJson, minHostVersion, configSchema,
                "1", ConnectorAuthoringModel.ADVANCED_PIPELINE, null, null, null,
                PluginCompatibility.empty(), "{}");
    }

    @Override
    public JsonNode configSchema() {
        return configSchema == null ? null : configSchema.deepCopy();
    }
}
