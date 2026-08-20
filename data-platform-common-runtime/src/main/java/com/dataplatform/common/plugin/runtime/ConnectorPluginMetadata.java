package com.dataplatform.common.plugin.runtime;

import com.dataplatform.common.plugin.artifact.PluginCompatibility;
import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.fasterxml.jackson.databind.JsonNode;

public record ConnectorPluginMetadata(
        String pluginId,
        String version,
        String artifactSha256,
        String manifestHash,
        String schemaHash,
        JsonNode configSchema,
        String manifestVersion,
        ConnectorAuthoringModel authoringModel,
        ConnectorKind connectorKind,
        ConnectorTransportMode transportMode,
        ConnectorOutputMode outputMode,
        PluginCompatibility compatibility) {

    public ConnectorPluginMetadata {
        configSchema = configSchema == null ? null : configSchema.deepCopy();
        manifestVersion = manifestVersion == null ? "1" : manifestVersion;
        authoringModel = authoringModel == null
                ? ConnectorAuthoringModel.ADVANCED_PIPELINE : authoringModel;
        compatibility = compatibility == null ? PluginCompatibility.empty() : compatibility;
    }

    /** Compatibility constructor for the original v1 runtime projection. */
    public ConnectorPluginMetadata(
            String pluginId,
            String version,
            String artifactSha256,
            String manifestHash,
            String schemaHash,
            JsonNode configSchema) {
        this(pluginId, version, artifactSha256, manifestHash, schemaHash, configSchema,
                "1", ConnectorAuthoringModel.ADVANCED_PIPELINE,
                null, null, null, PluginCompatibility.empty());
    }

    @Override public JsonNode configSchema() {
        return configSchema == null ? null : configSchema.deepCopy();
    }

    public boolean simpleV2() {
        return "2".equals(manifestVersion)
                && authoringModel == ConnectorAuthoringModel.SIMPLE_CONNECTOR;
    }
}
