package com.dataplatform.masterdata.connector.service;

import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import java.util.Objects;

/** Deterministic, write-free conversion output. A spec exists only for a lossless decision. */
public record LegacyHttpConversionResult(
        LegacyHttpConversionPreflightResult preflight,
        ConnectorSpecDTO connectorSpec) {

    public LegacyHttpConversionResult {
        Objects.requireNonNull(preflight, "preflight");
        if (preflight.convertible() != (connectorSpec != null)) {
            throw new IllegalArgumentException("Conversion result invariant violated");
        }
    }

    public boolean convertible() { return preflight.convertible(); }
}
