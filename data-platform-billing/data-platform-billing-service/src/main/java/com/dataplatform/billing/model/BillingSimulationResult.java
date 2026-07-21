package com.dataplatform.billing.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 发布前模拟结果，逐项展示计量判断和金额形成过程。 */

public class BillingSimulationResult {
    private Boolean valid;
    private Boolean billable;
    private BigDecimal quantity;
    private BigDecimal usageBefore;
    private BigDecimal baseAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal finalAmount;
    private String matchedTier;
    private List<String> decisions = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public Boolean getValid() { return valid; }
    public void setValid(Boolean valid) { this.valid = valid; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getUsageBefore() { return usageBefore; }
    public void setUsageBefore(BigDecimal usageBefore) { this.usageBefore = usageBefore; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }
    public BigDecimal getAdjustmentAmount() { return adjustmentAmount; }
    public void setAdjustmentAmount(BigDecimal adjustmentAmount) { this.adjustmentAmount = adjustmentAmount; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }
    public String getMatchedTier() { return matchedTier; }
    public void setMatchedTier(String matchedTier) { this.matchedTier = matchedTier; }
    public List<String> getDecisions() { return decisions; }
    public void setDecisions(List<String> decisions) { this.decisions = decisions; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
