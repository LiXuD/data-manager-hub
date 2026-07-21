package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 计费规则中的单个阶梯区间。
 */
public class BillingTierDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tierMin;
    private Long tierMax;
    private BigDecimal discount;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTierMin() { return tierMin; }
    public void setTierMin(Long tierMin) { this.tierMin = tierMin; }
    public Long getTierMax() { return tierMax; }
    public void setTierMax(Long tierMax) { this.tierMax = tierMax; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
