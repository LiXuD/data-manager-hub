package com.dataplatform.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;


@TableName("call_record")
public class CallRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 租户ID
     */
    private Long tenantId;
    
    /**
     * API Key ID
     */
    private Long apiKeyId;
    
    /**
     * 呼叫者ID
     */
    private Long callerId;
    
    /**
     * 供应商ID
     */
    private Long vendorId;
    
    /**
     * 数据类型编码
     */
    private String dataTypeCode;
    
    /**
     * 调用时间
     */
    private LocalDateTime callTime;
    
    /**
     * 调用结果：success-成功，failed-失败
     */
    private String result;
    
    /**
     * 错误信息
     */
    private String errorMsg;
    
    /**
     * 响应时间（毫秒）
     */
    private Integer responseTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableLogic
    private Boolean deleted;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public LocalDateTime getCallTime() { return callTime; }
    public void setCallTime(LocalDateTime callTime) { this.callTime = callTime; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Integer getResponseTime() { return responseTime; }
    public void setResponseTime(Integer responseTime) { this.responseTime = responseTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

}