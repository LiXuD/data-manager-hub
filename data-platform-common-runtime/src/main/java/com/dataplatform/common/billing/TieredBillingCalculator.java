package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 阶梯计费计算器
 * 根据调用量区间应用不同折扣
 */
public class TieredBillingCalculator implements BillingCalculator {

    @Override
    public BigDecimal calculate(BillingRuleDO rule, long callCount, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            throw new IllegalArgumentException("Billing rule and unit price are required");
        }
        if (callCount < 0) {
            throw new IllegalArgumentException("Call count must not be negative");
        }

        BigDecimal baseAmount = rule.getUnitPrice()
            .multiply(BigDecimal.valueOf(callCount));

        // 根据调用量确定折扣
        BigDecimal discount = determineDiscount(callCount, rule);

        return baseAmount.multiply(discount)
            .setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            throw new IllegalArgumentException("Billing rule and unit price are required");
        }

        // 单次调用使用折扣后的价格
        BigDecimal discount = rule.getDiscount() != null ? rule.getDiscount() : BigDecimal.ONE;
        return rule.getUnitPrice().multiply(discount)
            .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 确定折扣率
     * 阶梯区间由上游选择规则，本计算器只应用规则中配置的折扣。
     */
    private BigDecimal determineDiscount(long callCount, BillingRuleDO rule) {
        if (rule.getDiscount() == null) {
            return BigDecimal.ONE;
        }
        if (rule.getDiscount().compareTo(BigDecimal.ZERO) <= 0
                || rule.getDiscount().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Discount must be greater than 0 and at most 1");
        }
        return rule.getDiscount();
    }
}
