package com.dataplatform.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingReconciliation;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.billing.mapper.BillingReconciliationMapper;
import com.dataplatform.billing.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReconciliationServiceImpl extends ServiceImpl<BillingReconciliationMapper, BillingReconciliation>
    implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    // 差异率阈值
    private static final BigDecimal DIFF_RATE_WARNING = new BigDecimal("0.01");   // 1%
    private static final BigDecimal DIFF_RATE_ERROR = new BigDecimal("0.01");     // 1%

    @Autowired
    private BillingDailyMapper billingDailyMapper;

    @Override
    public void reconcile(Long vendorId, LocalDate billingDate) {
        log.info("开始对账: vendorId={}, date={}", vendorId, billingDate);

        // 1. 获取平台调用数据 (从billing_daily聚合)
        LambdaQueryWrapper<BillingDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingDaily::getBillingDate, billingDate);
        if (vendorId != null) {
            wrapper.eq(BillingDaily::getVendorId, vendorId);
        }

        List<BillingDaily> dailyList = billingDailyMapper.selectList(wrapper);

        for (BillingDaily daily : dailyList) {
            createReconciliationRecord(daily, billingDate);
        }

        log.info("对账完成: 共{}条记录", dailyList.size());
    }

    /**
     * 创建对账记录
     */
    private void createReconciliationRecord(BillingDaily daily, LocalDate billingDate) {
        Long vendorId = daily.getVendorId();

        // 平台数据
        Long platformCount = daily.getCallCount();
        BigDecimal platformAmount = daily.getTotalCost();

        // TODO: 实际应该调用厂商对账API获取vendorCount和vendorAmount
        // 这里用模拟数据，实际需要对接厂商的账单接口
        Long vendorCount = platformCount;  // 假设一致
        BigDecimal vendorAmount = platformAmount;

        // 计算差异
        Long diffCount = platformCount - vendorCount;
        BigDecimal diffAmount = platformAmount.subtract(vendorAmount);

        // 计算差异率
        BigDecimal diffRate = BigDecimal.ZERO;
        if (platformCount > 0) {
            diffRate = BigDecimal.valueOf(diffCount)
                .divide(BigDecimal.valueOf(platformCount), 6, RoundingMode.HALF_UP);
        }

        // 判断状态
        String status;
        if (diffRate.abs().compareTo(DIFF_RATE_WARNING) <= 0) {
            status = "matched";  // 差异≤1%，自动匹配
        } else if (diffRate.abs().compareTo(DIFF_RATE_ERROR) <= 0) {
            status = "diff_warning";  // 1%<差异≤1%，生成报告，人工确认
        } else {
            status = "diff_error";  // 差异>1%，触发告警
            log.error("对账差异过大: vendorId={}, date={}, diffRate={}",
                     vendorId, billingDate, diffRate);
        }

        // 保存对账记录
        BillingReconciliation reconciliation = new BillingReconciliation();
        reconciliation.setVendorId(vendorId);
        reconciliation.setVendorName("vendor_" + vendorId);
        reconciliation.setBillingDate(billingDate);
        reconciliation.setPlatformCount(platformCount);
        reconciliation.setPlatformAmount(platformAmount);
        reconciliation.setVendorCount(vendorCount);
        reconciliation.setVendorAmount(vendorAmount);
        reconciliation.setDiffCount(diffCount);
        reconciliation.setDiffAmount(diffAmount);
        reconciliation.setDiffRate(diffRate);
        reconciliation.setStatus(status);
        reconciliation.setCreatedAt(LocalDateTime.now());

        save(reconciliation);
    }

    @Override
    public void autoReconcile() {
        // T+1对账：昨天
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("开始自动对账: date={}", yesterday);

        // 对所有厂商进行对账
        reconcile(null, yesterday);
    }

    @Override
    public Page<BillingReconciliation> list(Long vendorId, LocalDate startDate, LocalDate endDate,
                                              Integer page, Integer pageSize) {
        Page<BillingReconciliation> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<BillingReconciliation> wrapper = new LambdaQueryWrapper<>();

        if (vendorId != null) {
            wrapper.eq(BillingReconciliation::getVendorId, vendorId);
        }
        if (startDate != null) {
            wrapper.ge(BillingReconciliation::getBillingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(BillingReconciliation::getBillingDate, endDate);
        }

        wrapper.orderByDesc(BillingReconciliation::getBillingDate);
        return page(pageParam, wrapper);
    }

    @Override
    public List<BillingReconciliation> listDiffs(Long vendorId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<BillingReconciliation> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BillingReconciliation::getStatus, "diff_warning", "diff_error");
        if (vendorId != null) {
            wrapper.eq(BillingReconciliation::getVendorId, vendorId);
        }
        if (startDate != null) {
            wrapper.ge(BillingReconciliation::getBillingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(BillingReconciliation::getBillingDate, endDate);
        }
        wrapper.orderByDesc(BillingReconciliation::getBillingDate);
        return list(wrapper);
    }

    @Override
    public Map<String, Object> getStats(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<BillingReconciliation> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            wrapper.ge(BillingReconciliation::getBillingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(BillingReconciliation::getBillingDate, endDate);
        }

        List<BillingReconciliation> list = list(wrapper);

        long total = list.size();
        long matched = list.stream().filter(r -> "matched".equals(r.getStatus())).count();
        long warnings = list.stream().filter(r -> "diff_warning".equals(r.getStatus())).count();
        long errors = list.stream().filter(r -> "diff_error".equals(r.getStatus())).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("matched", matched);
        stats.put("diffWarnings", warnings);
        stats.put("diffErrors", errors);
        stats.put("matchRate", total > 0 ? (double) matched / total : 0);

        return stats;
    }
}