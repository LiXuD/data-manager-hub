package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record VendorConnectorRuntimeSnapshotDTO(
        Long vendorConfigId,
        Long connectorVersionId,
        Integer versionNo,
        String snapshotHash,
        String hashAlgorithm,
        String integrityHash,
        Integer securityVersion,
        String status,
        List<ConnectorPipelineStepDTO> pipelineSnapshot,
        LocalDateTime publishedAt) implements Serializable {

    public VendorConnectorRuntimeSnapshotDTO(Long vendorConfigId, Long connectorVersionId,
                                             Integer versionNo, String snapshotHash,
                                             Integer securityVersion, String status,
                                             List<ConnectorPipelineStepDTO> pipelineSnapshot,
                                             LocalDateTime publishedAt) {
        this(vendorConfigId, connectorVersionId, versionNo, snapshotHash, null, null,
                securityVersion, status, pipelineSnapshot, publishedAt);
    }
}
