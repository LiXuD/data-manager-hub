package com.dataplatform.billing.service;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.model.BillingPlanModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Billing 域内的模板计算引擎。 */
@Component
public class BillingPricingEngine {

    private static final int SCALE = 8;

    public PricingResult calculate(BillingPlanModel plan,
                                   BigDecimal rawQuantity,
                                   BigDecimal usageBefore,
                                   BillingChargeReqDTO request) {
        String template = plan.getTemplateCode();
        BillingPlanModel.PricingConfig pricing = plan.getPricing();
        BigDecimal quantity = normalizeQuantity(template, rawQuantity, pricing);
        BigDecimal baseAmount = switch (template) {
            case "TIERED" -> tiered(plan.getTiers(), pricing.getUnitPrice(), usageBefore, quantity);
            case "PACKAGE_COUNT" -> packageOverage(pricing, usageBefore, quantity);
            case "FLAT_PERIOD" -> BigDecimal.ZERO;
            default -> unitPrice(plan, request).multiply(quantity);
        };
        baseAmount = money(baseAmount);

        BigDecimal finalAmount = applySla(plan.getAdjustment(), baseAmount, request.getLatencyMs());
        BigDecimal adjustmentAmount = finalAmount.subtract(baseAmount);
        return new PricingResult(quantity, baseAmount, money(adjustmentAmount), money(finalAmount));
    }

    private BigDecimal unitPrice(BillingPlanModel plan, BillingChargeReqDTO request) {
        BillingPlanModel.PricingConfig pricing = plan.getPricing();
        if (Boolean.TRUE.equals(request.getCached())
                && "CUSTOM".equalsIgnoreCase(plan.getMetering().getCacheBillingPolicy())) {
            return nonNegative(pricing.getCacheUnitPrice(), "缓存单价");
        }
        return nonNegative(pricing.getUnitPrice(), "单价");
    }

    private BigDecimal normalizeQuantity(String template, BigDecimal quantity,
                                         BillingPlanModel.PricingConfig pricing) {
        BigDecimal value = quantity != null ? quantity : BigDecimal.ZERO;
        if (!"DURATION".equals(template)) {
            return value;
        }
        BigDecimal divisor = switch (pricing.getDurationUnit() == null
                ? "SECOND" : pricing.getDurationUnit().toUpperCase()) {
            case "MILLISECOND" -> BigDecimal.ONE;
            case "MINUTE" -> BigDecimal.valueOf(60_000);
            case "HOUR" -> BigDecimal.valueOf(3_600_000);
            default -> BigDecimal.valueOf(1_000);
        };
        RoundingMode rounding = switch (pricing.getDurationRounding() == null
                ? "CEILING" : pricing.getDurationRounding().toUpperCase()) {
            case "FLOOR" -> RoundingMode.FLOOR;
            case "HALF_UP" -> RoundingMode.HALF_UP;
            default -> RoundingMode.CEILING;
        };
        return value.divide(divisor, 0, rounding);
    }

    private BigDecimal tiered(List<BillingPlanModel.TierConfig> source,
                              BigDecimal defaultUnitPrice,
                              BigDecimal usageBefore,
                              BigDecimal quantity) {
        List<BillingPlanModel.TierConfig> tiers = source == null ? List.of()
                : source.stream().sorted(Comparator.comparing(BillingPlanModel.TierConfig::getTierMin)).toList();
        if (tiers.isEmpty()) {
            throw new IllegalStateException("阶梯方案缺少阶梯配置");
        }
        BigDecimal after = usageBefore.add(quantity);
        return cumulative(tiers, defaultUnitPrice, after)
                .subtract(cumulative(tiers, defaultUnitPrice, usageBefore));
    }

    private BigDecimal cumulative(List<BillingPlanModel.TierConfig> tiers,
                                  BigDecimal defaultUnitPrice,
                                  BigDecimal usage) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal covered = BigDecimal.ZERO;
        for (BillingPlanModel.TierConfig tier : tiers) {
            if (usage.compareTo(tier.getTierMin()) <= 0) break;
            BigDecimal end = tier.getTierMax() == null || usage.compareTo(tier.getTierMax()) < 0
                    ? usage : tier.getTierMax();
            BigDecimal units = end.subtract(tier.getTierMin());
            if (units.signum() <= 0) continue;
            BigDecimal price = tier.getUnitPrice() != null ? tier.getUnitPrice()
                    : nonNegative(defaultUnitPrice, "阶梯基础单价")
                        .multiply(tier.getDiscount() != null ? tier.getDiscount() : BigDecimal.ONE);
            amount = amount.add(price.multiply(units));
            covered = covered.add(units);
        }
        if (covered.compareTo(usage) != 0) {
            throw new IllegalStateException("阶梯区间未完整覆盖累计用量");
        }
        return amount;
    }

    private BigDecimal packageOverage(BillingPlanModel.PricingConfig pricing,
                                      BigDecimal usageBefore,
                                      BigDecimal quantity) {
        BigDecimal included = nonNegative(pricing.getIncludedUnits(), "套餐包含量");
        BigDecimal beforeOverage = usageBefore.subtract(included).max(BigDecimal.ZERO);
        BigDecimal afterOverage = usageBefore.add(quantity).subtract(included).max(BigDecimal.ZERO);
        return afterOverage.subtract(beforeOverage)
                .multiply(nonNegative(pricing.getOverageUnitPrice(), "超额单价"));
    }

    private BigDecimal applySla(BillingPlanModel.AdjustmentConfig adjustment,
                                BigDecimal amount, Long latencyMs) {
        if (!Boolean.TRUE.equals(adjustment.getSlaEnabled()) || latencyMs == null
                || adjustment.getSlaThresholdMs() == null
                || latencyMs <= adjustment.getSlaThresholdMs()) {
            return money(amount);
        }
        BigDecimal rate = nonNegative(adjustment.getCompensationRatePer100Ms(), "SLA补偿率");
        long overUnits = (latencyMs - adjustment.getSlaThresholdMs()) / 100;
        BigDecimal factor = BigDecimal.ONE.subtract(rate.multiply(BigDecimal.valueOf(overUnits)))
                .max(BigDecimal.ZERO);
        return money(amount.multiply(factor));
    }

    private BigDecimal nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + "不能为空或为负数");
        }
        return value;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public record PricingResult(BigDecimal quantity, BigDecimal baseAmount,
                                BigDecimal adjustmentAmount, BigDecimal finalAmount) {
    }
}
