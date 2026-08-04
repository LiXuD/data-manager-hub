package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record VendorConnectorTestResultDTO(
        Boolean success,
        String errorCategory,
        String errorCode,
        String safeMessage,
        Map<String, Object> normalizedData,
        List<ConnectorStageTimingDTO> stageTimings) implements Serializable {
}
