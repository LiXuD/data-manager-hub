package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record ConnectorBillingObservationReqDTO(
        Long vendorId,
        Long interfaceId,
        Integer pipelineVersion,
        String snapshotHash,
        LocalDateTime startedAt,
        LocalDateTime endedAt) implements Serializable {
}
