package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import com.dataplatform.common.entity.unified.BillingTierDO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

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

        List<BillingTierDO> tiers = rule.getTiers();
        if (tiers == null || tiers.isEmpty()) {
            return rule.getUnitPrice()
                    .multiply(BigDecimal.valueOf(callCount))
                    .multiply(validateDiscount(rule.getDiscount()))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        List<BillingTierDO> sortedTiers = tiers.stream()
                .sorted(Comparator.comparing(BillingTierDO::getTierMin))
                .toList();
        BigDecimal amount = BigDecimal.ZERO;
        long coveredCalls = 0L;
        for (BillingTierDO tier : sortedTiers) {
            long tierMin = tier.getTierMin();
            if (callCount <= tierMin) {
                break;
            }
            long tierEnd = tier.getTierMax() == null
                    ? callCount
                    : Math.min(callCount, tier.getTierMax());
            long tierCalls = tierEnd - tierMin;
            if (tierCalls <= 0) {
                continue;
            }
            amount = amount.add(rule.getUnitPrice()
                    .multiply(BigDecimal.valueOf(tierCalls))
                    .multiply(validateDiscount(tier.getDiscount())));
            coveredCalls += tierCalls;
        }
        if (coveredCalls != callCount) {
            throw new IllegalArgumentException("Tier ranges must continuously cover the call count");
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            throw new IllegalArgumentException("Billing rule and unit price are required");
        }

        return calculate(rule, 1, latencyMs);
    }

    /**
     * 确定折扣率
     * 阶梯区间由上游选择规则，本计算器只应用规则中配置的折扣。
     */
    private BigDecimal validateDiscount(BigDecimal discount) {
        BigDecimal effectiveDiscount = discount != null ? discount : BigDecimal.ONE;
        if (effectiveDiscount.compareTo(BigDecimal.ZERO) <= 0
                || effectiveDiscount.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Discount must be greater than 0 and at most 1");
        }
        return effectiveDiscount;
    }
}
