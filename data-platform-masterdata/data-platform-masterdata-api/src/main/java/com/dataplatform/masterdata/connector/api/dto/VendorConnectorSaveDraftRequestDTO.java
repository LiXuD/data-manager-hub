package com.dataplatform.masterdata.connector.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

public record VendorConnectorSaveDraftRequestDTO(
        @NotNull Integer expectedDraftVersion,
        @NotNull @Size(max = 50) List<@Valid ConnectorPipelineStepDTO> pipelineSnapshot) implements Serializable {
}
