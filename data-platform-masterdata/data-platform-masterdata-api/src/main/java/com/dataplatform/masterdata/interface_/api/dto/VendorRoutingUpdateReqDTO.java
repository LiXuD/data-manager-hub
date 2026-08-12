package com.dataplatform.masterdata.interface_.api.dto;

import java.io.Serializable;

/** Explicit primary/fallback vendor configuration references for an interface. */
public class VendorRoutingUpdateReqDTO implements Serializable {
    private Long primaryVendorConfigId;
    private Long fallbackVendorConfigId;

    public Long getPrimaryVendorConfigId() {
        return primaryVendorConfigId;
    }

    public void setPrimaryVendorConfigId(Long primaryVendorConfigId) {
        this.primaryVendorConfigId = primaryVendorConfigId;
    }

    public Long getFallbackVendorConfigId() {
        return fallbackVendorConfigId;
    }

    public void setFallbackVendorConfigId(Long fallbackVendorConfigId) {
        this.fallbackVendorConfigId = fallbackVendorConfigId;
    }
}
