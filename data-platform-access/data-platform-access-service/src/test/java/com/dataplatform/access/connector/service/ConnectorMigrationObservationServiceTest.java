package com.dataplatform.access.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dataplatform.access.call.mapper.CallRecordMapper;
import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationReqDTO;
import com.dataplatform.common.entity.CallRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorMigrationObservationServiceTest {
    private final CallRecordMapper mapper = mock(CallRecordMapper.class);
    private final ConnectorMigrationObservationService service = new ConnectorMigrationObservationService(mapper);

    @Test
    void aggregatesOnlyTheRequestedConnectorTrace() {
        when(mapper.selectMaps(any())).thenReturn(List.of(Map.of(
                "total_calls", 20L,
                "successful_calls", 18L,
                "failed_calls", 2L,
                "p95_duration_ms", 450L,
                "cache_hit_calls", 5L,
                "realtime_calls", 15L)));

        LocalDateTime startedAt = LocalDateTime.now().minusHours(1);
        LocalDateTime endedAt = LocalDateTime.now();
        var result = service.observe(new ConnectorMigrationObservationReqDTO(
                7L, 3, "a".repeat(64), startedAt, endedAt));

        assertEquals(20, result.totalCalls());
        assertEquals(0.1D, result.errorRate());
        assertEquals(450, result.p95DurationMs());
        assertEquals(5, result.cacheHitCalls());
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<QueryWrapper<CallRecord>> query = (ArgumentCaptor) ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectMaps(query.capture());
        String sql = query.getValue().getCustomSqlSegment();
        assertEquals(true, sql.contains("vendor_id") && sql.contains("pipeline_version")
                && sql.contains("snapshot_hash") && sql.contains("call_time"));
        assertEquals(true, query.getValue().getParamNameValuePairs().values().containsAll(
                List.of(7L, 3, "a".repeat(64), startedAt, endedAt)));
    }

    @Test
    void rejectsMissingOrMalformedTraceIdentity() {
        assertThrows(IllegalArgumentException.class, () -> service.observe(
                new ConnectorMigrationObservationReqDTO(7L, 3, "short", LocalDateTime.now(), null)));
        assertThrows(IllegalArgumentException.class, () -> service.observe(
                new ConnectorMigrationObservationReqDTO(7L, null, "a".repeat(64), LocalDateTime.now(), null)));
    }
}
