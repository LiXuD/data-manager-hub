package com.dataplatform.billing.service;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BillingUsageRecorderTest {

    @Test
    void recordsUsageFromAuthenticatedBillingContract() {
        BillingDailyMapper mapper = mock(BillingDailyMapper.class);
        BillingUsageRecorder recorder = new BillingUsageRecorder(mapper);
        BillingCalculateReqDTO request = request("req-1");

        recorder.record(request, new BigDecimal("0.30"));

        verify(mapper).upsertDailyFromCallRecord(
                eq("req-1"), eq(1L), eq(2L), eq(3L), eq("company_info"),
                eq(LocalDate.of(2026, 5, 17)), eq(1L), eq(0L),
                eq(new BigDecimal("0.30")), eq(120));
    }

    @Test
    void skipsIncompleteContext() {
        BillingDailyMapper mapper = mock(BillingDailyMapper.class);
        BillingUsageRecorder recorder = new BillingUsageRecorder(mapper);

        recorder.record(new BillingCalculateReqDTO(), BigDecimal.ZERO);

        verifyNoInteractions(mapper);
    }

    private BillingCalculateReqDTO request(String requestId) {
        BillingCalculateReqDTO request = new BillingCalculateReqDTO();
        request.setRequestId(requestId);
        request.setTenantId(1L);
        request.setCallerId(2L);
        request.setVendorId(3L);
        request.setDataType("company_info");
        request.setCallTime(LocalDateTime.of(2026, 5, 17, 10, 15));
        request.setLatency(120L);
        request.setSuccess(true);
        return request;
    }
}
