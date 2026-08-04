package com.dataplatform.plugin.spi;

import java.util.Objects;
import java.util.Set;

public record PluginDescriptor(
        String pluginId,
        String version,
        String spiVersion,
        String displayName,
        String provider,
        Set<StageCapability> capabilities) {

    public PluginDescriptor {
        pluginId = requireText(pluginId, "pluginId");
        version = requireText(version, "version");
        spiVersion = requireText(spiVersion, "spiVersion");
        displayName = requireText(displayName, "displayName");
        provider = requireText(provider, "provider");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities cannot be empty");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
