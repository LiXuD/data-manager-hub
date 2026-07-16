package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 标准计费计算器
 * 费用 = 单价 × 调用次数
 */
public class StandardBillingCalculator implements BillingCalculator {

    @Override
    public BigDecimal calculate(BillingRuleDO rule, long callCount, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            throw new IllegalArgumentException("Billing rule and unit price are required");
        }
        if (callCount < 0) {
            throw new IllegalArgumentException("Call count must not be negative");
        }

        return rule.getUnitPrice()
            .multiply(BigDecimal.valueOf(callCount))
            .setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            throw new IllegalArgumentException("Billing rule and unit price are required");
        }
        return rule.getUnitPrice().setScale(4, RoundingMode.HALF_UP);
    }
}
