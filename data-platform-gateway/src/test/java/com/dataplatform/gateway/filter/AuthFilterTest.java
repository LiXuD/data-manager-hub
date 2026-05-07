package com.dataplatform.gateway.filter;

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
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthFilter(redisTemplate, objectMapper);
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
    void shouldReturn401WhenNoApiKey() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        verifyNoInteractions(chain);
    }

    @Test
    void shouldReturn401WhenApiKeyNotFoundInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:key:invalid-key")).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test")
                .header("X-Api-Key", "invalid-key")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        verifyNoInteractions(chain);
    }

    @Test
    void shouldReturn403WhenCallerIsDisabled() {
        Map<String, Object> keyInfo = Map.of("callerId", 1, "keyId", 10, "callerName", "disabled-caller", "status", 0);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:key:disabled-key")).thenReturn(Mono.just(keyInfo));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test")
                .header("X-Api-Key", "disabled-key")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
        verifyNoInteractions(chain);
    }

    @Test
    void shouldPassAndSetAttributesForValidKey() {
        Map<String, Object> keyInfo = Map.of("callerId", 1, "keyId", 10, "callerName", "test-caller", "status", 1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:key:valid-key")).thenReturn(Mono.just(keyInfo));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test")
                .header("X-Api-Key", "valid-key")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertEquals(Integer.valueOf(1), exchange.getAttribute("callerId"));
        assertEquals(Integer.valueOf(10), exchange.getAttribute("keyId"));
        assertEquals("test-caller", exchange.getAttribute("callerName"));
        verify(chain).filter(exchange);
    }

    @Test
    void shouldExtractKeyFromBearerHeader() {
        Map<String, Object> keyInfo = Map.of("callerId", 2, "keyId", 20, "callerName", "bearer-caller", "status", 1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:key:bearer-key-123")).thenReturn(Mono.just(keyInfo));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test")
                .header("Authorization", "Bearer bearer-key-123")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertEquals(Integer.valueOf(2), exchange.getAttribute("callerId"));
        verify(chain).filter(exchange);
    }
}
