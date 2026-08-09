package com.dataplatform.masterdata.vendor.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Plugin-only vendor routing and platform execution policy. */
public class VendorConfigDTO implements Serializable {
    private Long id;
    private Long vendorId;
    private String vendorName;
    private Long dataTypeId;
    private String dataTypeCode;
    private Long interfaceId;
    private Integer timeout;
    private Integer retryCount;
    private Integer circuitThreshold;
    private Integer circuitTimeout;
    private Long fallbackVendorId;
    private String status;
    private String runtimeMode;
    private Long activeConnectorVersionId;
    private Integer connectorVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public Long getDataTypeId() { return dataTypeId; }
    public void setDataTypeId(Long dataTypeId) { this.dataTypeId = dataTypeId; }
    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public Long getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getCircuitThreshold() { return circuitThreshold; }
    public void setCircuitThreshold(Integer circuitThreshold) { this.circuitThreshold = circuitThreshold; }
    public Integer getCircuitTimeout() { return circuitTimeout; }
    public void setCircuitTimeout(Integer circuitTimeout) { this.circuitTimeout = circuitTimeout; }
    public Long getFallbackVendorId() { return fallbackVendorId; }
    public void setFallbackVendorId(Long fallbackVendorId) { this.fallbackVendorId = fallbackVendorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRuntimeMode() { return runtimeMode; }
    public void setRuntimeMode(String runtimeMode) { this.runtimeMode = runtimeMode; }
    public Long getActiveConnectorVersionId() { return activeConnectorVersionId; }
    public void setActiveConnectorVersionId(Long activeConnectorVersionId) {
        this.activeConnectorVersionId = activeConnectorVersionId;
    }
    public Integer getConnectorVersion() { return connectorVersion; }
    public void setConnectorVersion(Integer connectorVersion) { this.connectorVersion = connectorVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
