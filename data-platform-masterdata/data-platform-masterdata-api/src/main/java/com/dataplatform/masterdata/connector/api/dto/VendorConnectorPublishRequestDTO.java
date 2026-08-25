package com.dataplatform.masterdata.connector.api.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public record VendorConnectorPublishRequestDTO(@NotNull Integer expectedDraftVersion) implements Serializable {
}
