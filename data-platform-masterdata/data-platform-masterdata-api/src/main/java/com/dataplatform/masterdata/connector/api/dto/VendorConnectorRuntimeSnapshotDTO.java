package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record VendorConnectorRuntimeSnapshotDTO(
        Long vendorConfigId,
        Long connectorVersionId,
        Integer versionNo,
        String snapshotHash,
        Integer securityVersion,
        String status,
        List<ConnectorPipelineStepDTO> pipelineSnapshot,
        LocalDateTime publishedAt) implements Serializable {
}
