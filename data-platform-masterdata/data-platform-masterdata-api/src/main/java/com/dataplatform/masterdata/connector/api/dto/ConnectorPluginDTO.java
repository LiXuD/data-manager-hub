package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record ConnectorPluginDTO(
        Long id,
        String pluginId,
        String displayName,
        String provider,
        String description,
        String status,
        String activeVersion,
        Long bindingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {
}
