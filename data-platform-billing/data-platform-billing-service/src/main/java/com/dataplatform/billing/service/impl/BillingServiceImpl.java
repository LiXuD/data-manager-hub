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
import java.util.List;
import java.util.Map;

/**
 * 计费域日账单查询服务实现。
 * <p>只负责 billing_daily 的分页/统计/导出；计费定价由版本化方案体系（BillingPlan + 事件账本）承载。</p>
 */
@Service
public class BillingServiceImpl extends ServiceImpl<BillingDailyMapper, BillingDaily>
        implements BillingService {

    @Override
    public Page<BillingDaily> pageQuery(Long tenantId, Long vendorId, LocalDate startDate, LocalDate endDate,
                                         Integer page, Integer pageSize) {
        Page<BillingDaily> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<BillingDaily> wrapper = new LambdaQueryWrapper<>();

        if (tenantId != null) {
            wrapper.eq(BillingDaily::getTenantId, tenantId);
        }
        if (vendorId != null) {
            wrapper.eq(BillingDaily::getVendorId, vendorId);
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

        List<BillingDaily> list = list(wrapper);

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

    @Override
    public byte[] export(Long tenantId, Long vendorId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<BillingDaily> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(BillingDaily::getTenantId, tenantId);
        }
        if (vendorId != null) {
            wrapper.eq(BillingDaily::getVendorId, vendorId);
        }
        if (startDate != null) {
            wrapper.ge(BillingDaily::getBillingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(BillingDaily::getBillingDate, endDate);
        }
        wrapper.orderByDesc(BillingDaily::getBillingDate);

        StringBuilder csv = new StringBuilder("id,tenant_id,caller_id,vendor_id,data_type,billing_date,call_count,success_count,fail_count,total_cost\n");
        for (BillingDaily billing : list(wrapper)) {
            csv.append(billing.getId()).append(',')
                    .append(billing.getTenantId()).append(',')
                    .append(billing.getCallerId()).append(',')
                    .append(billing.getVendorId()).append(',')
                    .append(csvCell(billing.getDataType())).append(',')
                    .append(billing.getBillingDate()).append(',')
                    .append(billing.getCallCount()).append(',')
                    .append(billing.getSuccessCount()).append(',')
                    .append(billing.getFailCount()).append(',')
                    .append(billing.getTotalCost())
                    .append('\n');
        }
        return ("\uFEFF" + csv).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
