package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record VendorConnectorVersionDTO(
        Long id,
        Long vendorConfigId,
        Integer versionNo,
        String snapshotHash,
        Integer securityVersion,
        String status,
        Long previousVersionId,
        LocalDateTime publishedAt,
        Long publishedBy,
        List<ConnectorPipelineStepDTO> pipelineSnapshot) implements Serializable {
}
