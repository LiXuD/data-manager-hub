package com.dataplatform.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 可复用的计费算法模板定义。 */
@TableName("billing_template")
public class BillingTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private Integer templateVersion;
    private String templateName;
    private String category;
    private String description;
    private String configSchema;
    private Boolean supportsQuantity;
    private Boolean supportsCycle;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer templateVersion) { this.templateVersion = templateVersion; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getConfigSchema() { return configSchema; }
    public void setConfigSchema(String configSchema) { this.configSchema = configSchema; }
    public Boolean getSupportsQuantity() { return supportsQuantity; }
    public void setSupportsQuantity(Boolean supportsQuantity) { this.supportsQuantity = supportsQuantity; }
    public Boolean getSupportsCycle() { return supportsCycle; }
    public void setSupportsCycle(Boolean supportsCycle) { this.supportsCycle = supportsCycle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

