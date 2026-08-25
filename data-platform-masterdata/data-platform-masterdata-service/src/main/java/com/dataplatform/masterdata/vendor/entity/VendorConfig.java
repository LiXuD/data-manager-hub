package com.dataplatform.masterdata.vendor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.handler.CommonStatusTypeHandler;
import java.time.LocalDateTime;

/**
 * 主数据域厂商的 Vendor Config。
 * <p>数据库实体对象，映射业务表字段并承载持久化层数据结构。</p>
 */
@TableName(value = "vendor_config", autoResultMap = true)
public class VendorConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vendorId;
    private Long dataTypeId;
    private Long interfaceId;
    private Integer timeout;
    private Integer retryCount;
    private Integer circuitThreshold;
    private Integer circuitTimeout;
    private Long fallbackVendorId;
    private Integer securityVersion;
    private String runtimeMode;
    private Long activeConnectorVersionId;
    private Integer connectorVersion;

    @TableField(typeHandler = CommonStatusTypeHandler.class)
    private CommonStatus status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean deleted;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public Long getDataTypeId() { return dataTypeId; }
    public void setDataTypeId(Long dataTypeId) { this.dataTypeId = dataTypeId; }
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
    public Integer getSecurityVersion() { return securityVersion; }
    public void setSecurityVersion(Integer securityVersion) { this.securityVersion = securityVersion; }
    public String getRuntimeMode() { return runtimeMode; }
    public void setRuntimeMode(String runtimeMode) { this.runtimeMode = runtimeMode; }
    public Long getActiveConnectorVersionId() { return activeConnectorVersionId; }
    public void setActiveConnectorVersionId(Long activeConnectorVersionId) {
        this.activeConnectorVersionId = activeConnectorVersionId;
    }
    public Integer getConnectorVersion() { return connectorVersion; }
    public void setConnectorVersion(Integer connectorVersion) { this.connectorVersion = connectorVersion; }
    public CommonStatus getStatus() { return status; }
    public void setStatus(CommonStatus status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

}
