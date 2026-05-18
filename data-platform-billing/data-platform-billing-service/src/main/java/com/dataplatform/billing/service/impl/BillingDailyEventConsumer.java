package com.dataplatform.billing.service.impl;

import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.common.entity.CallRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BillingDailyEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BillingDailyEventConsumer.class);
    private static final String CALL_RECORD_TOPIC = "call-record";
    private static final String DEFAULT_DATA_TYPE = "unknown";

    private final BillingDailyMapper billingDailyMapper;
    private final ObjectMapper objectMapper;

    public BillingDailyEventConsumer(BillingDailyMapper billingDailyMapper, ObjectMapper objectMapper) {
        this.billingDailyMapper = billingDailyMapper;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = CALL_RECORD_TOPIC, groupId = "data-platform-billing-daily")
    public void consume(String payload) {
        CallRecord record;
        try {
            record = objectMapper.readValue(payload, CallRecord.class);
        } catch (Exception ex) {
            log.warn("解析计费日账单事件失败，跳过该事件", ex);
            return;
        }

        try {
            aggregate(record);
        } catch (RuntimeException ex) {
            log.warn("聚合计费日账单事件失败，等待Kafka重试: requestId={}", record.getRequestId(), ex);
            throw ex;
        }
    }

    void aggregate(CallRecord record) {
        if (record == null || !StringUtils.hasText(record.getRequestId())
                || record.getTenantId() == null || record.getCallerId() == null) {
            return;
        }

        LocalDate billingDate = resolveBillingDate(record);
        if (billingDate == null) {
            return;
        }

        boolean success = Boolean.TRUE.equals(record.getSuccess());
        billingDailyMapper.upsertDailyFromCallRecord(
            record.getRequestId(),
            record.getTenantId(),
            record.getCallerId(),
            record.getVendorId(),
            resolveDataType(record),
            billingDate,
            success ? 1L : 0L,
            success ? 0L : 1L,
            record.getCost() == null ? BigDecimal.ZERO : record.getCost(),
            record.getLatency()
        );
    }

    private LocalDate resolveBillingDate(CallRecord record) {
        LocalDateTime time = firstPresent(
            record.getCallTime(),
            record.getRequestTime(),
            record.getResponseAt(),
            record.getCreatedAt()
        );
        return time == null ? null : time.toLocalDate();
    }

    private LocalDateTime firstPresent(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String resolveDataType(CallRecord record) {
        if (record.getDataType() != null && !record.getDataType().isBlank()) {
            return record.getDataType();
        }
        if (record.getDataTypeCode() != null && !record.getDataTypeCode().isBlank()) {
            return record.getDataTypeCode();
        }
        return DEFAULT_DATA_TYPE;
    }
}
