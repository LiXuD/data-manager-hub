package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.access.call.api.dto.CallStatsDTO;
import com.dataplatform.access.call.api.dto.DailyCallStatsDTO;
import com.dataplatform.access.call.api.dto.VendorCallSummaryDTO;
import com.dataplatform.access.call.mapper.CallStatsMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CallStatsQueryServiceTest {

    private final CallStatsMapper mapper = mock(CallStatsMapper.class);
    private final CallStatsQueryService service = new CallStatsQueryService(mapper);

    @Test
    void mapsInterfaceAndDailyStatistics() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 2, 0, 0);
        when(mapper.selectInterfaceStats("api-1", start, end, 500)).thenReturn(Map.of(
                "total_calls", 10L,
                "success_calls", 8L,
                "avg_latency", new BigDecimal("123.5"),
                "slow_calls", 2L));
        when(mapper.selectDailyInterfaceStats("api-1", start, end)).thenReturn(List.of(Map.of(
                "date", Date.valueOf("2026-07-01"),
                "total_calls", 10L,
                "success_calls", 8L,
                "avg_latency", 123.5D)));

        CallStatsDTO summary = service.getInterfaceStats("api-1", start, end, 500);
        List<DailyCallStatsDTO> daily = service.getDailyInterfaceStats("api-1", start, end);

        assertEquals(10L, summary.getTotalCalls());
        assertEquals(8L, summary.getSuccessCalls());
        assertEquals(123.5D, summary.getAvgLatency());
        assertEquals(2L, summary.getSlowCalls());
        assertEquals(LocalDate.of(2026, 7, 1), daily.getFirst().getDate());
    }

    @Test
    void mapsVendorSummaryAndNullAggregates() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        when(mapper.selectVendorDailySummary(9L, date)).thenReturn(Map.of());

        VendorCallSummaryDTO summary = service.getVendorDailySummary(9L, date);

        assertEquals(0L, summary.getCallCount());
        assertEquals(BigDecimal.ZERO, summary.getTotalAmount());
    }
}
