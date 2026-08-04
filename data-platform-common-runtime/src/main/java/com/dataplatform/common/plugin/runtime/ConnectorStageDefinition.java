package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record ConnectorStageDefinition(
        String stageKey,
        StageCapability capability,
        String pluginId,
        String pluginVersion,
        int order,
        boolean enabled,
        JsonNode config,
        String configHash) {

    public ConnectorStageDefinition {
        if (stageKey == null || stageKey.isBlank() || pluginId == null || pluginId.isBlank()
                || pluginVersion == null || pluginVersion.isBlank() || configHash == null || configHash.isBlank()) {
            throw new IllegalArgumentException("Stage identity and configHash are required");
        }
        capability = Objects.requireNonNull(capability, "capability");
        config = Objects.requireNonNull(config, "config").deepCopy();
    }

    @Override
    public JsonNode config() {
        return config.deepCopy();
    }
}
