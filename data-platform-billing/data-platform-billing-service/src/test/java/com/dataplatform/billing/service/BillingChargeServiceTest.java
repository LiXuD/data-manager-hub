package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.api.dto.BillingChargeRespDTO;
import com.dataplatform.billing.api.dto.BillingAdditionalPlanDTO;
import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.billing.mapper.BillingEventMapper;
import com.dataplatform.billing.mapper.BillingUsageBalanceMapper;
import com.dataplatform.billing.model.BillingPlanModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BillingChargeServiceTest {

    @Test
    void repeatedRequestReturnsExistingLedgerEventWithoutRepricing() {
        BillingEventMapper eventMapper = mock(BillingEventMapper.class);
        BillingEvent existing = new BillingEvent();
        existing.setId(7L);
        existing.setFinalAmount(new BigDecimal("1.25"));
        when(eventMapper.selectByRequestId("req-1")).thenReturn(existing);
        BillingPlanService planService = mock(BillingPlanService.class);
        BillingUsageBalanceMapper usageMapper = mock(BillingUsageBalanceMapper.class);
        BillingChargeService service = service(planService, eventMapper, usageMapper,
                mock(BillingDailyMapper.class), mock(BillingRecurringChargeService.class));
        BillingChargeReqDTO request = new BillingChargeReqDTO();
        request.setRequestId("req-1");
        request.setPlanId(1L);
        request.setPlanVersion(1);

        BillingChargeRespDTO response = service.charge(request);

        assertEquals(7L, response.getBillingEventId());
        assertEquals(0, new BigDecimal("1.25").compareTo(response.getFinalAmount()));
        verifyNoInteractions(planService, usageMapper);
        verify(eventMapper, never()).insert(any(BillingEvent.class));
    }

    @Test
    void successfulChargePinsPolicyReservesUsageAndWritesOneEvent() {
        BillingEventMapper eventMapper = mock(BillingEventMapper.class);
        BillingUsageBalanceMapper usageMapper = mock(BillingUsageBalanceMapper.class);
        BillingDailyMapper dailyMapper = mock(BillingDailyMapper.class);
        BillingRecurringChargeService recurring = mock(BillingRecurringChargeService.class);
        BillingPlanService planService = mock(BillingPlanService.class);
        BillingConfigCodec codec = new BillingConfigCodec(new ObjectMapper());
        BillingPlanModel model = new BillingPlanModel();
        model.setTemplateCode("PER_CALL");
        model.getPricing().setUnitPrice(new BigDecimal("0.5"));
        BillingPlan plan = plan(codec, model);
        when(planService.getEntity(1L)).thenReturn(plan);
        when(planService.get(1L)).thenReturn(model);
        when(usageMapper.selectUsedQuantity(1L, LocalDate.of(2026, 7, 1), "VENDOR_INTERFACE"))
                .thenReturn(new BigDecimal("10"));
        BillingChargeService service = service(planService, eventMapper, usageMapper, dailyMapper, recurring);
        BillingChargeReqDTO request = request(codec.sha256(plan.getMeteringConfig()));

        BillingChargeRespDTO response = service.charge(request);

        assertEquals(0, new BigDecimal("0.50000000").compareTo(response.getFinalAmount()));
        verify(usageMapper).increment(1L, LocalDate.of(2026, 7, 1), "VENDOR_INTERFACE", BigDecimal.ONE);
        ArgumentCaptor<BillingEvent> captured = ArgumentCaptor.forClass(BillingEvent.class);
        verify(eventMapper).insert(captured.capture());
        BillingEvent event = captured.getValue();
        assertEquals("VENDOR_PAYABLE", event.getAccountingPurpose());
        assertEquals(0, new BigDecimal("10").compareTo(event.getUsageBefore()));
        assertEquals("POSTED", event.getStatus());
    }

    @Test
    void oneCallWritesPayableAndInternalChargebackAsSeparateIdempotentEvents() {
        BillingEventMapper eventMapper = mock(BillingEventMapper.class);
        BillingUsageBalanceMapper usageMapper = mock(BillingUsageBalanceMapper.class);
        BillingPlanService planService = mock(BillingPlanService.class);
        BillingConfigCodec codec = new BillingConfigCodec(new ObjectMapper());
        BillingPlanModel payableModel = new BillingPlanModel();
        payableModel.setTemplateCode("PER_CALL");
        payableModel.getPricing().setUnitPrice(new BigDecimal("0.5"));
        BillingPlan payable = plan(codec, payableModel);
        BillingPlanModel internalModel = new BillingPlanModel();
        internalModel.setTemplateCode("PER_CALL");
        internalModel.getPricing().setUnitPrice(new BigDecimal("0.8"));
        BillingPlan internal = plan(codec, internalModel);
        internal.setId(2L);
        internal.setPlanCode("C");
        internal.setAccountingPurpose("INTERNAL_CHARGEBACK");
        when(planService.getEntity(1L)).thenReturn(payable);
        when(planService.get(1L)).thenReturn(payableModel);
        when(planService.getEntity(2L)).thenReturn(internal);
        when(planService.get(2L)).thenReturn(internalModel);
        BillingChargeReqDTO request = request(codec.sha256(payable.getMeteringConfig()));
        BillingAdditionalPlanDTO additional = new BillingAdditionalPlanDTO();
        additional.setPlanId(2L);
        additional.setPlanVersion(2);
        additional.setAccountingPurpose("INTERNAL_CHARGEBACK");
        additional.setPolicyHash(codec.sha256(internal.getMeteringConfig()));
        request.setAdditionalPlans(List.of(additional));
        BillingDailyMapper dailyMapper = mock(BillingDailyMapper.class);
        BillingChargeService service = service(planService, eventMapper, usageMapper,
                dailyMapper, mock(BillingRecurringChargeService.class));

        BillingChargeRespDTO response = service.charge(request);

        assertEquals(0, new BigDecimal("0.50000000").compareTo(response.getFinalAmount()));
        ArgumentCaptor<BillingEvent> events = ArgumentCaptor.forClass(BillingEvent.class);
        verify(eventMapper, org.mockito.Mockito.times(2)).insert(events.capture());
        assertEquals(List.of("VENDOR_PAYABLE", "INTERNAL_CHARGEBACK"),
                events.getAllValues().stream().map(BillingEvent::getAccountingPurpose).toList());
        assertEquals("req-new:CHARGEBACK:2", events.getAllValues().get(1).getRequestId());
        assertEquals(0, new BigDecimal("0.80000000")
                .compareTo(events.getAllValues().get(1).getFinalAmount()));
        verify(dailyMapper).upsertDailyFromCallRecord(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private BillingChargeService service(BillingPlanService planService,
                                         BillingEventMapper eventMapper,
                                         BillingUsageBalanceMapper usageMapper,
                                         BillingDailyMapper dailyMapper,
                                         BillingRecurringChargeService recurring) {
        return new BillingChargeService(planService, new BillingMeteringEvaluator(),
                new BillingPricingEngine(), new BillingPeriodResolver(), recurring,
                eventMapper, usageMapper, dailyMapper,
                new BillingConfigCodec(new ObjectMapper()));
    }

    private BillingPlan plan(BillingConfigCodec codec, BillingPlanModel model) {
        BillingPlan plan = new BillingPlan();
        plan.setId(1L);
        plan.setPlanCode("P");
        plan.setVersion(2);
        plan.setTemplateCode("PER_CALL");
        plan.setAccountingPurpose("VENDOR_PAYABLE");
        plan.setVendorId(3L);
        plan.setVendorCode("V");
        plan.setInterfaceId(4L);
        plan.setInterfaceCode("I");
        plan.setCurrency("CNY");
        plan.setTimezone("Asia/Shanghai");
        plan.setSettlementCycle("MONTH");
        plan.setMeteringConfig(codec.write(model.getMetering()));
        plan.setEffectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        return plan;
    }

    private BillingChargeReqDTO request(String policyHash) {
        BillingChargeReqDTO request = new BillingChargeReqDTO();
        request.setRequestId("req-new");
        request.setPlanId(1L);
        request.setPlanVersion(2);
        request.setPolicyHash(policyHash);
        request.setVendorCode("V");
        request.setInterfaceCode("I");
        request.setTenantId(11L);
        request.setCallerId(22L);
        request.setCallTime(LocalDateTime.of(2026, 7, 21, 10, 0));
        request.setSuccess(true);
        request.setCached(false);
        request.setResponseContractValid(true);
        request.setLatencyMs(30L);
        return request;
    }
}
