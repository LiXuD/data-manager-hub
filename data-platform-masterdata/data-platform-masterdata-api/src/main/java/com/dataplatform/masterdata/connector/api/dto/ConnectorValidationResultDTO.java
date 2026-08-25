package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

public record ConnectorValidationResultDTO(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        String snapshotHash,
        String hashAlgorithm,
        String integrityHash) implements Serializable {

    public ConnectorValidationResultDTO(boolean valid, List<String> errors,
                                        List<String> warnings, String snapshotHash) {
        this(valid, errors, warnings, snapshotHash, null, null);
    }
}
