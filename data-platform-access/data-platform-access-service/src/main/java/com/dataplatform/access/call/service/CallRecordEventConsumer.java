package com.dataplatform.access.call.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.access.call.config.KafkaConfig;
import com.dataplatform.common.entity.CallRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public CallRecordEventConsumer(CallRecordService callRecordService, ObjectMapper objectMapper) {
        this.callRecordService = callRecordService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaConfig.CALL_RECORD_TOPIC, groupId = "data-platform-access-call-record")
    public void consume(String payload) {
        CallRecord record;
        try {
            record = objectMapper.readValue(payload, CallRecord.class);
        } catch (Exception ex) {
            log.warn("解析调用记录事件失败，交由Kafka错误处理器重试或投递死信", ex);
            throw new IllegalArgumentException("Invalid call-record event", ex);
        }

        if (StringUtils.hasText(record.getRequestId()) && existsByRequestId(record.getRequestId())) {
            return;
        }

        try {
            callRecordService.save(record);
        } catch (RuntimeException ex) {
            log.warn("保存调用记录事件失败，等待Kafka重试: requestId={}", record.getRequestId(), ex);
            throw ex;
        }
    }

    private boolean existsByRequestId(String requestId) {
        CallRecord existing = callRecordService.getOne(new LambdaQueryWrapper<CallRecord>()
                .eq(CallRecord::getRequestId, requestId)
                .last("LIMIT 1"), false);
        return existing != null;
    }
}
