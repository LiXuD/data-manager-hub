package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.billing.entity.BillingDaily;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BillingService extends IService<BillingDaily> {

    /**
     * 计算费用 - 支持阶梯计费
     */
    BigDecimal calculateCost(String dataType, int callCount);

    /**
     * 计算费用(带响应时间) - 支持SLA补偿
     * @param dataType 数据类型
     * @param callCount 调用次数
     * @param latency 响应时间(毫秒)
     * @return 实际费用
     */
    BigDecimal calculateCost(String dataType, int callCount, long latency);

    /**
     * 记录每日账单
     */
    void recordDailyBilling(LocalDate billingDate);

    /**
     * 分页查询账单
     */
    Page<BillingDaily> pageQuery(Long tenantId, LocalDate startDate, LocalDate endDate,
                                  Integer page, Integer pageSize);

    /**
     * 获取账单统计
     */
    Map<String, Object> getBillingStats(Long tenantId, LocalDate startDate, LocalDate endDate);
}
