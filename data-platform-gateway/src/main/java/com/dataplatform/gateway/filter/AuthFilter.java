package com.dataplatform.gateway.filter;

import com.dataplatform.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关层过滤器的 Auth Filter。
 * <p>请求过滤器，处理网关或 Web 链路中的横切逻辑。</p>
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private static final String OPENAPI_PREFIX = "/openapi/";
    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REDIS_KEY_PREFIX = "openapi:key:";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public AuthFilter(ReactiveRedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(OPENAPI_PREFIX)) {
            return chain.filter(exchange);
        }

        String apiKey = extractApiKey(exchange);
        if (apiKey == null || apiKey.isBlank()) {
            return writeError(exchange, 401, "API Key 无效或已过期");
        }

        return redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + apiKey)
                .switchIfEmpty(Mono.defer(() -> writeError(exchange, 401, "API Key 无效或已过期")))
                .flatMap(value -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> keyInfo = objectMapper.convertValue(value, Map.class);
                    Integer status = (Integer) keyInfo.get("status");
                    if (status == null || status != 1) {
                        return writeError(exchange, 403, "调用方已禁用");
                    }
                    exchange.getAttributes().put("callerId", keyInfo.get("callerId"));
                    exchange.getAttributes().put("keyId", keyInfo.get("keyId"));
                    exchange.getAttributes().put("callerName", keyInfo.get("callerName"));
                    return chain.filter(exchange);
                });
    }

    private String extractApiKey(ServerWebExchange exchange) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        String auth = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, int code, String message) {
        Result<Void> result = Result.error(code, message);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            exchange.getResponse().setStatusCode(HttpStatus.valueOf(code));
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
        return -2;
    }
}
