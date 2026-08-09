package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

public class ConnectorSecretResolutionRequestDTO implements Serializable {
    private Long vendorConfigId;
    private Set<String> secretRefs = new LinkedHashSet<>();

    public Long getVendorConfigId() { return vendorConfigId; }
    public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
    public Set<String> getSecretRefs() { return secretRefs; }
    public void setSecretRefs(Set<String> secretRefs) {
        this.secretRefs = secretRefs == null ? new LinkedHashSet<>() : new LinkedHashSet<>(secretRefs);
    }
}
