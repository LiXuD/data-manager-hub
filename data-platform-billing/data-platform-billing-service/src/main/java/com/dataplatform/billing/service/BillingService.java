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
     * 按厂商和接口计算费用；数据类型不参与规则匹配。
     */
    BigDecimal calculateCost(String vendorCode, String interfaceCode, int callCount, long latency);

    /**
     * 按自然月累计用量计算本次增量费用；requestId 用于保证用量占用幂等。
     */
    BigDecimal calculateCost(String vendorCode, String interfaceCode, int callCount, long latency,
                             String requestId, LocalDate billingDate);

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
     * 根据厂商编码和接口编码获取唯一计费规则。
     */
    BillingRule getRuleByVendorAndInterface(String vendorCode, String interfaceCode);
}
