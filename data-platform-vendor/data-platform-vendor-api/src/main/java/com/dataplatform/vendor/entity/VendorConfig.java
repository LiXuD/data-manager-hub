package com.dataplatform.vendor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.common.handler.JsonbTypeHandler;
import com.dataplatform.common.enums.CommonStatus;
import java.time.LocalDateTime;

@TableName(value = "vendor_config", autoResultMap = true)
public class VendorConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vendorId;
    private Long dataTypeId;
    private Long interfaceId;
    private String apiUrl;
    private String method;
    private Integer timeout;
    private Integer retryCount;
    private Integer circuitThreshold;
    private Integer circuitTimeout;
    private String signType;
    private String encryptType;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String headerConfig;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String requestTemplate;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String responseMapping;
    private Long fallbackVendorId;
    private String authType;
    private String authConfig;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String paramMapping;

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
    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }
    public String getEncryptType() { return encryptType; }
    public void setEncryptType(String encryptType) { this.encryptType = encryptType; }
    public String getHeaderConfig() { return headerConfig; }
    public void setHeaderConfig(String headerConfig) { this.headerConfig = headerConfig; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }
    public String getResponseMapping() { return responseMapping; }
    public void setResponseMapping(String responseMapping) { this.responseMapping = responseMapping; }
    public Long getFallbackVendorId() { return fallbackVendorId; }
    public void setFallbackVendorId(Long fallbackVendorId) { this.fallbackVendorId = fallbackVendorId; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthConfig() { return authConfig; }
    public void setAuthConfig(String authConfig) { this.authConfig = authConfig; }
    public String getParamMapping() { return paramMapping; }
    public void setParamMapping(String paramMapping) { this.paramMapping = paramMapping; }
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
