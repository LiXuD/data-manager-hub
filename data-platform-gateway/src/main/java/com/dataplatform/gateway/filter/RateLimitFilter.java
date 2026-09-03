package com.dataplatform.gateway.filter;

import com.dataplatform.common.result.Result;
import com.dataplatform.common.ratelimit.SlidingWindowRateLimitAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
                .flatMap(config -> evaluateRateLimit(keyId, config))
                .onErrorResume(e -> {
                    log.error("Rate limit check failed, rejecting request: {}", e.getMessage());
                    return Mono.just(RateLimitDecision.unavailableDecision());
                })
                .flatMap(decision -> {
                    if (decision.unavailable()) {
                        return writeError(exchange, 503, "限流服务暂时不可用");
                    }
                    if (!decision.allowed()) {
                        return writeRateLimitError(exchange, decision.windowSec());
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<RateLimitDecision> evaluateRateLimit(Long keyId, Map<String, Object> config) {
        boolean enabled = !config.containsKey("enabled") || asBoolean(config.get("enabled"), true);
        if (!enabled) {
            return Mono.just(RateLimitDecision.allowed(defaultWindowSec));
        }
        int windowSec = configInt(config, "windowSec", defaultWindowSec);
        int maxReqs = configInt(config, "maxReqs", defaultMaxRequests);

        long now = System.currentTimeMillis();
        String windowKey = "openapi:window:" + keyId;
        String member = SlidingWindowRateLimitAlgorithm.uniqueMember(now);

        return redisTemplate.execute(rateLimitScript,
                        Collections.singletonList(windowKey),
                        windowSec * 1000L, now, member, (long) maxReqs)
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("限流脚本未返回结果")))
                .map(count -> {
                    if (count == null || count < 0) {
                        throw new IllegalStateException("限流脚本返回结果无效");
                    }
                    return new RateLimitDecision(count <= maxReqs, windowSec, false);
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

    private int configInt(Map<String, Object> config, String field, int defaultValue) {
        Object value = config.containsKey(field) ? config.get(field) : defaultValue;
        Long parsed = asLong(value);
        if (parsed == null || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("限流配置字段无效: " + field);
        }
        return parsed.intValue();
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            try {
                BigDecimal decimal = new BigDecimal(number.toString());
                if (decimal.compareTo(BigDecimal.ZERO) == 0) return false;
                if (decimal.compareTo(BigDecimal.ONE) == 0) return true;
                return defaultValue;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            return asBoolean(list.get(1), defaultValue);
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized)) {
                return false;
            }
            return defaultValue;
        }
        return defaultValue;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            try {
                BigDecimal decimal = new BigDecimal(number.toString());
                return decimal.signum() > 0 ? decimal.longValueExact() : null;
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            return asLong(list.get(1));
        }
        if (value instanceof String text) {
            try {
                long parsed = Long.parseLong(text.trim());
                return parsed > 0 ? parsed : null;
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

    private record RateLimitDecision(boolean allowed, int windowSec, boolean unavailable) {
        private static RateLimitDecision allowed(int windowSec) {
            return new RateLimitDecision(true, windowSec, false);
        }

        private static RateLimitDecision unavailableDecision() {
            return new RateLimitDecision(false, 0, true);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
