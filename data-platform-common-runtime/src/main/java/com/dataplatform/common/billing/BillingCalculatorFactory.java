package com.dataplatform.common.billing;

import com.dataplatform.common.enums.BillingType;
import org.springframework.stereotype.Component;

/**
 * 计费计算器工厂
 * 根据计费类型返回对应的计费器实现
 */
@Component
public class BillingCalculatorFactory {

    private static final BillingCalculator STANDARD = new StandardBillingCalculator();
    private static final BillingCalculator TIERED = new TieredBillingCalculator();
    private static final BillingCalculator DYNAMIC = new DynamicBillingCalculator();

    /**
     * 获取计费计算器
     */
    public BillingCalculator getCalculator(String billingType) {
        BillingType parsed = BillingType.fromCode(billingType);
        if (parsed == null) {
            throw new IllegalArgumentException("Unsupported billing type: " + billingType);
        }
        return getCalculator(parsed);
    }

    /**
     * 获取计费计算器
     */
    public BillingCalculator getCalculator(BillingType billingType) {
        if (billingType == null) {
            throw new IllegalArgumentException("Billing type is required");
        }

        switch (billingType) {
            case TIERED:
                return TIERED;
            case DYNAMIC:
                return DYNAMIC;
            default:
                return STANDARD;
        }
    }
}
