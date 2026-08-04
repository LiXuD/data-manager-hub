package com.dataplatform.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record CompiledStageConfig(
        String stageKey,
        String pluginId,
        String pluginVersion,
        StageCapability capability,
        JsonNode config,
        String configHash) {

    public CompiledStageConfig {
        stageKey = requireText(stageKey, "stageKey");
        pluginId = requireText(pluginId, "pluginId");
        pluginVersion = requireText(pluginVersion, "pluginVersion");
        capability = Objects.requireNonNull(capability, "capability");
        config = Objects.requireNonNull(config, "config").deepCopy();
        configHash = requireText(configHash, "configHash");
    }

    @Override
    public JsonNode config() {
        return config.deepCopy();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
