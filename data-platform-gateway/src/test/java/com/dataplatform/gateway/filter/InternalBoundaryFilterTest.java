package com.dataplatform.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class InternalBoundaryFilterTest {

    private final InternalBoundaryFilter filter = new InternalBoundaryFilter();

    @Test
    void blocksInternalRoutesAtGateway() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/masterdata/interfaces/1").build());
        AtomicBoolean invoked = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            invoked.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertTrue(!invoked.get());
    }

    @Test
    void removesCallerSuppliedTrustedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/openapi/v1/query")
                .header("X-Actor-Id", "forged")
                .header("X-Internal-Service", "forged-service")
                .build());
        GatewayFilterChain chain = downstream -> {
            assertNull(downstream.getRequest().getHeaders().getFirst("X-Actor-Id"));
            assertNull(downstream.getRequest().getHeaders().getFirst("X-Internal-Service"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }
}
