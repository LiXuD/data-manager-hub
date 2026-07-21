package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.mapper.BillingEventMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BillingEventQueryService {

    private final BillingEventMapper eventMapper;

    public BillingEventQueryService(BillingEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    public Page<BillingEvent> page(Long tenantId, Long vendorId, Long interfaceId,
                                   String accountingPurpose, String status,
                                   LocalDateTime startTime, LocalDateTime endTime,
                                   int page, int pageSize) {
        LambdaQueryWrapper<BillingEvent> query = query(tenantId, vendorId, interfaceId,
                accountingPurpose, status, startTime, endTime)
                .orderByDesc(BillingEvent::getCallTime, BillingEvent::getId);
        return eventMapper.selectPage(new Page<>(page, pageSize), query);
    }

    public Map<String, Object> stats(Long tenantId, Long vendorId, Long interfaceId,
                                     String accountingPurpose,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        List<BillingEvent> events = eventMapper.selectList(query(
                tenantId, vendorId, interfaceId, accountingPurpose, null, startTime, endTime));
        BigDecimal totalAmount = events.stream().map(BillingEvent::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalQuantity = events.stream()
                .filter(item -> "USAGE".equals(item.getEventType()) || "REVERSAL".equals(item.getEventType()))
                .map(BillingEvent::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        long pending = events.stream().filter(item -> "PENDING_REVIEW".equals(item.getStatus())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventCount", events.size());
        result.put("totalAmount", totalAmount);
        result.put("totalQuantity", totalQuantity);
        result.put("pendingReviewCount", pending);
        return result;
    }

    private LambdaQueryWrapper<BillingEvent> query(Long tenantId, Long vendorId, Long interfaceId,
                                                   String accountingPurpose, String status,
                                                   LocalDateTime startTime,
                                                   LocalDateTime endTime) {
        return new LambdaQueryWrapper<BillingEvent>()
                .eq(tenantId != null, BillingEvent::getTenantId, tenantId)
                .eq(vendorId != null, BillingEvent::getVendorId, vendorId)
                .eq(interfaceId != null, BillingEvent::getInterfaceId, interfaceId)
                .eq(accountingPurpose != null && !accountingPurpose.isBlank(),
                        BillingEvent::getAccountingPurpose, accountingPurpose)
                .eq(status != null && !status.isBlank(), BillingEvent::getStatus, status)
                .ge(startTime != null, BillingEvent::getCallTime, startTime)
                .lt(endTime != null, BillingEvent::getCallTime, endTime);
    }
}
