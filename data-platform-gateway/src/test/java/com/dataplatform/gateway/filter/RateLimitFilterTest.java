package com.dataplatform.gateway.filter;

import com.dataplatform.common.ratelimit.SlidingWindowRateLimitAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.data.redis.core.script.RedisScript;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOps;

    private ObjectMapper objectMapper = new ObjectMapper();
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redisTemplate, objectMapper);
    }

    @Test
    void shouldSkipNonOpenapiPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldAuthenticateButNotRateLimitDocumentationPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/v1/docs/interfaces").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldUseDefaultConfigWhenNoneConfigured() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1")).thenReturn(Mono.empty());
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(Flux.just(50L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void shouldUseSharedSlidingWindowScript() {
        RedisScript<?> script = (RedisScript<?>) ReflectionTestUtils.getField(filter, "rateLimitScript");

        assertEquals(SlidingWindowRateLimitAlgorithm.ACQUIRE_SCRIPT, script.getScriptAsString());
    }

    @Test
    void shouldPassWhenUnderLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1")).thenReturn(Mono.just(Map.of("windowSec", 60, "maxReqs", 100)));
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(Flux.just(50L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void shouldSkipCounterWhenRateLimitIsDisabled() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1"))
                .thenReturn(Mono.just(Map.of("enabled", false, "windowSec", 60, "maxReqs", 100)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(redisTemplate, never()).execute(any(), anyList(), any(Object[].class));
    }

    @Test
    void shouldReturn429WhenOverLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1")).thenReturn(Mono.just(Map.of("windowSec", 60, "maxReqs", 100)));
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(Flux.just(101L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(429, exchange.getResponse().getStatusCode().value());
        assertNotNull(exchange.getResponse().getHeaders().getFirst("Retry-After"));
        verifyNoInteractions(chain);
    }

    @Test
    void shouldReturn503WhenRedisFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1")).thenReturn(Mono.error(new RuntimeException("Redis down")));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(503, exchange.getResponse().getStatusCode().value());
        verifyNoInteractions(chain);
    }

    @Test
    void shouldRejectOpenapiRequestWithoutAuthenticatedKeyId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        verifyNoInteractions(chain);
    }
}
