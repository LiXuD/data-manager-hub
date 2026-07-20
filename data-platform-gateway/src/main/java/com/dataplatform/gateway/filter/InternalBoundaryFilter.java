package com.dataplatform.gateway.filter;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class InternalBoundaryFilter implements GlobalFilter, Ordered {

    private static final List<String> TRUSTED_HEADERS = List.of(
            "X-Internal-Service",
            "X-Actor-Id",
            "X-Actor-Tenant-Id",
            "X-Internal-Scope");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/internal/") || path.contains("/internal/")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        ServerWebExchange sanitized = exchange.mutate()
                .request(builder -> builder.headers(headers -> TRUSTED_HEADERS.forEach(headers::remove)))
                .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
