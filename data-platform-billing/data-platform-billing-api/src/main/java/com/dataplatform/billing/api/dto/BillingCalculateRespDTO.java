package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class BillingCalculateRespDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal cost;
    private String billingType;
    private String ruleName;

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public String getBillingType() { return billingType; }
    public void setBillingType(String billingType) { this.billingType = billingType; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
}
