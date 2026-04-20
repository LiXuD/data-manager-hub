package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.billing.entity.BillingDaily;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BillingService extends IService<BillingDaily> {
    
    BigDecimal calculateCost(String dataType, int callCount);
    
    void recordDailyBilling(LocalDate billingDate);
    
    Page<BillingDaily> pageQuery(Long tenantId, LocalDate startDate, LocalDate endDate, 
                                  Integer page, Integer pageSize);
    
    Map<String, Object> getBillingStats(Long tenantId, LocalDate startDate, LocalDate endDate);
}
