package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;

public record ConnectorStageTimingDTO(
        String stageKey, String capability, String pluginId, String pluginVersion, Long durationMs)
        implements Serializable {
}
