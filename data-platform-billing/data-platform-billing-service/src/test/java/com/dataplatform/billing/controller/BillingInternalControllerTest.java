package com.dataplatform.billing.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.api.dto.BillingCalculateRespDTO;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.billing.service.BillingUsageRecorder;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BillingInternalControllerTest {

    private final BillingService billingService = mock(BillingService.class);
    private final BillingUsageRecorder recorder = mock(BillingUsageRecorder.class);
    private final BillingInternalController controller =
            new BillingInternalController(billingService, recorder);

    @Test
    void calculatesAndRecordsBillableSuccess() {
        BillingCalculateReqDTO request = request();
        when(billingService.calculateCost("vendor-a", "personal", 1, 120L))
                .thenReturn(new BigDecimal("0.25"));

        Result<BillingCalculateRespDTO> result = controller.calculateCost(request);

        assertEquals(new BigDecimal("0.25"), result.getData().getCost());
        verify(recorder).record(request, new BigDecimal("0.25"));
    }

    @Test
    void recordsZeroWithoutCalculatingForFailedCall() {
        BillingCalculateReqDTO request = request();
        request.setSuccess(false);

        Result<BillingCalculateRespDTO> result = controller.calculateCost(request);

        assertEquals(BigDecimal.ZERO, result.getData().getCost());
        verify(billingService, never()).calculateCost(any(), any(), anyInt(), anyLong());
        verify(recorder).record(request, BigDecimal.ZERO);
    }

    private BillingCalculateReqDTO request() {
        BillingCalculateReqDTO request = new BillingCalculateReqDTO();
        request.setDataType("personal");
        request.setVendorCode("vendor-a");
        request.setCallCount(1);
        request.setLatency(120L);
        request.setSuccess(true);
        request.setBillable(true);
        return request;
    }
}
