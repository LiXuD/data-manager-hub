package com.dataplatform.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.billing.service.BillingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class BillingServiceImpl extends ServiceImpl<BillingDailyMapper, BillingDaily> 
    implements BillingService {
    
    // 从数据库读取或缓存
    private static final Map<String, BigDecimal> UNIT_PRICES = Map.of(
        "company_info", new BigDecimal("0.30"),
        "person_phone", new BigDecimal("0.15"),
        "id_card_verify", new BigDecimal("0.20")
    );

    @Override
    public BigDecimal calculateCost(String dataType, int callCount) {
        BigDecimal unitPrice = UNIT_PRICES.getOrDefault(dataType, BigDecimal.ZERO);
        return unitPrice.multiply(BigDecimal.valueOf(callCount));
    }

    @Override
    public void recordDailyBilling(LocalDate billingDate) {
        // 从call_record聚合数据，写入billing_daily
        // 这里简化处理，实际应从Kafka消费或查询数据库
    }

    @Override
    public Page<BillingDaily> pageQuery(Long tenantId, LocalDate startDate, LocalDate endDate,
                                         Integer page, Integer pageSize) {
        Page<BillingDaily> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<BillingDaily> wrapper = new LambdaQueryWrapper<>();
        
        if (tenantId != null) {
            wrapper.eq(BillingDaily::getTenantId, tenantId);
        }
        if (startDate != null) {
            wrapper.ge(BillingDaily::getBillingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(BillingDaily::getBillingDate, endDate);
        }
        
        wrapper.orderByDesc(BillingDaily::getBillingDate);
        return page(pageParam, wrapper);
    }

    @Override
    public Map<String, Object> getBillingStats(Long tenantId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<BillingDaily> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(BillingDaily::getTenantId, tenantId);
        }
        if (startDate != null) {
            wrapper.ge(BillingDaily::getBillingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(BillingDaily::getBillingDate, endDate);
        }
        
        java.util.List<BillingDaily> list = list(wrapper);
        
        long totalCallCount = list.stream().mapToLong(BillingDaily::getCallCount).sum();
        BigDecimal totalCost = list.stream()
            .map(BillingDaily::getTotalCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCallCount", totalCallCount);
        stats.put("totalCost", totalCost);
        stats.put("days", list.size());
        
        return stats;
    }
}
