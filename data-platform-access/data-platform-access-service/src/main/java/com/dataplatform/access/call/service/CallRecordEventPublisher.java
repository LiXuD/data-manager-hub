package com.dataplatform.access.call.service;

import com.dataplatform.access.call.config.KafkaConfig;
import com.dataplatform.common.entity.CallRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 访问域数据调用的 Call Record Event Publisher。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class CallRecordEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CallRecordEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CallRecordService callRecordService;
    private final ObjectMapper objectMapper;

    public CallRecordEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                    CallRecordService callRecordService,
                                    ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.callRecordService = callRecordService;
        this.objectMapper = objectMapper;
    }

    public void publish(CallRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("调用记录不能为空");
        }
        try {
            String payload = objectMapper.writeValueAsString(record);
            kafkaTemplate.send(KafkaConfig.CALL_RECORD_TOPIC, record.getRequestId(), payload)
                    .exceptionally(ex -> {
                        log.warn("发送调用记录事件失败，回退同步落库: requestId={}", record.getRequestId(), ex);
                        saveFallback(record, "kafka-send", ex);
                        return null;
                    });
        } catch (Exception ex) {
            log.warn("序列化调用记录事件失败，回退同步落库: requestId={}", record.getRequestId(), ex);
            saveFallback(record, "serialization", ex);
        }
    }

    private void saveFallback(CallRecord record, String reason, Throwable cause) {
        if (!callRecordService.save(record)) {
            log.error("调用记录回退落库失败: requestId={}, reason={}", record.getRequestId(), reason, cause);
            throw new IllegalStateException("调用记录回退落库失败，请重试");
        }
    }
}
