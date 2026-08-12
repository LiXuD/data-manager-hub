package com.dataplatform.masterdata.interface_.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/** Readiness of an interface's explicit vendor routing. */
public enum RoutingReadiness {
    UNBOUND,
    PRIMARY_NOT_READY,
    FALLBACK_NOT_READY,
    READY;

    @JsonValue
    public String getCode() {
        return name();
    }
}
