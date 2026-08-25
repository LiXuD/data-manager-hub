package com.dataplatform.masterdata.connector.service;

import java.util.Objects;

/** A safe preflight finding. It never contains configuration values or secret material. */
public record LegacyHttpConversionReason(
        LegacyHttpConversionReasonCode code,
        Integer stepIndex,
        String stageKey,
        String detail) {

    public LegacyHttpConversionReason {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
    }
}
