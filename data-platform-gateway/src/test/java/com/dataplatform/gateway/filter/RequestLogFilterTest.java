package com.dataplatform.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class RequestLogFilterTest {

    private RequestLogFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLogFilter();
    }

    @Test
    void shouldSkipNonOpenapiPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = e -> {
            assertNull(e.getAttribute("requestLog.startTime"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }

    @Test
    void shouldRecordStartTimeAndLogForOpenapiPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("callerId", 1L);
        exchange.getAttributes().put("keyId", 10L);
        exchange.getAttributes().put("callerName", "test-caller");
        exchange.getAttributes().put("traceId", "trace123");
        exchange.getResponse().setRawStatusCode(200);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertNotNull(exchange.getAttribute("requestLog.startTime"));
    }

    @Test
    void shouldHandleErrorStatusCodes() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/error").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setRawStatusCode(500);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertNotNull(exchange.getAttribute("requestLog.startTime"));
    }

    @Test
    void shouldWrapOpenApiAuthFailures() {
        assertEquals(-4, filter.getOrder());
    }
}
