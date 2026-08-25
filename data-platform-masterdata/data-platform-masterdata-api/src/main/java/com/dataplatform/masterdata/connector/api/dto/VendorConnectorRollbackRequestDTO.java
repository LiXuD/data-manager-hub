package com.dataplatform.masterdata.connector.api.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public record VendorConnectorRollbackRequestDTO(@NotNull Integer expectedConnectorVersion) implements Serializable {
}
