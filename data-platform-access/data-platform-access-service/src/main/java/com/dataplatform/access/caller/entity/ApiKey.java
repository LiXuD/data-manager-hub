package com.dataplatform.access.caller.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.access.caller.handler.ApiKeyStatusTypeHandler;
import com.dataplatform.common.enums.ApiKeyStatus;
import java.time.LocalDateTime;


/**
 * 访问域调用方的 Api Key。
 * <p>数据库实体对象，映射业务表字段并承载持久化层数据结构。</p>
 */
@TableName(value = "api_key", autoResultMap = true)
public class ApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long callerId;
    private String keyName;
    private String apiKey;
    private String apiSecret;
    private Integer rateLimit;
    private Long quotaLimit;
    private Long quotaUsed;
    @TableField(typeHandler = ApiKeyStatusTypeHandler.class)
    private ApiKeyStatus status;
    private LocalDateTime expireTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Boolean deleted;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
    public Integer getRateLimit() { return rateLimit; }
    public void setRateLimit(Integer rateLimit) { this.rateLimit = rateLimit; }
    public Long getQuotaLimit() { return quotaLimit; }
    public void setQuotaLimit(Long quotaLimit) { this.quotaLimit = quotaLimit; }
    public Long getQuotaUsed() { return quotaUsed; }
    public void setQuotaUsed(Long quotaUsed) { this.quotaUsed = quotaUsed; }
    public ApiKeyStatus getStatus() { return status; }
    public void setStatus(ApiKeyStatus status) { this.status = status; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

}
