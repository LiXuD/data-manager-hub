package com.dataplatform.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 版本化计费方案的累进阶梯。上下限采用十进制定量，兼容次数和时长。 */
@TableName("billing_plan_tier")
public class BillingPlanTier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private BigDecimal tierMin;
    private BigDecimal tierMax;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public BigDecimal getTierMin() { return tierMin; }
    public void setTierMin(BigDecimal tierMin) { this.tierMin = tierMin; }
    public BigDecimal getTierMax() { return tierMax; }
    public void setTierMax(BigDecimal tierMax) { this.tierMax = tierMax; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

