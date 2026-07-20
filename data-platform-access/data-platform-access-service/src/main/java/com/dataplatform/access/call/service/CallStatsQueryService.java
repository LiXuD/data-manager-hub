package com.dataplatform.access.call.service;

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
import org.springframework.stereotype.Service;

@Service
public class CallStatsQueryService {

    private final CallStatsMapper mapper;

    public CallStatsQueryService(CallStatsMapper mapper) {
        this.mapper = mapper;
    }

    public CallStatsDTO getInterfaceStats(String apiCode, LocalDateTime startTime,
                                          LocalDateTime endTime, Integer slaThreshold) {
        Map<String, Object> row = mapper.selectInterfaceStats(apiCode, startTime, endTime, slaThreshold);
        CallStatsDTO dto = new CallStatsDTO();
        dto.setTotalCalls(longValue(row.get("total_calls")));
        dto.setSuccessCalls(longValue(row.get("success_calls")));
        dto.setAvgLatency(doubleValue(row.get("avg_latency")));
        dto.setSlowCalls(longValue(row.get("slow_calls")));
        return dto;
    }

    public List<DailyCallStatsDTO> getDailyInterfaceStats(String apiCode, LocalDateTime startTime,
                                                           LocalDateTime endTime) {
        return mapper.selectDailyInterfaceStats(apiCode, startTime, endTime).stream()
                .map(this::toDailyStats)
                .toList();
    }

    public VendorCallSummaryDTO getVendorDailySummary(Long vendorId, LocalDate billingDate) {
        Map<String, Object> row = mapper.selectVendorDailySummary(vendorId, billingDate);
        VendorCallSummaryDTO dto = new VendorCallSummaryDTO();
        dto.setCallCount(longValue(row.get("call_count")));
        dto.setTotalAmount(decimalValue(row.get("total_amount")));
        return dto;
    }

    private DailyCallStatsDTO toDailyStats(Map<String, Object> row) {
        DailyCallStatsDTO dto = new DailyCallStatsDTO();
        Object date = row.get("date");
        dto.setDate(date instanceof Date sqlDate ? sqlDate.toLocalDate() : LocalDate.parse(date.toString()));
        dto.setTotalCalls(longValue(row.get("total_calls")));
        dto.setSuccessCalls(longValue(row.get("success_calls")));
        dto.setAvgLatency(doubleValue(row.get("avg_latency")));
        return dto;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;
    }
}
