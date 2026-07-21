package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.mapper.BillingEventMapper;
import com.dataplatform.billing.mapper.BillingUsageBalanceMapper;
import com.dataplatform.billing.model.BillingPlanModel;
import com.dataplatform.billing.model.BillingReversalCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BillingReversalServiceTest {

    @Test
    void createsNegativeImmutableEventAndRestoresUsage() {
        BillingEventMapper eventMapper = mock(BillingEventMapper.class);
        BillingUsageBalanceMapper usageMapper = mock(BillingUsageBalanceMapper.class);
        BillingPlanService planService = mock(BillingPlanService.class);
        BillingEvent original = original();
        when(eventMapper.selectById(9L)).thenReturn(original);
        BillingPlanModel plan = new BillingPlanModel();
        plan.getMetering().setAggregationScope("CALLER");
        when(planService.get(3L)).thenReturn(plan);
        BillingReversalService service = new BillingReversalService(
                eventMapper, usageMapper, planService, new BillingConfigCodec(new ObjectMapper()));

        BillingEvent result = service.reverse(9L, command("reverse-1"));

        ArgumentCaptor<BillingEvent> event = ArgumentCaptor.forClass(BillingEvent.class);
        verify(eventMapper).insert(event.capture());
        assertSame(result, event.getValue());
        assertEquals("REVERSAL", result.getEventType());
        assertEquals(9L, result.getOriginalEventId());
        assertEquals(0, new BigDecimal("-2.5").compareTo(result.getFinalAmount()));
        assertEquals(0, new BigDecimal("-5").compareTo(result.getQuantity()));
        verify(usageMapper).decrement(3L, LocalDate.of(2026, 7, 1), "CALLER:22", new BigDecimal("5"));
    }

    @Test
    void sameReversalRequestIsIdempotent() {
        BillingEventMapper eventMapper = mock(BillingEventMapper.class);
        BillingEvent existing = new BillingEvent();
        existing.setEventType("REVERSAL");
        existing.setOriginalEventId(9L);
        when(eventMapper.selectByRequestId("reverse-1")).thenReturn(existing);
        BillingReversalService service = new BillingReversalService(eventMapper,
                mock(BillingUsageBalanceMapper.class), mock(BillingPlanService.class),
                new BillingConfigCodec(new ObjectMapper()));

        assertSame(existing, service.reverse(9L, command("reverse-1")));
        verify(eventMapper, never()).insert(any(BillingEvent.class));
    }

    private BillingEvent original() {
        BillingEvent event = new BillingEvent();
        event.setId(9L);
        event.setRequestId("original");
        event.setEventType("USAGE");
        event.setPlanId(3L);
        event.setPlanCode("P");
        event.setPlanVersion(1);
        event.setTemplateCode("PER_ITEM");
        event.setAccountingPurpose("VENDOR_PAYABLE");
        event.setCallerId(22L);
        event.setVendorId(4L);
        event.setVendorCode("V");
        event.setInterfaceId(5L);
        event.setInterfaceCode("I");
        event.setBillable(true);
        event.setQuantity(new BigDecimal("5"));
        event.setUnit("ITEM");
        event.setBaseAmount(new BigDecimal("2.5"));
        event.setAdjustmentAmount(BigDecimal.ZERO);
        event.setFinalAmount(new BigDecimal("2.5"));
        event.setCurrency("CNY");
        event.setStatus("POSTED");
        event.setPricingSnapshot("{}");
        event.setBillingPeriod(LocalDate.of(2026, 7, 1));
        return event;
    }

    private BillingReversalCommand command(String requestId) {
        BillingReversalCommand command = new BillingReversalCommand();
        command.setRequestId(requestId);
        command.setReason("vendor refund");
        return command;
    }
}
