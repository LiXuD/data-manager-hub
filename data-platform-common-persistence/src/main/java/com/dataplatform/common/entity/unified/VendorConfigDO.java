package com.dataplatform.common.entity.unified;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 统一厂商配置实体
 */
@TableName("vendor_config")
public class VendorConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vendorId;
    private Long dataTypeId;

    /** 数据类型编码(冗余字段，便于查询) */
    private String dataTypeCode;

    private String apiUrl;
    private String method;
    private Integer timeout;
    private Integer retryCount;
    private Integer circuitThreshold;
    private Integer circuitTimeout;

    /** 请求头配置 JSON */
    private String headerConfig;

    /** 请求参数模板 JSON */
    private String requestTemplate;

    /** 响应字段映射 JSON */
    private String responseMapping;

    /** 备用厂商ID */
    private Long fallbackVendorId;

    private String status;

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
    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getCircuitThreshold() { return circuitThreshold; }
    public void setCircuitThreshold(Integer circuitThreshold) { this.circuitThreshold = circuitThreshold; }
    public Integer getCircuitTimeout() { return circuitTimeout; }
    public void setCircuitTimeout(Integer circuitTimeout) { this.circuitTimeout = circuitTimeout; }
    public String getHeaderConfig() { return headerConfig; }
    public void setHeaderConfig(String headerConfig) { this.headerConfig = headerConfig; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }
    public String getResponseMapping() { return responseMapping; }
    public void setResponseMapping(String responseMapping) { this.responseMapping = responseMapping; }
    public Long getFallbackVendorId() { return fallbackVendorId; }
    public void setFallbackVendorId(Long fallbackVendorId) { this.fallbackVendorId = fallbackVendorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
