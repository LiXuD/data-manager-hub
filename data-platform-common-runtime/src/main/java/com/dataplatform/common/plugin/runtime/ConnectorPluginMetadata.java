package com.dataplatform.common.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public record ConnectorPluginMetadata(
        String pluginId,
        String version,
        String artifactSha256,
        String manifestHash,
        String schemaHash,
        JsonNode configSchema) {

    public ConnectorPluginMetadata {
        configSchema = configSchema == null ? null : configSchema.deepCopy();
    }

    @Override public JsonNode configSchema() {
        return configSchema == null ? null : configSchema.deepCopy();
    }
}
