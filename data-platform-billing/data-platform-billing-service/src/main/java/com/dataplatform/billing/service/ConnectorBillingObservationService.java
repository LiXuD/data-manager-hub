package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationDTO;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationReqDTO;
import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.mapper.BillingEventMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Billing-owned aggregate used only by the connector migration observation gate. */
@Service
public class ConnectorBillingObservationService {
    private final BillingEventMapper billingEventMapper;

    public ConnectorBillingObservationService(BillingEventMapper billingEventMapper) {
        this.billingEventMapper = billingEventMapper;
    }

    public ConnectorBillingObservationDTO observe(ConnectorBillingObservationReqDTO request) {
        validate(request);
        QueryWrapper<BillingEvent> query = new QueryWrapper<>();
        query.select(
                "COUNT(*) AS total_events",
                "SUM(CASE WHEN status = 'POSTED' THEN 1 ELSE 0 END) AS posted_events",
                "SUM(CASE WHEN status = 'PENDING_REVIEW' THEN 1 ELSE 0 END) AS pending_review_events",
                "SUM(CASE WHEN status = 'REVERSED' THEN 1 ELSE 0 END) AS reversed_events",
                "SUM(CASE WHEN billable THEN 1 ELSE 0 END) AS billable_events",
                "COALESCE(SUM(final_amount), 0) AS final_amount")
                .eq("vendor_id", request.vendorId())
                .eq("interface_id", request.interfaceId())
                .eq("pipeline_version", request.pipelineVersion())
                .eq("snapshot_hash", request.snapshotHash())
                .ge("call_time", request.startedAt())
                .le(request.endedAt() != null, "call_time", request.endedAt());
        List<Map<String, Object>> rows = billingEventMapper.selectMaps(query);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        long total = value(row, "total_events");
        long posted = value(row, "posted_events");
        long pendingReview = value(row, "pending_review_events");
        long reversed = value(row, "reversed_events");
        long billable = value(row, "billable_events");
        BigDecimal finalAmount = decimal(row, "final_amount");
        validateAggregates(total, posted, pendingReview, reversed, billable, finalAmount);
        return new ConnectorBillingObservationDTO(total, posted, pendingReview, reversed, billable, finalAmount);
    }

    private void validate(ConnectorBillingObservationReqDTO request) {
        if (request == null || request.vendorId() == null || request.interfaceId() == null
                || request.pipelineVersion() == null || request.snapshotHash() == null
                || !request.snapshotHash().matches("(?i)[0-9a-f]{64}") || request.startedAt() == null) {
            throw new IllegalArgumentException(
                    "vendorId, interfaceId, pipelineVersion, snapshotHash and startedAt are required");
        }
        if (request.endedAt() != null && request.endedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException("endedAt cannot be before startedAt");
        }
    }

    private long value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return 0L;
        try {
            return new BigDecimal(value.toString().trim()).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalStateException("Invalid billing observation aggregate: " + key, exception);
        }
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid billing observation amount", exception);
        }
    }

    private void validateAggregates(long total, long posted, long pendingReview, long reversed,
                                    long billable, BigDecimal finalAmount) {
        if (total < 0 || posted < 0 || pendingReview < 0 || reversed < 0 || billable < 0
                || posted > total || billable > total || finalAmount == null || finalAmount.signum() < 0) {
            throw new IllegalStateException("Billing observation aggregates are invalid");
        }
        try {
            if (Math.addExact(Math.addExact(posted, pendingReview), reversed) != total) {
                throw new IllegalStateException("Billing observation status counts are inconsistent");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Billing observation counts overflow", exception);
        }
    }
}
