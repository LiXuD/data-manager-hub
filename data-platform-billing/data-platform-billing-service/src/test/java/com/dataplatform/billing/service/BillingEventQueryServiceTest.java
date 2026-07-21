package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.mapper.BillingEventMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BillingEventQueryServiceTest {

    @Test
    void statsUseImmutableEventsToCalculateNetAmountAndQuantity() {
        BillingEventMapper eventMapper = mock(BillingEventMapper.class);
        when(eventMapper.selectList(any())).thenReturn(List.of(
                event("USAGE", "POSTED", "8.50", "5"),
                event("REVERSAL", "POSTED", "-8.50", "-5"),
                event("PERIODIC", "PENDING_REVIEW", "20.00", "0")));
        BillingEventQueryService service = new BillingEventQueryService(eventMapper);

        Map<String, Object> stats = service.stats(
                null, null, null, "VENDOR_PAYABLE", null, null);

        assertEquals(3, stats.get("eventCount"));
        assertEquals(0, new BigDecimal("20.00").compareTo((BigDecimal) stats.get("totalAmount")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) stats.get("totalQuantity")));
        assertEquals(1L, stats.get("pendingReviewCount"));
    }

    private BillingEvent event(String type, String status, String amount, String quantity) {
        BillingEvent event = new BillingEvent();
        event.setEventType(type);
        event.setStatus(status);
        event.setFinalAmount(new BigDecimal(amount));
        event.setQuantity(new BigDecimal(quantity));
        return event;
    }
}
