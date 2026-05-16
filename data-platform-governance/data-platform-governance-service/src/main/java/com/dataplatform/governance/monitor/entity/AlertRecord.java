package com.dataplatform.governance.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("alert_record")
public class AlertRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private Long tenantId;
    @TableField("alert_type")
    private String alertType;
    @TableField("alert_title")
    private String alertTitle;
    @TableField("fired_at")
    private LocalDateTime alertTime;
    @TableField("severity")
    private String level;
    @TableField("alert_content")
    private String alertMessage;
    @TableField("metric_value")
    private BigDecimal triggeredValue;
    private String status;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public LocalDateTime getAlertTime() { return alertTime; }
    public void setAlertTime(LocalDateTime alertTime) { this.alertTime = alertTime; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }
    public BigDecimal getTriggeredValue() { return triggeredValue; }
    public void setTriggeredValue(BigDecimal triggeredValue) { this.triggeredValue = triggeredValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getAlertTitle() { return alertTitle; }
    public void setAlertTitle(String alertTitle) { this.alertTitle = alertTitle; }

    // Alias methods for service compatibility
    public LocalDateTime getTriggerTime() { return alertTime; }
    public void setTriggerTime(LocalDateTime triggerTime) { this.alertTime = triggerTime; }
    public void setResolvedTime(LocalDateTime resolvedTime) { this.resolvedAt = resolvedTime; }
}