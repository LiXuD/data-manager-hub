package com.dataplatform.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.billing.entity.BillingReconciliation;
import com.dataplatform.billing.mapper.BillingReconciliationMapper;
import com.dataplatform.billing.mapper.CallRecordMapper;
import com.dataplatform.billing.service.ReconciliationService;
import com.dataplatform.governance.api.dto.AlertRecordCreateDTO;
import com.dataplatform.governance.api.feign.GovernanceFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReconciliationServiceImpl extends ServiceImpl<BillingReconciliationMapper, BillingReconciliation>
    implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    @Autowired
    private CallRecordMapper callRecordMapper;

    @Autowired(required = false)
    private GovernanceFeignClient governanceFeignClient;

    @Override
    public void reconcile(Long vendorId, LocalDate billingDate) {
        log.info("开始对账: vendorId={}, date={}", vendorId, billingDate);

        LambdaQueryWrapper<BillingReconciliation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingReconciliation::getBillingDate, billingDate);
        if (vendorId != null) {
            wrapper.eq(BillingReconciliation::getVendorId, vendorId);
        }

        List<BillingReconciliation> rows = list(wrapper);

        for (BillingReconciliation row : rows) {
            reconcileImportedRow(row);
        }

        log.info("对账完成: 共{}条记录", rows.size());
    }

    @Override
    public int importVendorBills(String csvContent) {
        List<VendorBillCsvParser.VendorBillRow> rows = VendorBillCsvParser.parse(csvContent);
        for (VendorBillCsvParser.VendorBillRow row : rows) {
            BillingReconciliation reconciliation = findByVendorAndDate(row.vendorId(), row.billingDate());
            if (reconciliation == null) {
                reconciliation = new BillingReconciliation();
                reconciliation.setCreatedAt(LocalDateTime.now());
            }
            reconciliation.setVendorId(row.vendorId());
            reconciliation.setVendorName(row.vendorName());
            reconciliation.setBillingDate(row.billingDate());
            reconciliation.setVendorCount(row.vendorCount());
            reconciliation.setVendorAmount(row.vendorAmount());
            reconcileImportedRow(reconciliation);
        }
        return rows.size();
    }

    private void reconcileImportedRow(BillingReconciliation reconciliation) {
        Map<String, Object> platform = callRecordMapper.selectPlatformSummaryByVendorAndDate(
                reconciliation.getVendorId(), reconciliation.getBillingDate());

        Long platformCount = longValue(platform.get("platform_count"));
        BigDecimal platformAmount = decimalValue(platform.get("platform_amount"));
        Long vendorCount = Objects.requireNonNullElse(reconciliation.getVendorCount(), 0L);
        BigDecimal vendorAmount = Objects.requireNonNullElse(reconciliation.getVendorAmount(), BigDecimal.ZERO);

        Long diffCount = platformCount - vendorCount;
        BigDecimal diffAmount = platformAmount.subtract(vendorAmount);

        String previousStatus = reconciliation.getStatus();
        BigDecimal diffRate = VendorBillCsvParser.calculateDiffRate(
                diffCount, platformCount, vendorCount, diffAmount, platformAmount, vendorAmount);
        String status = VendorBillCsvParser.classifyStatus(diffRate);

        reconciliation.setPlatformCount(platformCount);
        reconciliation.setPlatformAmount(platformAmount);
        reconciliation.setVendorCount(vendorCount);
        reconciliation.setVendorAmount(vendorAmount);
        reconciliation.setDiffCount(diffCount);
        reconciliation.setDiffAmount(diffAmount);
        reconciliation.setDiffRate(diffRate);
        reconciliation.setStatus(status);
        reconciliation.setReconciledAt(LocalDateTime.now());
        if (reconciliation.getCreatedAt() == null) {
            reconciliation.setCreatedAt(LocalDateTime.now());
        }

        if (reconciliation.getId() == null) {
            save(reconciliation);
        } else {
            updateById(reconciliation);
        }

        if (shouldPublishDiffAlert(previousStatus, status)) {
            publishDiffAlert(reconciliation);
        }
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

    private BillingReconciliation findByVendorAndDate(Long vendorId, LocalDate billingDate) {
        return getOne(new LambdaQueryWrapper<BillingReconciliation>()
                .eq(BillingReconciliation::getVendorId, vendorId)
                .eq(BillingReconciliation::getBillingDate, billingDate)
                .last("LIMIT 1"));
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private boolean shouldPublishDiffAlert(String previousStatus, String status) {
        return !"matched".equals(status) && !status.equals(previousStatus);
    }

    private void publishDiffAlert(BillingReconciliation reconciliation) {
        if (governanceFeignClient == null) {
            log.warn("GovernanceFeignClient不可用，跳过对账差异告警: reconciliationId={}", reconciliation.getId());
            return;
        }
        try {
            AlertRecordCreateDTO dto = new AlertRecordCreateDTO();
            dto.setAlertType("billing_reconciliation");
            dto.setAlertTitle("计费对账差异");
            dto.setLevel("diff_error".equals(reconciliation.getStatus()) ? "error" : "warning");
            dto.setAlertMessage("厂商 " + reconciliation.getVendorName()
                    + " 在 " + reconciliation.getBillingDate()
                    + " 存在对账差异，差异率 " + reconciliation.getDiffRate());
            dto.setTriggeredValue(reconciliation.getDiffRate());
            dto.setStatus("pending");
            governanceFeignClient.createAlertRecord(dto);
        } catch (Exception ex) {
            log.warn("发送对账差异告警失败: reconciliationId={}", reconciliation.getId(), ex);
        }
    }
}
