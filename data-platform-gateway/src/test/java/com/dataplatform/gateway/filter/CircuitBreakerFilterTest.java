package com.dataplatform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CircuitBreakerFilterTest {

    private ObjectMapper objectMapper = new ObjectMapper();
    private CircuitBreakerFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CircuitBreakerFilter(objectMapper);
        // Use reflection to set config values since @Value won't work in unit tests
        try {
            var thresholdField = CircuitBreakerFilter.class.getDeclaredField("failureThreshold");
            thresholdField.setAccessible(true);
            thresholdField.set(filter, 3);

            var timeoutField = CircuitBreakerFilter.class.getDeclaredField("resetTimeoutMs");
            timeoutField.setAccessible(true);
            timeoutField.set(filter, 1000L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MockServerWebExchange createExchange(String serviceId) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.builder(request).build();
        Route route = Route.async()
                .id(serviceId)
                .uri(URI.create("http://localhost:8081"))
                .predicate(e -> true)
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    @Test
    void shouldPassThroughWhenCircuitClosed() {
        MockServerWebExchange exchange = createExchange("test-service");
        GatewayFilterChain chain = e -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void shouldRejectWhenCircuitOpen() throws Exception {
        GatewayFilterChain chain = e -> {
            e.getResponse().setRawStatusCode(500);
            return Mono.empty();
        };

        // Trigger enough failures to open the circuit
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = createExchange("failing-service");
            filter.filter(exchange, chain).block();
        }

        // Next request should be rejected
        MockServerWebExchange blockedExchange = createExchange("failing-service");
        StepVerifier.create(filter.filter(blockedExchange, e -> Mono.empty()))
                .verifyComplete();

        assertNotNull(blockedExchange.getResponse().getStatusCode());
        assertEquals(503, blockedExchange.getResponse().getStatusCode().value());
    }

    @Test
    void shouldAllowProbeAfterResetTimeout() throws Exception {
        GatewayFilterChain chain = e -> {
            e.getResponse().setRawStatusCode(500);
            return Mono.empty();
        };

        // Open the circuit
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = createExchange("probe-service");
            filter.filter(exchange, chain).block();
        }

        // Wait for reset timeout
        Thread.sleep(1100);

        // Probe request should be allowed through
        MockServerWebExchange probeExchange = createExchange("probe-service");
        GatewayFilterChain successChain = e -> Mono.empty();
        StepVerifier.create(filter.filter(probeExchange, successChain))
                .verifyComplete();
    }

    @Test
    void shouldReturnOrderZero() {
        assertEquals(0, filter.getOrder());
    }
}
