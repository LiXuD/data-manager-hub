package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class VendorSecurityVersionDTO implements Serializable {
    private Long id;
    private Integer version;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
