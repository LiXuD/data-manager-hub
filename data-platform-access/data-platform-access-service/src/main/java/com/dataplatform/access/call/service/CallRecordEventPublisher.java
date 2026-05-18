package com.dataplatform.access.call.service;

import com.dataplatform.access.call.config.KafkaConfig;
import com.dataplatform.common.entity.CallRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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
        try {
            String payload = objectMapper.writeValueAsString(record);
            kafkaTemplate.send(KafkaConfig.CALL_RECORD_TOPIC, record.getRequestId(), payload)
                    .exceptionally(ex -> {
                        log.warn("发送调用记录事件失败，回退同步落库: requestId={}", record.getRequestId(), ex);
                        callRecordService.save(record);
                        return null;
                    });
        } catch (Exception ex) {
            log.warn("序列化调用记录事件失败，回退同步落库: requestId={}", record.getRequestId(), ex);
            callRecordService.save(record);
        }
    }
}
