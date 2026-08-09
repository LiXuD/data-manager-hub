package com.dataplatform.common.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 熔断器和重试管理器
 */
public class CircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManager.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();

    public CircuitBreakerManager() {
        // 默认熔断器配置
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)                    // 失败率50%触发熔断
            .waitDurationInOpenState(Duration.ofSeconds(30))  // 熔断30秒
            .permittedNumberOfCallsInHalfOpenState(5)   // 半开状态允许5次调用
            .slidingWindowSize(10)                       // 滑动窗口10次
            .minimumNumberOfCalls(10)                   // 累积10次后计算失败率
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .build();

        // 默认重试配置
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)                              // 最多重试3次
            .waitDuration(Duration.ofMillis(500))        // 重试间隔500ms
            .retryExceptions(Exception.class)           // 所有异常都重试
            .build();

        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
        this.retryRegistry = RetryRegistry.of(retryConfig);
    }

    /**
     * 获取或创建熔断器
     */
    public CircuitBreaker getCircuitBreaker(String vendorCode) {
        return circuitBreakers.computeIfAbsent(vendorCode,
            code -> circuitBreakerRegistry.circuitBreaker("vendor_" + code));
    }

    /**
     * 获取或创建重试器
     */
    public Retry getRetry(String vendorCode) {
        return retries.computeIfAbsent(vendorCode,
            code -> retryRegistry.retry("vendor_" + code));
    }

    /**
     * 使用熔断和重试执行操作
     */
    public <T> T executeWithProtection(String vendorCode, Supplier<T> supplier) {
        return executeWithProtection(vendorCode, supplier, ignored -> false);
    }

    /** Executes once and records both thrown exceptions and typed failure results. */
    public <T> T executeWithProtection(String vendorCode, Supplier<T> supplier,
                                       Predicate<T> resultFailurePredicate) {
        CircuitBreaker cb = getCircuitBreaker(vendorCode);
        cb.acquirePermission();
        long startedAt = System.nanoTime();
        try {
            T result = supplier.get();
            long duration = System.nanoTime() - startedAt;
            if (resultFailurePredicate.test(result)) {
                cb.onError(duration, TimeUnit.NANOSECONDS,
                        new TypedConnectorFailureException());
            } else {
                cb.onSuccess(duration, TimeUnit.NANOSECONDS);
            }
            return result;
        } catch (RuntimeException e) {
            cb.onError(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS, e);
            log.error("厂商调用失败: vendor={}, error={}", vendorCode, e.getMessage());
            throw e;
        }
    }

    private static final class TypedConnectorFailureException extends RuntimeException {
        private TypedConnectorFailureException() {
            super("typed connector failure");
        }
    }

    /**
     * 获取熔断器状态
     */
    public String getCircuitBreakerState(String vendorCode) {
        CircuitBreaker cb = circuitBreakers.get(vendorCode);
        if (cb == null) {
            return "UNKNOWN";
        }
        return cb.getState().name();
    }

    /**
     * 强制打开熔断器
     */
    public void forceOpen(String vendorCode) {
        CircuitBreaker cb = circuitBreakers.get(vendorCode);
        if (cb != null) {
            cb.transitionToOpenState();
            log.info("强制打开熔断器: {}", vendorCode);
        }
    }

    /**
     * 强制关闭熔断器
     */
    public void forceClose(String vendorCode) {
        CircuitBreaker cb = circuitBreakers.get(vendorCode);
        if (cb != null) {
            cb.transitionToClosedState();
            log.info("强制关闭熔断器: {}", vendorCode);
        }
    }
}
