package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

public record VendorConnectorDraftDTO(
        Long id,
        Long vendorConfigId,
        Integer draftVersion,
        Integer securityVersion,
        List<ConnectorPipelineStepDTO> pipelineSnapshot) implements Serializable {
}
