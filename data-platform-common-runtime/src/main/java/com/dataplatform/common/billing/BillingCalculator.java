package com.dataplatform.common.billing;

import com.dataplatform.common.entity.unified.BillingRuleDO;
import java.math.BigDecimal;

/**
 * 计费计算器接口
 */
public interface BillingCalculator {

    /**
     * 计算费用
     *
     * @param rule        计费规则
     * @param callCount   调用次数
     * @param latencyMs   响应时间(毫秒)
     * @return 费用金额
     */
    BigDecimal calculate(BillingRuleDO rule, long callCount, Integer latencyMs);

    /**
     * 计算单次调用费用
     *
     * @param rule      计费规则
     * @param latencyMs 响应时间(毫秒)
     * @return 单次费用
     */
    BigDecimal calculateSingle(BillingRuleDO rule, Integer latencyMs);
}
