package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.billing.entity.BillingReconciliation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ReconciliationService {

    /**
     * 执行对账
     * @param vendorId 厂商ID (null表示所有厂商)
     * @param billingDate 对账日期
     */
    void reconcile(Long vendorId, LocalDate billingDate);

    int importVendorBills(String csvContent);

    /**
     * 自动对账 (T+1执行昨日对账)
     */
    void autoReconcile();

    /**
     * 获取对账结果
     */
    Page<BillingReconciliation> list(Long vendorId, LocalDate startDate, LocalDate endDate,
                                      Integer page, Integer pageSize);

    /**
     * 获取差异列表
     */
    List<BillingReconciliation> listDiffs(Long vendorId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取对账统计
     */
    Map<String, Object> getStats(LocalDate startDate, LocalDate endDate);
}
