package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.common.entity.CallRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class CallRecordEventPublisherTest {

    @Test
    void rejectsNullRecordBeforeCallingKafka() {
        CallRecordEventPublisher publisher = new CallRecordEventPublisher(
                mock(KafkaTemplate.class), mock(CallRecordService.class), new ObjectMapper());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> publisher.publish(null));

        assertEquals("调用记录不能为空", exception.getMessage());
    }

    @Test
    void fallsBackToPersistenceWhenKafkaSendFails() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CallRecordService callRecordService = mock(CallRecordService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedSend);
        when(callRecordService.save(any(CallRecord.class))).thenReturn(true);

        CallRecord record = record("req-kafka-failure");
        new CallRecordEventPublisher(kafkaTemplate, callRecordService, objectMapper).publish(record);

        verify(callRecordService).save(record);
    }

    @Test
    void fallsBackToPersistenceWhenSerializationFails() throws JsonProcessingException {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CallRecordService callRecordService = mock(CallRecordService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any(CallRecord.class)))
                .thenThrow(new JsonProcessingException("cannot serialize") { });
        when(callRecordService.save(any(CallRecord.class))).thenReturn(true);

        CallRecord record = record("req-serialization-failure");
        new CallRecordEventPublisher(kafkaTemplate, callRecordService, objectMapper).publish(record);

        verify(callRecordService).save(record);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void exposesSynchronousFallbackPersistenceFailure() throws JsonProcessingException {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CallRecordService callRecordService = mock(CallRecordService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any(CallRecord.class)))
                .thenThrow(new JsonProcessingException("cannot serialize") { });
        when(callRecordService.save(any(CallRecord.class))).thenReturn(false);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CallRecordEventPublisher(kafkaTemplate, callRecordService, objectMapper)
                        .publish(record("req-persistence-failure")));

        assertEquals("调用记录回退落库失败，请重试", exception.getMessage());
    }

    private CallRecord record(String requestId) {
        CallRecord record = new CallRecord();
        record.setRequestId(requestId);
        return record;
    }
}
