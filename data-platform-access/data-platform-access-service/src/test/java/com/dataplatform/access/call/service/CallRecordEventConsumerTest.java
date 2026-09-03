package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataplatform.common.entity.CallRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CallRecordEventConsumerTest {

    @Test
    void skipsDuplicateRequestId() throws Exception {
        CallRecordService service = mock(CallRecordService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CallRecordEventConsumer consumer = new CallRecordEventConsumer(service, objectMapper, registry);

        CallRecord existing = new CallRecord();
        existing.setId(1L);
        when(service.getOne(any(Wrapper.class), eq(false))).thenReturn(existing);

        CallRecord record = record("req-1");
        consumer.consume(objectMapper.writeValueAsString(record));

        verify(service, never()).save(any(CallRecord.class));
        assertEquals(1.0, registry.counter("call_record_consumer_events", "outcome", "duplicate").count());
    }

    @Test
    void rethrowsPersistenceFailureSoKafkaCanRetry() throws Exception {
        CallRecordService service = mock(CallRecordService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CallRecordEventConsumer consumer = new CallRecordEventConsumer(service, objectMapper, registry);

        when(service.save(any(CallRecord.class))).thenThrow(new RuntimeException("db down"));

        CallRecord record = record("req-2");

        assertThrows(RuntimeException.class, () -> consumer.consume(objectMapper.writeValueAsString(record)));
        assertEquals(1.0, registry.counter("call_record_consumer_events", "outcome", "failed").count());
    }

    @Test
    void rethrowsMalformedPayloadSoKafkaDoesNotSilentlyLoseIt() {
        CallRecordService service = mock(CallRecordService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CallRecordEventConsumer consumer = new CallRecordEventConsumer(service, new ObjectMapper(), registry);

        assertThrows(IllegalArgumentException.class, () -> consumer.consume("not-json"));
        verify(service, never()).save(any(CallRecord.class));
        assertEquals(1.0, registry.counter("call_record_consumer_events", "outcome", "malformed").count());
    }

    private CallRecord record(String requestId) {
        CallRecord record = new CallRecord();
        record.setRequestId(requestId);
        record.setTenantId(1L);
        record.setCallerId(2L);
        record.setApiKeyId(3L);
        record.setVendorId(4L);
        record.setVendorCode("vendor");
        record.setDataType("company_info");
        record.setCallTime(LocalDateTime.of(2026, 5, 17, 10, 15));
        return record;
    }
}
