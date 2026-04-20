package com.dataplatform.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警记录实体
 */

@TableName("alert_record")
public class AlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 告警规则ID
     */
    private Long ruleId;

    /**
     * 告警规则名称
     */
    private String ruleName;

    /**
     * 规则类型
     */
    private String ruleType;

    /**
     * 告警级别: INFO, WARNING, CRITICAL
     */
    private String level;

    /**
     * 告警消息
     */
    private String message;

    /**
     * 触发值
     */
    private BigDecimal triggerValue;

    /**
     * 阈值
     */
    private BigDecimal threshold;

    /**
     * 关联的租户ID
     */
    private Long tenantId;

    /**
     * 关联的厂商ID
     */
    private Long vendorId;

    /**
     * 关联的API Key ID
     */
    private Long apiKeyId;

    /**
     * 告警状态: TRIGGERED(触发中), RESOLVED(已恢复), ACKNOWLEDGED(已确认)
     */
    private String status;

    /**
     * 触发时间
     */
    private LocalDateTime triggerTime;

    /**
     * 恢复时间
     */
    private LocalDateTime resolvedTime;

    public LocalDateTime getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(LocalDateTime resolvedTime) { this.resolvedTime = resolvedTime; }

    /**
     * 确认时间
     */
    private LocalDateTime acknowledgeTime;

    public LocalDateTime getAcknowledgeTime() { return acknowledgeTime; }
    public void setAcknowledgeTime(LocalDateTime acknowledgeTime) { this.acknowledgeTime = acknowledgeTime; }

    /**
     * 确认人ID
     */
    private Long acknowledgeBy;

    /**
     * 确认备注
     */
    private String acknowledgeNote;

    /**
     * 通知是否成功
     */
    private Boolean notifySuccess;

    /**
     * 通知失败原因
     */
    private String notifyError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public BigDecimal getTriggerValue() { return triggerValue; }
    public void setTriggerValue(BigDecimal triggerValue) { this.triggerValue = triggerValue; }
    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getTriggerTime() { return triggerTime; }
    public void setTriggerTime(LocalDateTime triggerTime) { this.triggerTime = triggerTime; }
    public Long getAcknowledgeBy() { return acknowledgeBy; }
    public void setAcknowledgeBy(Long acknowledgeBy) { this.acknowledgeBy = acknowledgeBy; }
    public String getAcknowledgeNote() { return acknowledgeNote; }
    public void setAcknowledgeNote(String acknowledgeNote) { this.acknowledgeNote = acknowledgeNote; }
    public Boolean getNotifySuccess() { return notifySuccess; }
    public void setNotifySuccess(Boolean notifySuccess) { this.notifySuccess = notifySuccess; }
    public String getNotifyError() { return notifyError; }
    public void setNotifyError(String notifyError) { this.notifyError = notifyError; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

}