package com.dataplatform.access.caller.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 访问域调用方的 Api Key Interface。
 * <p>数据库实体对象，映射业务表字段并承载持久化层数据结构。</p>
 */
@TableName("api_key_interface")
public class ApiKeyInterface {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long apiKeyId;
    private Long interfaceId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private String grantSource;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long applicationItemId;
    private String status;
    private Boolean cacheEnabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer approvedCacheDays;
    private LocalDateTime effectiveAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime expireAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime revokedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long revokedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String revokeReason;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }

    public Long getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getGrantSource() { return grantSource; }
    public void setGrantSource(String grantSource) { this.grantSource = grantSource; }

    public Long getApplicationItemId() { return applicationItemId; }
    public void setApplicationItemId(Long applicationItemId) { this.applicationItemId = applicationItemId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(Boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public Integer getApprovedCacheDays() { return approvedCacheDays; }
    public void setApprovedCacheDays(Integer approvedCacheDays) { this.approvedCacheDays = approvedCacheDays; }

    public LocalDateTime getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(LocalDateTime effectiveAt) { this.effectiveAt = effectiveAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }

    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }

    public Long getRevokedBy() { return revokedBy; }
    public void setRevokedBy(Long revokedBy) { this.revokedBy = revokedBy; }

    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
