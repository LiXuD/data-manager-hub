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
        return getCalculator(BillingType.fromCode(billingType));
    }

    /**
     * 获取计费计算器
     */
    public BillingCalculator getCalculator(BillingType billingType) {
        if (billingType == null) {
            return STANDARD;
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
