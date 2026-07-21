package com.dataplatform.common.entity.unified;

import java.math.BigDecimal;

/**
 * 计费阶梯值对象。
 */
public class BillingTierDO {

    /** 区间下限（含）。 */
    private Long tierMin;

    /** 区间上限（不含），null 表示无上限。 */
    private Long tierMax;

    /** 折扣率，1 表示原价。 */
    private BigDecimal discount;

    public Long getTierMin() { return tierMin; }
    public void setTierMin(Long tierMin) { this.tierMin = tierMin; }
    public Long getTierMax() { return tierMax; }
    public void setTierMax(Long tierMax) { this.tierMax = tierMax; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
}
