package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;

/** Safe connector secret reference metadata. It intentionally contains no secret value. */
public record ConnectorSecretReferenceOptionDTO(
        String secretRef,
        String scope,
        boolean available) implements Serializable {
}
