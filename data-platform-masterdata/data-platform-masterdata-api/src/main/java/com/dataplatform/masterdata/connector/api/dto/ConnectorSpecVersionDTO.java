package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Published SIMPLE connector facts. Compiled stage configuration is intentionally absent. */
public record ConnectorSpecVersionDTO(
        Long id,
        Long vendorConfigId,
        Integer version,
        String authoringMode,
        String specHash,
        String compilerVersion,
        String compileHash,
        String snapshotHash,
        String hashAlgorithm,
        String integrityHash,
        Integer securityVersion,
        String status,
        Long previousVersionId,
        LocalDateTime publishedAt,
        Long publishedBy) implements Serializable {
}
