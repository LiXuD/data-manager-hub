package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;

/** Redacted draft view. The compiled pipeline is intentionally absent. */
public record ConnectorSpecDraftViewDTO(
        boolean present,
        Long id,
        Long vendorConfigId,
        Integer draftVersion,
        String authoringMode,
        Integer securityVersion,
        ConnectorSpecDTO connectorSpec,
        String specHash,
        String compilerVersion,
        String compileHash,
        String compiledSnapshotHash) implements Serializable {

    public static ConnectorSpecDraftViewDTO empty(Long vendorConfigId) {
        return new ConnectorSpecDraftViewDTO(false, null, vendorConfigId, null, null,
                null, null, null, null, null, null);
    }
}
