package com.dataplatform.masterdata.connector.service;

import java.util.List;
import java.util.Objects;

/** Read-only conversion decision. No converted draft or persistence mutation is carried here. */
public record LegacyHttpConversionPreflightResult(
        LegacyHttpConversionClassification classification,
        List<LegacyHttpConversionReason> reasons) {

    public LegacyHttpConversionPreflightResult {
        Objects.requireNonNull(classification, "classification");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }

    public boolean convertible() {
        return classification == LegacyHttpConversionClassification.LOSSLESS_CONVERTIBLE;
    }
}
