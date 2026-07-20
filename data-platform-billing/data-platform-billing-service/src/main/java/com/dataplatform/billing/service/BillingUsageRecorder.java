package com.dataplatform.billing.service;

import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BillingUsageRecorder {

    private static final String DEFAULT_DATA_TYPE = "unknown";

    private final BillingDailyMapper billingDailyMapper;

    public BillingUsageRecorder(BillingDailyMapper billingDailyMapper) {
        this.billingDailyMapper = billingDailyMapper;
    }

    public void record(BillingCalculateReqDTO request, BigDecimal cost) {
        if (!StringUtils.hasText(request.getRequestId())
                || request.getTenantId() == null
                || request.getCallerId() == null) {
            return;
        }
        boolean success = request.getSuccess() == null || Boolean.TRUE.equals(request.getSuccess());
        LocalDate billingDate = request.getCallTime() != null
                ? request.getCallTime().toLocalDate()
                : LocalDate.now();
        String dataType = StringUtils.hasText(request.getDataType())
                ? request.getDataType()
                : DEFAULT_DATA_TYPE;
        billingDailyMapper.upsertDailyFromCallRecord(
                request.getRequestId(),
                request.getTenantId(),
                request.getCallerId(),
                request.getVendorId(),
                dataType,
                billingDate,
                success ? 1L : 0L,
                success ? 0L : 1L,
                cost != null ? cost : BigDecimal.ZERO,
                request.getLatency() != null ? request.getLatency().intValue() : null);
    }
}
