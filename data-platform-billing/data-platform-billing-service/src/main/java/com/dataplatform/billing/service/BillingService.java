package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingRule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 计费域计费计算的 Billing Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
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
     * 按厂商和数据类型计算费用。
     */
    BigDecimal calculateCost(String vendorCode, String dataType, int callCount, long latency);

    /**
     * 分页查询账单
     */
    Page<BillingDaily> pageQuery(Long tenantId, Long vendorId, LocalDate startDate, LocalDate endDate,
                                  Integer page, Integer pageSize);

    /**
     * 获取账单统计
     */
    Map<String, Object> getBillingStats(Long tenantId, LocalDate startDate, LocalDate endDate);

    // BillingRule 相关方法
    List<BillingRule> listRules();
    BillingRule getRuleById(Long id);
    void saveRule(BillingRule rule);
    void updateRule(BillingRule rule);
    void deleteRule(Long id);
    Map<String, Object> getStats();
    byte[] export();

    /**
     * 根据厂商编码和数据类型获取计费规则
     */
    BillingRule getRuleByVendorAndDataType(String vendorCode, String dataType);
}
