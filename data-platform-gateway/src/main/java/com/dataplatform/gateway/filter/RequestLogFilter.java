package com.dataplatform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关层过滤器的 Request Log Filter。
 * <p>请求过滤器，处理网关或 Web 链路中的横切逻辑。</p>
 */
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);
    private static final String OPENAPI_PREFIX = "/openapi/";
    private static final String START_TIME_ATTR = "requestLog.startTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(OPENAPI_PREFIX)) {
            return chain.filter(exchange);
        }

        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Long startTime = exchange.getAttribute(START_TIME_ATTR);
            long elapsed = startTime != null ? System.currentTimeMillis() - startTime : -1;

            String method = exchange.getRequest().getMethod() != null
                    ? exchange.getRequest().getMethod().name() : "UNKNOWN";
            String requestPath = exchange.getRequest().getURI().getPath();
            Object callerName = exchange.getAttribute("callerName");
            Object keyId = exchange.getAttribute("keyId");
            Object traceId = exchange.getAttribute("traceId");
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;

            log.info("[OPENAPI] {} {} | caller={} | keyId={} | traceId={} | {} | {}ms",
                    method, requestPath,
                    callerName != null ? callerName : "-",
                    keyId != null ? keyId : "-",
                    traceId != null ? traceId : "-",
                    statusCode,
                    elapsed);
        }));
    }

    @Override
    public int getOrder() {
        return -4;
    }
}
