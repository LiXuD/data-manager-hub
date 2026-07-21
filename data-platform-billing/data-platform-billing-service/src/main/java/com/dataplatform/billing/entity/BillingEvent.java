package com.dataplatform.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 不可变计费事实账本；账单和对账均应由事件聚合得到。 */
@TableName("billing_event")
public class BillingEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String eventType;
    private Long planId;
    private String planCode;
    private Integer planVersion;
    private String templateCode;
    private String accountingPurpose;
    private Long originalEventId;
    private Long tenantId;
    private Long callerId;
    private Long vendorId;
    private String vendorCode;
    private Long interfaceId;
    private String interfaceCode;
    private String dataType;
    private Boolean billable;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal usageBefore;
    private BigDecimal baseAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal finalAmount;
    private String currency;
    private String status;
    private String evidenceHash;
    private String decisionDetail;
    private String pricingSnapshot;
    private LocalDate billingPeriod;
    private LocalDateTime callTime;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer planVersion) { this.planVersion = planVersion; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getAccountingPurpose() { return accountingPurpose; }
    public void setAccountingPurpose(String accountingPurpose) { this.accountingPurpose = accountingPurpose; }
    public Long getOriginalEventId() { return originalEventId; }
    public void setOriginalEventId(Long originalEventId) { this.originalEventId = originalEventId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public Long getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getUsageBefore() { return usageBefore; }
    public void setUsageBefore(BigDecimal usageBefore) { this.usageBefore = usageBefore; }
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
    public String getEvidenceHash() { return evidenceHash; }
    public void setEvidenceHash(String evidenceHash) { this.evidenceHash = evidenceHash; }
    public String getDecisionDetail() { return decisionDetail; }
    public void setDecisionDetail(String decisionDetail) { this.decisionDetail = decisionDetail; }
    public String getPricingSnapshot() { return pricingSnapshot; }
    public void setPricingSnapshot(String pricingSnapshot) { this.pricingSnapshot = pricingSnapshot; }
    public LocalDate getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(LocalDate billingPeriod) { this.billingPeriod = billingPeriod; }
    public LocalDateTime getCallTime() { return callTime; }
    public void setCallTime(LocalDateTime callTime) { this.callTime = callTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
