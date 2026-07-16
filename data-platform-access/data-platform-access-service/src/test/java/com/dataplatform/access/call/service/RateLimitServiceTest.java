package com.dataplatform.access.call.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RateLimitService();
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
    }

    @Test
    void shouldRejectRequestWhenRedisIsUnavailable() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis unavailable"));

        assertFalse(service.checkRateLimit("api-key", 10));
    }

    @Test
    void shouldRejectRequestWhenRedisReturnsNoCounter() {
        when(valueOperations.increment(anyString())).thenReturn(null);

        assertFalse(service.checkRateLimit("api-key", 10));
    }

    @Test
    void shouldReportZeroRemainingQuotaWhenRedisIsUnavailable() {
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis unavailable"));

        assertEquals(0, service.getRemainingQuota("api-key"));
    }
}
