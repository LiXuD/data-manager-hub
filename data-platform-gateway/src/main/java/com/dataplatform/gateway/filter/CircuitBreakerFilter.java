package com.dataplatform.gateway.filter;

import com.dataplatform.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RefreshScope
public class CircuitBreakerFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerFilter.class);

    @Value("${circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${circuit-breaker.reset-timeout-ms:30000}")
    private long resetTimeoutMs;

    private final ObjectMapper objectMapper;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public CircuitBreakerFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }
        String serviceId = route.getId();

        CircuitState state = circuits.computeIfAbsent(serviceId, k -> new CircuitState());

        if (state.isOpen()) {
            if (state.shouldAttemptReset()) {
                log.info("Circuit half-open for {}, allowing probe request", serviceId);
                state.setHalfOpen(true);
            } else {
                log.debug("Circuit open for {}, rejecting request", serviceId);
                return writeCircuitOpenError(exchange, serviceId);
            }
        }

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    HttpStatus status = exchange.getResponse().getStatusCode() instanceof HttpStatus hs
                            ? hs : null;
                    if (status != null && status.is5xxServerError()) {
                        state.recordFailure();
                    } else if (state.isHalfOpen()) {
                        state.reset();
                        log.info("Circuit closed for {} after successful probe", serviceId);
                    } else {
                        state.resetFailures();
                    }
                })
                .doOnError(e -> {
                    state.recordFailure();
                    log.warn("Circuit recorded failure for {}: {}", serviceId, e.getMessage());
                });
    }

    private Mono<Void> writeCircuitOpenError(ServerWebExchange exchange, String serviceId) {
        Result<Void> result = Result.error(503, "服务暂时不可用，请稍后再试");
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().setContentLength(bytes.length);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }

    class CircuitState {

        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile boolean open = false;
        private volatile boolean halfOpen = false;
        private volatile long openTimestamp = 0;

        void recordFailure() {
            int count = failureCount.incrementAndGet();
            if (count >= failureThreshold) {
                open = true;
                openTimestamp = System.currentTimeMillis();
                log.warn("Circuit opened after {} failures", count);
            }
        }

        boolean isOpen() {
            return open;
        }

        boolean isHalfOpen() {
            return halfOpen;
        }

        void setHalfOpen(boolean halfOpen) {
            this.halfOpen = halfOpen;
        }

        boolean shouldAttemptReset() {
            return System.currentTimeMillis() - openTimestamp >= resetTimeoutMs;
        }

        void resetFailures() {
            failureCount.set(0);
        }

        void reset() {
            failureCount.set(0);
            open = false;
            halfOpen = false;
        }
    }
}
