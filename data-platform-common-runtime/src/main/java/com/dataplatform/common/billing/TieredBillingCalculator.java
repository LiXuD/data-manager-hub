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
            return BigDecimal.ZERO;
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
            return BigDecimal.ZERO;
        }

        // 单次调用使用折扣后的价格
        BigDecimal discount = rule.getDiscount() != null ? rule.getDiscount() : BigDecimal.ONE;
        return rule.getUnitPrice().multiply(discount)
            .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 确定折扣率
     * 规则：
     * - 0-10万次: 1.0 (无折扣)
     * - 10-50万次: 0.9 (9折)
     * - 50万次以上: 0.8 (8折)
     */
    private BigDecimal determineDiscount(long callCount, BillingRuleDO rule) {
        // 如果规则中指定了折扣，优先使用规则折扣
        if (rule.getDiscount() != null && rule.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            return rule.getDiscount();
        }

        // 默认阶梯折扣
        if (callCount > 500_000) {
            return new BigDecimal("0.80");
        } else if (callCount > 100_000) {
            return new BigDecimal("0.90");
        } else {
            return BigDecimal.ONE;
        }
    }
}
