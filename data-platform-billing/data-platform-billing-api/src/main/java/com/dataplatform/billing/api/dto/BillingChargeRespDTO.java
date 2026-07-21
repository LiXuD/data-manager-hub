package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** 计量判断、价格计算和事件入账后的权威结果。 */
public class BillingChargeRespDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long billingEventId;
    private Long planId;
    private String planCode;
    private Integer planVersion;
    private String templateCode;
    private Boolean billable;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal baseAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal finalAmount;
    private String currency;
    private String status;
    private String decisionDetail;

    public Long getBillingEventId() { return billingEventId; }
    public void setBillingEventId(Long billingEventId) { this.billingEventId = billingEventId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer planVersion) { this.planVersion = planVersion; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }
    public BigDecimal getAdjustmentAmount() { return adjustmentAmount; }
    public void setAdjustmentAmount(BigDecimal adjustmentAmount) { this.adjustmentAmount = adjustmentAmount; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecisionDetail() { return decisionDetail; }
    public void setDecisionDetail(String decisionDetail) { this.decisionDetail = decisionDetail; }
}
