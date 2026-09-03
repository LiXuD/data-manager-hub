package com.dataplatform.billing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.access.call.api.dto.VendorCallSummaryDTO;
import com.dataplatform.access.call.api.feign.CallStatsInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.billing.entity.BillingReconciliation;
import com.dataplatform.billing.mapper.BillingReconciliationMapper;
import com.dataplatform.billing.service.BillingReconciliationException;
import com.dataplatform.governance.api.feign.GovernanceInternalFeignClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReconciliationServiceImplTest {

    private BillingReconciliationMapper mapper;
    private CallStatsInternalFeignClient callStatsClient;
    private GovernanceInternalFeignClient governanceClient;
    private ReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(BillingReconciliationMapper.class);
        callStatsClient = mock(CallStatsInternalFeignClient.class);
        governanceClient = mock(GovernanceInternalFeignClient.class);
        service = new ReconciliationServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "callStatsClient", callStatsClient);
        ReflectionTestUtils.setField(service, "governanceFeignClient", governanceClient);
    }

    @Test
    void rejectsEmptyOrMalformedBillFileAsBadRequest() {
        BillingReconciliationException empty = assertThrows(BillingReconciliationException.class,
                () -> service.importVendorBills("billingDate,vendorId,vendorName,vendorCount,vendorAmount\n"));
        assertEquals(400, empty.getStatus());

        BillingReconciliationException malformed = assertThrows(BillingReconciliationException.class,
                () -> service.importVendorBills("billingDate,vendorId,vendorName,vendorCount,vendorAmount\n"
                        + "not-a-date,1,Vendor,1,1.00\n"));
        assertEquals("BILLING_RECONCILIATION_INVALID_CSV", malformed.getErrorCode());
    }

    @Test
    void rejectsMissingReconciliationDate() {
        BillingReconciliationException exception = assertThrows(BillingReconciliationException.class,
                () -> service.reconcile(1L, null));
        assertEquals(400, exception.getStatus());
    }

    @Test
    void publishesAlertWhenFirstReconciliationHasDifference() {
        VendorCallSummaryDTO summary = new VendorCallSummaryDTO();
        summary.setCallCount(0L);
        summary.setTotalAmount(BigDecimal.ZERO);
        when(callStatsClient.getVendorDailySummary(1L, "2026-05-16"))
                .thenReturn(Result.success(summary));
        when(mapper.insert(any(BillingReconciliation.class))).thenReturn(1);
        when(governanceClient.createAlertRecord(any())).thenReturn(Result.success(null));

        int imported = service.importVendorBills("billingDate,vendorId,vendorName,vendorCount,vendorAmount\n"
                + "2026-05-16,1,Vendor,10,10.00\n");

        assertEquals(1, imported);
        verify(governanceClient).createAlertRecord(any());
    }

    @Test
    void failsWhenReconciliationRowCannotBePersisted() {
        VendorCallSummaryDTO summary = new VendorCallSummaryDTO();
        summary.setCallCount(1L);
        summary.setTotalAmount(BigDecimal.ONE);
        when(callStatsClient.getVendorDailySummary(1L, "2026-05-16"))
                .thenReturn(Result.success(summary));
        when(mapper.insert(any(BillingReconciliation.class))).thenReturn(0);

        BillingReconciliationException exception = assertThrows(BillingReconciliationException.class,
                () -> service.importVendorBills("billingDate,vendorId,vendorName,vendorCount,vendorAmount\n"
                        + "2026-05-16,1,Vendor,1,1.00\n"));
        assertEquals("BILLING_RECONCILIATION_PERSIST_FAILED", exception.getErrorCode());
    }
}
