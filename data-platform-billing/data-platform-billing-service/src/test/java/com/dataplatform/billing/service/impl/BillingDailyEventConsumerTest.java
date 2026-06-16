package com.dataplatform.billing.service.impl;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.common.entity.CallRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BillingDailyEventConsumerTest {

    @Test
    void aggregatesCallRecordEventIntoDailyBilling() throws Exception {
        BillingDailyMapper mapper = mock(BillingDailyMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        BillingDailyEventConsumer consumer = new BillingDailyEventConsumer(mapper, objectMapper);

        CallRecord record = new CallRecord();
        record.setRequestId("req-1");
        record.setTenantId(1L);
        record.setCallerId(2L);
        record.setVendorId(3L);
        record.setDataType("company_info");
        record.setCallTime(LocalDateTime.of(2026, 5, 17, 10, 15));
        record.setSuccess(true);
        record.setCost(new BigDecimal("0.30"));
        record.setLatency(120);

        consumer.consume(objectMapper.writeValueAsString(record));

        verify(mapper).upsertDailyFromCallRecord(
            eq("req-1"),
            eq(1L),
            eq(2L),
            eq(3L),
            eq("company_info"),
            eq(LocalDate.of(2026, 5, 17)),
            eq(1L),
            eq(0L),
            eq(new BigDecimal("0.30")),
            eq(120)
        );
    }

    @Test
    void skipsEventsWithoutRequestIdBecauseTheyCannotBeDeduped() throws Exception {
        BillingDailyMapper mapper = mock(BillingDailyMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        BillingDailyEventConsumer consumer = new BillingDailyEventConsumer(mapper, objectMapper);

        CallRecord record = new CallRecord();
        record.setTenantId(1L);
        record.setCallerId(2L);
        record.setVendorId(3L);
        record.setDataType("company_info");
        record.setCallTime(LocalDateTime.of(2026, 5, 17, 10, 15));

        consumer.consume(objectMapper.writeValueAsString(record));

        verifyNoInteractions(mapper);
    }

    @Test
    void rethrowsPersistenceFailureSoKafkaCanRetry() throws Exception {
        BillingDailyMapper mapper = mock(BillingDailyMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        BillingDailyEventConsumer consumer = new BillingDailyEventConsumer(mapper, objectMapper);

        CallRecord record = new CallRecord();
        record.setRequestId("req-2");
        record.setTenantId(1L);
        record.setCallerId(2L);
        record.setVendorId(3L);
        record.setDataType("company_info");
        record.setCallTime(LocalDateTime.of(2026, 5, 17, 10, 15));
        when(mapper.upsertDailyFromCallRecord(
            eq("req-2"),
            eq(1L),
            eq(2L),
            eq(3L),
            eq("company_info"),
            eq(LocalDate.of(2026, 5, 17)),
            eq(0L),
            eq(1L),
            eq(BigDecimal.ZERO),
            eq(null)
        )).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> consumer.consume(objectMapper.writeValueAsString(record)));
    }
}
