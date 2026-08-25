package com.dataplatform.common.plugin.artifact;

import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.Set;

public record PluginManifest(
        String manifestVersion,
        String pluginId,
        String version,
        String spiVersion,
        String displayName,
        String provider,
        String entryClass,
        Set<StageCapability> capabilities,
        String minHostVersion,
        JsonNode configSchema,
        PluginPermissions permissions,
        ConnectorAuthoringModel authoringModel,
        ConnectorKind connectorKind,
        ConnectorTransportMode transportMode,
        ConnectorOutputMode outputMode,
        PluginCompatibility compatibility) {

    public PluginManifest {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        configSchema = configSchema == null ? null : configSchema.deepCopy();
        authoringModel = Objects.requireNonNull(authoringModel, "authoringModel");
        compatibility = compatibility == null ? PluginCompatibility.empty() : compatibility;
        if ("1".equals(manifestVersion)) {
            if (authoringModel != ConnectorAuthoringModel.ADVANCED_PIPELINE
                    || connectorKind != null || transportMode != null || outputMode != null
                    || !compatibility.isEmpty()) {
                throw new IllegalArgumentException("Manifest v1 cannot carry v2 product fields");
            }
        } else if ("2".equals(manifestVersion)) {
            Objects.requireNonNull(connectorKind, "connectorKind");
            Objects.requireNonNull(transportMode, "transportMode");
            Objects.requireNonNull(outputMode, "outputMode");
            if (compatibility.isEmpty()) {
                throw new IllegalArgumentException("Manifest v2 compatibility cannot be empty");
            }
        }
    }

    /** Source- and binary-compatible constructor for the original Manifest v1 projection. */
    public PluginManifest(
            String manifestVersion,
            String pluginId,
            String version,
            String spiVersion,
            String displayName,
            String provider,
            String entryClass,
            Set<StageCapability> capabilities,
            String minHostVersion,
            JsonNode configSchema,
            PluginPermissions permissions) {
        this(manifestVersion, pluginId, version, spiVersion, displayName, provider, entryClass,
                capabilities, minHostVersion, configSchema, permissions,
                ConnectorAuthoringModel.ADVANCED_PIPELINE, null, null, null,
                PluginCompatibility.empty());
    }

    @Override
    public JsonNode configSchema() {
        return configSchema == null ? null : configSchema.deepCopy();
    }

    public PluginDescriptor descriptor() {
        return new PluginDescriptor(pluginId, version, spiVersion, displayName, provider, capabilities);
    }
}
