package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

public record ConnectorValidationResultDTO(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        String snapshotHash) implements Serializable {
}
