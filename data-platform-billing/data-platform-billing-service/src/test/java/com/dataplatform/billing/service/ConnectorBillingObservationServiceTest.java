package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationReqDTO;
import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.mapper.BillingEventMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorBillingObservationServiceTest {
    private final BillingEventMapper mapper = mock(BillingEventMapper.class);
    private final ConnectorBillingObservationService service = new ConnectorBillingObservationService(mapper);

    @Test
    void aggregatesBillingFactsForAnExactPipelineSnapshot() {
        when(mapper.selectMaps(any())).thenReturn(List.of(Map.of(
                "total_events", 12L,
                "posted_events", 10L,
                "pending_review_events", 1L,
                "reversed_events", 1L,
                "billable_events", 9L,
                "final_amount", new BigDecimal("12.30"))));

        LocalDateTime startedAt = LocalDateTime.now().minusHours(1);
        LocalDateTime endedAt = LocalDateTime.now();
        var result = service.observe(new ConnectorBillingObservationReqDTO(
                7L, 8L, 3, "b".repeat(64), startedAt, endedAt));

        assertEquals(12, result.totalEvents());
        assertEquals(10, result.postedEvents());
        assertEquals(new BigDecimal("12.30"), result.finalAmount());
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<QueryWrapper<BillingEvent>> query = (ArgumentCaptor) ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectMaps(query.capture());
        String sql = query.getValue().getCustomSqlSegment();
        assertEquals(true, sql.contains("vendor_id") && sql.contains("interface_id")
                && sql.contains("pipeline_version") && sql.contains("snapshot_hash")
                && sql.contains("call_time"));
        assertEquals(true, query.getValue().getParamNameValuePairs().values().containsAll(
                List.of(7L, 8L, 3, "b".repeat(64), startedAt, endedAt)));
    }

    @Test
    void refusesObservationWithoutAnExactSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> service.observe(
                new ConnectorBillingObservationReqDTO(7L, 8L, 3, "bad", LocalDateTime.now(), null)));
        assertThrows(IllegalArgumentException.class, () -> service.observe(
                new ConnectorBillingObservationReqDTO(7L, 8L, null, "b".repeat(64), LocalDateTime.now(), null)));
    }
}
