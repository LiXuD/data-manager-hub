package com.dataplatform.access.call.service;

import com.dataplatform.common.ratelimit.SlidingWindowRateLimitAlgorithm;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        service = new RateLimitService(redisTemplate);
    }

    @Test
    void shouldAllowRequestWithinSlidingWindowLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(10L);

        assertTrue(service.checkRateLimit("api-key", 10));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of("rate_limit:window:api-key")),
                any(Object[].class));
    }

    @Test
    void shouldRejectRequestOverSlidingWindowLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(11L);

        assertFalse(service.checkRateLimit("api-key", 10));
    }

    @Test
    void shouldRejectRequestWhenRedisIsUnavailable() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertFalse(service.checkRateLimit("api-key", 10));
    }

    @Test
    void shouldRejectRequestWhenRedisReturnsNoCounter() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null);

        assertFalse(service.checkRateLimit("api-key", 10));
    }

    @Test
    void shouldCalculateRemainingQuotaFromSlidingWindow() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(25L);

        assertEquals(975, service.getRemainingQuota("api-key"));
    }

    @Test
    void shouldReportZeroRemainingQuotaWhenRedisIsUnavailable() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertEquals(0, service.getRemainingQuota("api-key"));
    }

    @Test
    void shouldUseSharedSlidingWindowScripts() {
        RedisScript<?> acquireScript = (RedisScript<?>) ReflectionTestUtils.getField(service, "acquireScript");
        RedisScript<?> countScript = (RedisScript<?>) ReflectionTestUtils.getField(service, "countScript");

        assertEquals(SlidingWindowRateLimitAlgorithm.ACQUIRE_SCRIPT, acquireScript.getScriptAsString());
        assertEquals(SlidingWindowRateLimitAlgorithm.COUNT_SCRIPT, countScript.getScriptAsString());
    }
}
