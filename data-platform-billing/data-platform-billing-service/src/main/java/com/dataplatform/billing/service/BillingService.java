package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.billing.entity.BillingDaily;

import java.time.LocalDate;
import java.util.Map;

/**
 * 计费域日账单查询服务。
 * <p>业务服务接口，定义 billing_daily 的分页/统计/导出能力。</p>
 */
public interface BillingService extends IService<BillingDaily> {

    /**
     * 分页查询账单
     */
    Page<BillingDaily> pageQuery(Long tenantId, Long vendorId, LocalDate startDate, LocalDate endDate,
                                  Integer page, Integer pageSize);

    /**
     * 获取账单统计
     */
    Map<String, Object> getBillingStats(Long tenantId, LocalDate startDate, LocalDate endDate);

    /**
     * 导出账单 CSV
     */
    byte[] export();
}
