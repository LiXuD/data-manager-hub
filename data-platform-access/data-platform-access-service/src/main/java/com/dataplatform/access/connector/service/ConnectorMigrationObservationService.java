package com.dataplatform.access.connector.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dataplatform.access.call.mapper.CallRecordMapper;
import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationReqDTO;
import com.dataplatform.common.entity.CallRecord;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Access-owned aggregate view of connector call records. No request or response payload is returned. */
@Service
public class ConnectorMigrationObservationService {
    private final CallRecordMapper callRecordMapper;

    public ConnectorMigrationObservationService(CallRecordMapper callRecordMapper) {
        this.callRecordMapper = callRecordMapper;
    }

    public ConnectorMigrationObservationDTO observe(ConnectorMigrationObservationReqDTO request) {
        validate(request);
        QueryWrapper<CallRecord> query = new QueryWrapper<>();
        query.select(
                "COUNT(*) AS total_calls",
                "SUM(CASE WHEN success THEN 1 ELSE 0 END) AS successful_calls",
                "SUM(CASE WHEN NOT success THEN 1 ELSE 0 END) AS failed_calls",
                "COALESCE(CAST(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS bigint), 0) AS p95_duration_ms",
                "SUM(CASE WHEN cache_hit THEN 1 ELSE 0 END) AS cache_hit_calls",
                "SUM(CASE WHEN NOT cache_hit THEN 1 ELSE 0 END) AS realtime_calls")
                .eq("vendor_id", request.vendorId())
                .eq("pipeline_version", request.pipelineVersion())
                .eq("snapshot_hash", request.snapshotHash())
                .ge("call_time", request.startedAt())
                .le(request.endedAt() != null, "call_time", request.endedAt());
        List<Map<String, Object>> rows = callRecordMapper.selectMaps(query);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        long total = value(row, "total_calls");
        long success = value(row, "successful_calls");
        long failed = value(row, "failed_calls");
        return new ConnectorMigrationObservationDTO(total, success, failed,
                total == 0 ? 0D : (double) failed / total,
                value(row, "p95_duration_ms"), value(row, "cache_hit_calls"),
                value(row, "realtime_calls"));
    }

    private void validate(ConnectorMigrationObservationReqDTO request) {
        if (request == null || request.vendorId() == null || request.pipelineVersion() == null
                || !StringUtils.hasText(request.snapshotHash()) || request.startedAt() == null) {
            throw new IllegalArgumentException("vendorId, pipelineVersion, snapshotHash and startedAt are required");
        }
        if (request.snapshotHash().length() != 64) {
            throw new IllegalArgumentException("snapshotHash must be a SHA-256 value");
        }
        if (request.endedAt() != null && request.endedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException("endedAt cannot be before startedAt");
        }
    }

    private long value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return 0L;
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}
