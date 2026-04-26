package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 动态计费计算器
 * 根据响应时间动态调整费用 (SLA补偿)
 *
 * 计算规则：
 * - 响应时间 <= SLA阈值: 正常计费
 * - 响应时间 > SLA阈值: 每超过100ms, 费用减少 compensationRate
 */
public class DynamicBillingCalculator implements BillingCalculator {

    /** 默认SLA阈值(毫秒) */
    private static final int DEFAULT_SLA_THRESHOLD = 2000;

    /** 默认补偿系数 */
    private static final BigDecimal DEFAULT_COMPENSATION_RATE = new BigDecimal("0.10");

    @Override
    public BigDecimal calculate(BillingRuleDO rule, long callCount, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseAmount = rule.getUnitPrice()
            .multiply(BigDecimal.valueOf(callCount));

        // 如果没有响应时间数据，返回基础费用
        if (latencyMs == null) {
            return baseAmount.setScale(4, RoundingMode.HALF_UP);
        }

        // 计算补偿系数
        BigDecimal compensationFactor = calculateCompensationFactor(rule, latencyMs);

        return baseAmount.multiply(compensationFactor)
            .setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs) {
        if (rule == null || rule.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal basePrice = rule.getUnitPrice();

        // 如果没有响应时间数据，返回基础价格
        if (latencyMs == null) {
            return basePrice;
        }

        // 计算补偿系数
        BigDecimal compensationFactor = calculateCompensationFactor(rule, latencyMs);

        return basePrice.multiply(compensationFactor)
            .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算补偿系数
     * 返回值: 0.0 ~ 1.0
     */
    private BigDecimal calculateCompensationFactor(BillingRuleDO rule, Integer latencyMs) {
        int slaThreshold = DEFAULT_SLA_THRESHOLD;
        BigDecimal compensationRate = DEFAULT_COMPENSATION_RATE;

        // 响应时间在SLA内，无补偿
        if (latencyMs <= slaThreshold) {
            return BigDecimal.ONE;
        }

        // 超过SLA，计算补偿
        int overTime = latencyMs - slaThreshold;
        int overUnits = overTime / 100;  // 每超过100ms为一个单位

        // 补偿金额 = overUnits * compensationRate
        BigDecimal totalCompensation = compensationRate.multiply(BigDecimal.valueOf(overUnits));

        // 确保补偿不超过100%
        if (totalCompensation.compareTo(BigDecimal.ONE) >= 0) {
            return BigDecimal.ZERO;
        }

        // 实际费用系数 = 1 - 补偿比例
        return BigDecimal.ONE.subtract(totalCompensation);
    }
}
