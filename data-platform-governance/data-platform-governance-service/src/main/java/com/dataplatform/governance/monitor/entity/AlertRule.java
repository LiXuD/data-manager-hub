package com.dataplatform.governance.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dataplatform.common.enums.AlertStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 观测治理域监控告警的 Alert Rule。
 * <p>数据库实体对象，映射业务表字段并承载持久化层数据结构。</p>
 */
@TableName("alert_rule")
public class AlertRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleName;
    private String ruleType;
    @TableField("metric_name")
    private String targetType;
    private Long targetId;
    @TableField("condition")
    private String conditionType;
    @TableField("threshold")
    private BigDecimal thresholdValue;
    @TableField("time_window")
    private Integer timeWindowMinutes;
    @TableField("notification_channels")
    private String notifyChannels;
    private AlertStatus status;
    @TableField("severity")
    private String severity;
    @TableField("tenant_id")
    private Long tenantId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;

    // Alias fields for test compatibility
    @TableField(exist = false)
    private String metric;
    @TableField(exist = false)
    private Object threshold;
    @TableField(exist = false)
    private String condition;
    @TableField(exist = false)
    private String level;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public Integer getTimeWindowMinutes() { return timeWindowMinutes; }
    public void setTimeWindowMinutes(Integer timeWindowMinutes) { this.timeWindowMinutes = timeWindowMinutes; }
    public String getNotifyChannels() { return notifyChannels; }
    public void setNotifyChannels(String notifyChannels) { this.notifyChannels = notifyChannels; }
    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    // Alias getters/setters for test compatibility
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; if (this.targetType == null) this.targetType = metric; }
    public Object getThreshold() { return threshold; }
    public void setThreshold(Object threshold) {
        this.threshold = threshold;
        if (threshold instanceof Number) {
            this.thresholdValue = new BigDecimal(threshold.toString());
        }
    }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; if (this.conditionType == null) this.conditionType = condition; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
}