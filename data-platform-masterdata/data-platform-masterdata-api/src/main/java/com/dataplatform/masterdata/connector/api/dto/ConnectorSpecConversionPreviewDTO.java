package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

/** Safe, read-only projection of Legacy-to-SIMPLE conversion feasibility. */
public record ConnectorSpecConversionPreviewDTO(
        boolean convertible,
        String classification,
        String errorCode,
        List<Reason> reasons,
        ConnectorSpecDTO connectorSpec) implements Serializable {

    public ConnectorSpecConversionPreviewDTO {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public record Reason(String code, Integer stepIndex, String stageKey, String safeMessage)
            implements Serializable { }
}
