package com.dataplatform.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;

public interface ConnectorStageFactory {

    StageCapability capability();

    /** Existing plugins remain stateless shared stages unless they explicitly opt in. */
    default StageLifecycle lifecycle() {
        return StageLifecycle.SHARED;
    }

    void validate(JsonNode config, PluginValidationContext context) throws ConnectorException;

    ConnectorStage create(CompiledStageConfig config) throws ConnectorException;
}
