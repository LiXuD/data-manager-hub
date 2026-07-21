package com.dataplatform.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 阶梯和套餐共用的周期用量账户。 */
@TableName("billing_usage_balance")
public class BillingUsageBalance {
    private Long planId;
    private LocalDate billingPeriod;
    private String scopeKey;
    private BigDecimal usedQuantity;
    private LocalDateTime updatedAt;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public LocalDate getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(LocalDate billingPeriod) { this.billingPeriod = billingPeriod; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public BigDecimal getUsedQuantity() { return usedQuantity; }
    public void setUsedQuantity(BigDecimal usedQuantity) { this.usedQuantity = usedQuantity; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

