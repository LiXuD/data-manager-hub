package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

/** Value-free dry-run result for invalid PREPARED migration rows. */
public record VendorConnectorMigrationRepairCandidateDTO(
        Long migrationId,
        Long vendorConfigId,
        Integer recordVersion,
        String classification,
        List<String> reasonCodes) implements Serializable {
    public VendorConnectorMigrationRepairCandidateDTO {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
