package com.dataplatform.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;

public interface ConnectorStageFactory {

    StageCapability capability();

    void validate(JsonNode config, PluginValidationContext context) throws ConnectorException;

    ConnectorStage create(CompiledStageConfig config) throws ConnectorException;
}
