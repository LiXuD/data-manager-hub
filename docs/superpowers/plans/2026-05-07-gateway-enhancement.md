# Gateway 增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 4 GlobalFilter implementations to the gateway module providing TraceId propagation, API Key authentication, sliding-window rate limiting, and structured request logging for the `/openapi/**` path prefix.

**Architecture:** Four independent `GlobalFilter` beans ordered by `@Order` annotation: TraceIdFilter(-3) → AuthFilter(-2) → RateLimitFilter(-1) → RequestLogFilter(0). Auth and rate-limit filters use `ReactiveRedisTemplate` for non-blocking Redis operations. Admin paths (`/api/v1/**`) pass through unchanged.

**Tech Stack:** Java 21, Spring Cloud Gateway, ReactiveRedisTemplate, Redis Lua scripting, JUnit 5 + Mockito + reactor-test

---

## File Structure

| File | Purpose |
|------|---------|
| `data-platform-gateway/pom.xml` | Add `spring-boot-starter-data-redis-reactive` |
| `data-platform-gateway/src/main/java/.../gateway/config/GatewayRedisConfig.java` | ReactiveRedisTemplate bean |
| `data-platform-gateway/src/main/java/.../gateway/filter/TraceIdFilter.java` | Generate/pass-through X-Trace-Id |
| `data-platform-gateway/src/main/java/.../gateway/filter/AuthFilter.java` | API Key auth via Redis |
| `data-platform-gateway/src/main/java/.../gateway/filter/RateLimitFilter.java` | Sliding window rate limit via Redis Lua |
| `data-platform-gateway/src/main/java/.../gateway/filter/RequestLogFilter.java` | Structured request logging |
| `data-platform-gateway/src/test/java/.../gateway/filter/TraceIdFilterTest.java` | 4 test cases |
| `data-platform-gateway/src/test/java/.../gateway/filter/AuthFilterTest.java` | 6 test cases |
| `data-platform-gateway/src/test/java/.../gateway/filter/RateLimitFilterTest.java` | 5 test cases |
| `data-platform-gateway/src/test/java/.../gateway/filter/RequestLogFilterTest.java` | 3 test cases |

---

### Task 1: Add reactive Redis dependency to gateway pom.xml

**Files:**
- Modify: `data-platform-gateway/pom.xml`

- [ ] **Step 1: Add spring-boot-starter-data-redis-reactive dependency**

Open `data-platform-gateway/pom.xml`. Add this inside `<dependencies>`, after the Redisson dependency block:

```xml
        <!-- Redis Reactive (for Gateway non-blocking Redis ops) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
```

- [ ] **Step 2: Add reactor-test dependency for unit tests**

Add this inside `<dependencies>`:

```xml
        <!-- Reactor Test (for Gateway filter unit tests) -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl data-platform-gateway -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add data-platform-gateway/pom.xml
git commit -m "chore(gateway): add spring-boot-starter-data-redis-reactive and reactor-test dependencies"
```

---

### Task 2: Create GatewayRedisConfig

**Files:**
- Create: `data-platform-gateway/src/main/java/com/dataplatform/gateway/config/GatewayRedisConfig.java`

- [ ] **Step 1: Create the config class**

```java
package com.dataplatform.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class GatewayRedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<Object> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        RedisSerializationContext<String, Object> serializationContext =
                RedisSerializationContext.<String, Object>newSerializationContext(new StringRedisSerializer())
                        .value(jsonSerializer)
                        .hashKey(new StringRedisSerializer())
                        .hashValue(jsonSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl data-platform-gateway -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add data-platform-gateway/src/main/java/com/dataplatform/gateway/config/GatewayRedisConfig.java
git commit -m "feat(gateway): add ReactiveRedisTemplate bean configuration"
```

---

### Task 3: Implement TraceIdFilter

**Files:**
- Create: `data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/TraceIdFilter.java`

- [ ] **Step 1: Create TraceIdFilter.java**

```java
package com.dataplatform.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_ATTR = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        exchange.getAttributes().put(TRACE_ID_ATTR, traceId);

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.header(TRACE_ID_HEADER, traceId))
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return -3;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl data-platform-gateway -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/TraceIdFilter.java
git commit -m "feat(gateway): add TraceIdFilter for request trace ID generation and propagation"
```

---

### Task 4: Write TraceIdFilter unit tests

**Files:**
- Create: `data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/TraceIdFilterTest.java`

- [ ] **Step 1: Create the test directory structure**

```bash
mkdir -p data-platform-gateway/src/test/java/com/dataplatform/gateway/filter
```

- [ ] **Step 2: Create TraceIdFilterTest.java**

```java
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
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl data-platform-gateway -Dtest=TraceIdFilterTest -q`
Expected: Tests run: 5, Failures: 0

- [ ] **Step 4: Commit**

```bash
git add data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/TraceIdFilterTest.java
git commit -m "test(gateway): add TraceIdFilter unit tests"
```

---

### Task 5: Implement AuthFilter

**Files:**
- Create: `data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/AuthFilter.java`

- [ ] **Step 1: Create AuthFilter.java**

```java
package com.dataplatform.gateway.filter;

import com.dataplatform.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
                .switchIfEmpty(Mono.defer(() -> writeErrorMono(exchange, 401, "API Key 无效或已过期")))
                .flatMap(value -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> keyInfo = objectMapper.convertValue(value, Map.class);
                    Integer status = (Integer) keyInfo.get("status");
                    if (status == null || status != 1) {
                        return writeErrorMono(exchange, 403, "调用方已禁用");
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

    private Mono<Void> writeErrorMono(ServerWebExchange exchange, int code, String message) {
        return writeError(exchange, code, message);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl data-platform-gateway -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/AuthFilter.java
git commit -m "feat(gateway): add AuthFilter for OpenAPI key authentication via Redis"
```

---

### Task 6: Write AuthFilter unit tests

**Files:**
- Create: `data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/AuthFilterTest.java`

- [ ] **Step 1: Create AuthFilterTest.java**

```java
package com.dataplatform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOps;

    private ObjectMapper objectMapper = new ObjectMapper();
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthFilter(redisTemplate, objectMapper);
    }

    @Test
    void shouldSkipNonOpenapiPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldReturn401WhenNoApiKey() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        verifyNoInteractions(chain);
    }

    @Test
    void shouldReturn401WhenApiKeyNotFoundInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:key:invalid-key")).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test")
                .header("X-Api-Key", "invalid-key")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        verifyNoInteractions(chain);
    }

    @Test
    void shouldReturn403WhenCallerIsDisabled() {
        Map<String, Object> keyInfo = Map.of("callerId", 1, "keyId", 10, "callerName", "disabled-caller", "status", 0);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:key:disabled-key")).thenReturn(Mono.just(keyInfo));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test")
                .header("X-Api-Key", "disabled-key")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
        verifyNoInteractions(chain);
    }

    @Test
    void shouldPassAndSetAttributesForValidKey() {
        Map<String, Object> keyInfo = Map.of("callerId", 1, "keyId", 10, "callerName", "test-caller", "status", 1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:key:valid-key")).thenReturn(Mono.just(keyInfo));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test")
                .header("X-Api-Key", "valid-key")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertEquals(1L, exchange.getAttribute("callerId"));
        assertEquals(10L, exchange.getAttribute("keyId"));
        assertEquals("test-caller", exchange.getAttribute("callerName"));
        verify(chain).filter(exchange);
    }

    @Test
    void shouldExtractKeyFromBearerHeader() {
        Map<String, Object> keyInfo = Map.of("callerId", 2, "keyId", 20, "callerName", "bearer-caller", "status", 1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:key:bearer-key-123")).thenReturn(Mono.just(keyInfo));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test")
                .header("Authorization", "Bearer bearer-key-123")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertEquals(2L, exchange.getAttribute("callerId"));
        verify(chain).filter(exchange);
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl data-platform-gateway -Dtest=AuthFilterTest -q`
Expected: Tests run: 6, Failures: 0

- [ ] **Step 3: Commit**

```bash
git add data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/AuthFilterTest.java
git commit -m "test(gateway): add AuthFilter unit tests"
```

---

### Task 7: Implement RateLimitFilter

**Files:**
- Create: `data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/RateLimitFilter.java`

- [ ] **Step 1: Create RateLimitFilter.java**

```java
package com.dataplatform.gateway.filter;

import com.dataplatform.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String OPENAPI_PREFIX = "/openapi/";
    private static final int DEFAULT_WINDOW_SEC = 60;
    private static final int DEFAULT_MAX_REQUESTS = 100;

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])
            local member = ARGV[3]
            redis.call('ZADD', key, now, member)
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            return redis.call('ZCARD', key)
            """;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisScript<Long> rateLimitScript;

    public RateLimitFilter(ReactiveRedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(OPENAPI_PREFIX)) {
            return chain.filter(exchange);
        }

        Object keyIdObj = exchange.getAttribute("keyId");
        if (keyIdObj == null) {
            return chain.filter(exchange);
        }
        Long keyId = ((Number) keyIdObj).longValue();

        return loadRateLimitConfig(keyId)
                .flatMap(config -> {
                    int windowSec = config.containsKey("windowSec")
                            ? ((Number) config.get("windowSec")).intValue() : DEFAULT_WINDOW_SEC;
                    int maxReqs = config.containsKey("maxReqs")
                            ? ((Number) config.get("maxReqs")).intValue() : DEFAULT_MAX_REQUESTS;

                    long now = System.currentTimeMillis();
                    String windowKey = "openapi:window:" + keyId;
                    String member = now + "-" + Thread.currentThread().threadId();

                    return redisTemplate.execute(rateLimitScript,
                                    Collections.singletonList(windowKey),
                                    windowSec * 1000L, now, member)
                            .flatMap(count -> {
                                if (count > maxReqs) {
                                    return writeRateLimitError(exchange, windowSec);
                                }
                                return chain.filter(exchange);
                            });
                })
                .onErrorResume(e -> {
                    log.warn("Rate limit check failed, passing through: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> loadRateLimitConfig(Long keyId) {
        String configKey = "openapi:rate_limit:" + keyId;
        return redisTemplate.opsForValue().get(configKey)
                .defaultIfEmpty(Map.of("windowSec", DEFAULT_WINDOW_SEC, "maxReqs", DEFAULT_MAX_REQUESTS))
                .map(v -> (Map<String, Object>) v);
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
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl data-platform-gateway -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/RateLimitFilter.java
git commit -m "feat(gateway): add RateLimitFilter with sliding window algorithm via Redis Lua"
```

---

### Task 8: Write RateLimitFilter unit tests

**Files:**
- Create: `data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/RateLimitFilterTest.java`

- [ ] **Step 1: Create RateLimitFilterTest.java**

```java
package com.dataplatform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOps;

    private ObjectMapper objectMapper = new ObjectMapper();
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redisTemplate, objectMapper);
    }

    @Test
    void shouldSkipNonOpenapiPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldUseDefaultConfigWhenNoneConfigured() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1")).thenReturn(Mono.empty());
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenReturn(Mono.just(50L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void shouldPassWhenUnderLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1")).thenReturn(Mono.just(Map.of("windowSec", 60, "maxReqs", 100)));
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenReturn(Mono.just(50L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void shouldReturn429WhenOverLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1")).thenReturn(Mono.just(Map.of("windowSec", 60, "maxReqs", 100)));
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenReturn(Mono.just(101L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(429, exchange.getResponse().getStatusCode().value());
        assertNotNull(exchange.getResponse().getHeaders().getFirst("Retry-After"));
        verifyNoInteractions(chain);
    }

    @Test
    void shouldPassThroughWhenRedisFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("openapi:rate_limit:1")).thenReturn(Mono.error(new RuntimeException("Redis down")));

        MockServerHttpRequest request = MockServerHttpRequest.get("/openapi/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("keyId", 1L);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl data-platform-gateway -Dtest=RateLimitFilterTest -q`
Expected: Tests run: 5, Failures: 0

- [ ] **Step 3: Commit**

```bash
git add data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/RateLimitFilterTest.java
git commit -m "test(gateway): add RateLimitFilter unit tests"
```

---

### Task 9: Implement RequestLogFilter

**Files:**
- Create: `data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/RequestLogFilter.java`

- [ ] **Step 1: Create RequestLogFilter.java**

```java
package com.dataplatform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
        return 0;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl data-platform-gateway -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add data-platform-gateway/src/main/java/com/dataplatform/gateway/filter/RequestLogFilter.java
git commit -m "feat(gateway): add RequestLogFilter for structured OpenAPI request logging"
```

---

### Task 10: Write RequestLogFilter unit tests

**Files:**
- Create: `data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/RequestLogFilterTest.java`

- [ ] **Step 1: Create RequestLogFilterTest.java**

```java
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
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl data-platform-gateway -Dtest=RequestLogFilterTest -q`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 3: Commit**

```bash
git add data-platform-gateway/src/test/java/com/dataplatform/gateway/filter/RequestLogFilterTest.java
git commit -m "test(gateway): add RequestLogFilter unit tests"
```

---

### Task 11: Full build verification and final commit

- [ ] **Step 1: Run all gateway tests**

Run: `mvn test -pl data-platform-gateway`
Expected: Tests run: 19, Failures: 0, BUILD SUCCESS

- [ ] **Step 2: Full compile verification**

Run: `mvn compile -pl data-platform-gateway -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Verify existing routes are unchanged (check application.yml)**

Run: `git diff data-platform-gateway/src/main/resources/application.yml`
Expected: No diff (no changes to existing routes or config)

- [ ] **Step 4: Final commit (if any cleanup needed)**

Only if there are minor fixes from the test/build verification. Otherwise skip.

---

## Test Summary

| Test Class | Cases | External Mocks | Covers |
|------------|-------|----------------|--------|
| TraceIdFilterTest | 5 | None | generate, pass-through, propagate, uniqueness, ordering |
| AuthFilterTest | 6 | ReactiveRedisTemplate | skip non-openapi, 401 missing key, 401 invalid key, 403 disabled, success, Bearer extraction |
| RateLimitFilterTest | 5 | ReactiveRedisTemplate, RedisScript | skip non-openapi, default config, under limit, over limit 429, Redis failure pass-through |
| RequestLogFilterTest | 3 | None | skip non-openapi, normal log, error status code |
| **Total** | **19** | | |

## Acceptance Checklist

- [ ] `mvn test -pl data-platform-gateway` — 19 tests pass
- [ ] `mvn compile -pl data-platform-gateway` — BUILD SUCCESS
- [ ] Existing 13 routes and Sa-Token config in `application.yml` unchanged
- [ ] All 4 filters use `@Component` for auto-detection
- [ ] Filter order: TraceIdFilter(-3) → AuthFilter(-2) → RateLimitFilter(-1) → RequestLogFilter(0)
- [ ] `/api/v1/**` paths pass through all filters without interception
- [ ] AuthFilter uses `ReactiveRedisTemplate` (non-blocking) not `RedissonClient` (blocking)
