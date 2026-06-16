package com.dataplatform.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {

    private TraceIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
    }

    @Test
    void shouldGenerateTraceIdWhenHeaderMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();

        String traceId = exchange.getAttribute("traceId");
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
    }

    @Test
    void shouldPassThroughExistingTraceId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header("X-Trace-Id", "abc123def456")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();

        String traceId = exchange.getAttribute("traceId");
        assertEquals("abc123def456", traceId);
    }

    @Test
    void shouldPropagateTraceIdToDownstreamHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = e -> {
            String downstreamHeader = e.getRequest().getHeaders().getFirst("X-Trace-Id");
            assertNotNull(downstreamHeader);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }

    @Test
    void shouldGenerateUniqueTraceIdsForDifferentRequests() {
        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/v1/other").build();
        ServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        ServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange1, chain).block();
        filter.filter(exchange2, chain).block();

        String traceId1 = exchange1.getAttribute("traceId");
        String traceId2 = exchange2.getAttribute("traceId");
        assertNotEquals(traceId1, traceId2);
    }

    @Test
    void shouldBeOrderedMinus3() {
        assertEquals(-3, filter.getOrder());
    }
}
