package com.dataplatform.common.plugin.artifact;

import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
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
        PluginPermissions permissions) {

    public PluginManifest {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        configSchema = configSchema == null ? null : configSchema.deepCopy();
    }

    @Override
    public JsonNode configSchema() {
        return configSchema == null ? null : configSchema.deepCopy();
    }

    public PluginDescriptor descriptor() {
        return new PluginDescriptor(pluginId, version, spiVersion, displayName, provider, capabilities);
    }
}
