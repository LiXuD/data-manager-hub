package com.dataplatform.access.call.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.access.call.config.KafkaConfig;
import com.dataplatform.common.entity.CallRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 访问域数据调用的 Call Record Event Consumer。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class CallRecordEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CallRecordEventConsumer.class);

    private final CallRecordService callRecordService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public CallRecordEventConsumer(CallRecordService callRecordService, ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.callRecordService = callRecordService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = KafkaConfig.CALL_RECORD_TOPIC, groupId = "data-platform-access-call-record")
    public void consume(String payload) {
        long processingStartedAt = System.nanoTime();
        CallRecord record = null;
        String outcome = "failed";
        try {
            try {
                record = objectMapper.readValue(payload, CallRecord.class);
            } catch (Exception ex) {
                outcome = "malformed";
                log.warn("解析调用记录事件失败，交由Kafka错误处理器重试或投递死信", ex);
                throw new IllegalArgumentException("Invalid call-record event", ex);
            }

            if (StringUtils.hasText(record.getRequestId()) && existsByRequestId(record.getRequestId())) {
                outcome = "duplicate";
                return;
            }

            long persistenceStartedAt = System.nanoTime();
            String persistenceOutcome = "stored";
            try {
                if (!callRecordService.save(record)) {
                    throw new IllegalStateException("Call-record persistence returned false");
                }
            } catch (RuntimeException ex) {
                persistenceOutcome = "failed";
                log.warn("保存调用记录事件失败，等待Kafka重试: requestId={}", record.getRequestId(), ex);
                throw ex;
            } finally {
                meterRegistry.timer("call_record_persistence_latency", "outcome", persistenceOutcome)
                        .record(Duration.ofNanos(System.nanoTime() - persistenceStartedAt));
            }
            outcome = "stored";
        } finally {
            increment(outcome);
            recordEndToEndLatency(record, outcome);
            recordProcessingLatency(processingStartedAt, outcome);
        }
    }

    private boolean existsByRequestId(String requestId) {
        CallRecord existing = callRecordService.getOne(new LambdaQueryWrapper<CallRecord>()
                .eq(CallRecord::getRequestId, requestId)
                .last("LIMIT 1"), false);
        return existing != null;
    }

    private void increment(String outcome) {
        meterRegistry.counter("call_record_consumer_events", "outcome", outcome).increment();
    }

    private void recordProcessingLatency(long startedAt, String outcome) {
        meterRegistry.timer("call_record_consumer_processing_latency", "outcome", outcome)
                .record(Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private void recordEndToEndLatency(CallRecord record, String outcome) {
        if (record == null) {
            return;
        }
        LocalDateTime callTime = record.getCallTime();
        if (callTime == null) {
            return;
        }
        Duration latency = Duration.between(callTime, LocalDateTime.now());
        if (!latency.isNegative()) {
            meterRegistry.timer("call_record_end_to_end_persistence_latency", "outcome", outcome)
                    .record(latency);
        }
    }
}
