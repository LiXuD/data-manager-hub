package com.dataplatform.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/**
 * 计费规则阶梯区间。
 */
@TableName("billing_rule_tier")
public class BillingRuleTier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private Long tierMin;
    private Long tierMax;
    private BigDecimal discount;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getTierMin() { return tierMin; }
    public void setTierMin(Long tierMin) { this.tierMin = tierMin; }
    public Long getTierMax() { return tierMax; }
    public void setTierMax(Long tierMax) { this.tierMax = tierMax; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
