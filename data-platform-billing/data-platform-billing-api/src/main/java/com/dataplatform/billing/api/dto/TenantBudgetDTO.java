package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class TenantBudgetDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private BigDecimal monthlyBudget;
    private BigDecimal usedAmount;
    private BigDecimal remainingAmount;
    private String alertThreshold;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public BigDecimal getMonthlyBudget() { return monthlyBudget; }
    public void setMonthlyBudget(BigDecimal monthlyBudget) { this.monthlyBudget = monthlyBudget; }
    public BigDecimal getUsedAmount() { return usedAmount; }
    public void setUsedAmount(BigDecimal usedAmount) { this.usedAmount = usedAmount; }
    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }
    public String getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(String alertThreshold) { this.alertThreshold = alertThreshold; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
