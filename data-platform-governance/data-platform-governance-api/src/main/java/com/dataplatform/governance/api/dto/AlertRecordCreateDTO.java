package com.dataplatform.governance.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 观测治理域的 Alert Record Create DTO。
 * <p>跨服务契约数据对象，用于 api 模块暴露远程接口时传递稳定字段。</p>
 */
public class AlertRecordCreateDTO implements Serializable {

    private Long ruleId;
    private Long tenantId;
    private String alertType;
    private String alertTitle;
    private String level;
    private String alertMessage;
    private BigDecimal triggeredValue;
    private String status;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getAlertTitle() { return alertTitle; }
    public void setAlertTitle(String alertTitle) { this.alertTitle = alertTitle; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }
    public BigDecimal getTriggeredValue() { return triggeredValue; }
    public void setTriggeredValue(BigDecimal triggeredValue) { this.triggeredValue = triggeredValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
