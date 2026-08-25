package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record ConnectorMigrationObservationReqDTO(
        Long vendorId,
        Integer pipelineVersion,
        String snapshotHash,
        LocalDateTime startedAt,
        LocalDateTime endedAt) implements Serializable {
}
