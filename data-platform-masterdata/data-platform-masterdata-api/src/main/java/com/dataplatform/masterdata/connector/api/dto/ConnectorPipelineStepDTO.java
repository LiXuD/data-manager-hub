package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.Map;

public record ConnectorPipelineStepDTO(
        String stageKey,
        String capability,
        String pluginId,
        String pluginVersion,
        Integer order,
        Boolean enabled,
        Map<String, Object> config,
        String configHash) implements Serializable {
}
