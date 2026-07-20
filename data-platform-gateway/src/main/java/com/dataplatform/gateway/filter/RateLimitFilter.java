package com.dataplatform.gateway.filter;

import com.dataplatform.common.result.Result;
import com.dataplatform.common.ratelimit.SlidingWindowRateLimitAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关层过滤器的 Rate Limit Filter。
 * <p>请求过滤器，处理网关或 Web 链路中的横切逻辑。</p>
 */
@Component
@RefreshScope
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String OPENAPI_PREFIX = "/openapi/";
    private static final String OPENAPI_DOCS_PREFIX = "/openapi/v1/docs";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisScript<Long> rateLimitScript;

    @Value("${gateway.rate-limit.default-window-sec:60}")
    private int defaultWindowSec = 60;

    @Value("${gateway.rate-limit.default-max-requests:100}")
    private int defaultMaxRequests = 100;

    public RateLimitFilter(ReactiveRedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitScript = new DefaultRedisScript<>(SlidingWindowRateLimitAlgorithm.ACQUIRE_SCRIPT, Long.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(OPENAPI_PREFIX) || path.startsWith(OPENAPI_DOCS_PREFIX)) {
            return chain.filter(exchange);
        }

        Object keyIdObj = exchange.getAttribute("keyId");
        if (keyIdObj == null) {
            return writeError(exchange, 401, "API Key 认证上下文缺失");
        }
        Long keyId = asLong(keyIdObj);
        if (keyId == null) {
            log.warn("Rate limit rejected because keyId is invalid: {}", keyIdObj);
            return writeError(exchange, 401, "API Key 认证上下文无效");
        }

        return loadRateLimitConfig(keyId)
                .flatMap(config -> {
                    boolean enabled = !config.containsKey("enabled") || asBoolean(config.get("enabled"), true);
                    if (!enabled) {
                        return chain.filter(exchange);
                    }
                    int windowSec = config.containsKey("windowSec")
                            ? asInt(config.get("windowSec"), defaultWindowSec) : defaultWindowSec;
                    int maxReqs = config.containsKey("maxReqs")
                            ? asInt(config.get("maxReqs"), defaultMaxRequests) : defaultMaxRequests;

                    long now = System.currentTimeMillis();
                    String windowKey = "openapi:window:" + keyId;
                    String member = SlidingWindowRateLimitAlgorithm.uniqueMember(now);

                    return redisTemplate.execute(rateLimitScript,
                                    Collections.singletonList(windowKey),
                                    windowSec * 1000L, now, member)
                            .next()
                            .flatMap(count -> {
                                if (count > maxReqs) {
                                    return writeRateLimitError(exchange, windowSec);
                                }
                                return chain.filter(exchange);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Rate limit check failed, rejecting request: {}", e.getMessage());
                    return writeError(exchange, 503, "限流服务暂时不可用");
                });
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

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> loadRateLimitConfig(Long keyId) {
        String configKey = "openapi:rate_limit:" + keyId;
        return redisTemplate.opsForValue().get(configKey)
                .defaultIfEmpty(Map.of("windowSec", defaultWindowSec, "maxReqs", defaultMaxRequests))
                .map(v -> (Map<String, Object>) v);
    }

    private int asInt(Object value, int defaultValue) {
        Long parsed = asLong(value);
        return parsed != null ? parsed.intValue() : defaultValue;
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            return asBoolean(list.get(1), defaultValue);
        }
        if (value instanceof String text) {
            if ("1".equals(text)) {
                return true;
            }
            if ("0".equals(text)) {
                return false;
            }
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            return asLong(list.get(1));
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Mono<Void> writeRateLimitError(ServerWebExchange exchange, int windowSec) {
        Result<Map<String, Integer>> result = Result.error(429, "请求过于频繁，请稍后再试");
        result.setData(Map.of("retryAfter", windowSec));
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(windowSec));
            exchange.getResponse().getHeaders().setContentLength(bytes.length);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
