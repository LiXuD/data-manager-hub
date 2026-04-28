package com.dataplatform.test;

import com.dataplatform.common.circuitbreaker.CircuitBreakerManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断器管理器测试
 */
@DisplayName("熔断器管理器测试")
class CircuitBreakerManagerTest {

    private CircuitBreakerManager manager;

    @BeforeEach
    void setUp() {
        manager = new CircuitBreakerManager();
    }

    @Test
    @DisplayName("获取熔断器 - 首次创建")
    void testGetCircuitBreaker_FirstCreate() {
        CircuitBreaker cb = manager.getCircuitBreaker("VENDOR_A");

        assertNotNull(cb);
        assertEquals("vendor_VENDOR_A", cb.getName());
    }

    @Test
    @DisplayName("获取熔断器 - 相同名称返回同一实例")
    void testGetCircuitBreaker_SameInstance() {
        CircuitBreaker cb1 = manager.getCircuitBreaker("VENDOR_A");
        CircuitBreaker cb2 = manager.getCircuitBreaker("VENDOR_A");

        assertSame(cb1, cb2);
    }

    @Test
    @DisplayName("获取熔断器 - 不同名称返回不同实例")
    void testGetCircuitBreaker_DifferentInstances() {
        CircuitBreaker cb1 = manager.getCircuitBreaker("VENDOR_A");
        CircuitBreaker cb2 = manager.getCircuitBreaker("VENDOR_B");

        assertNotSame(cb1, cb2);
    }

    @Test
    @DisplayName("获取重试器 - 首次创建")
    void testGetRetry_FirstCreate() {
        var retry = manager.getRetry("VENDOR_A");

        assertNotNull(retry);
        assertEquals("vendor_VENDOR_A", retry.getName());
    }

    @Test
    @DisplayName("获取重试器 - 相同名称返回同一实例")
    void testGetRetry_SameInstance() {
        var retry1 = manager.getRetry("VENDOR_A");
        var retry2 = manager.getRetry("VENDOR_A");

        assertSame(retry1, retry2);
    }

    @Test
    @DisplayName("执行保护操作 - 成功场景")
    void testExecuteWithProtection_Success() {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = manager.executeWithProtection("VENDOR_A", () -> {
            callCount.incrementAndGet();
            return "SUCCESS";
        });

        assertEquals("SUCCESS", result);
        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("执行保护操作 - 失败后重试")
    void testExecuteWithProtection_RetryOnFailure() {
        AtomicInteger callCount = new AtomicInteger(0);

        assertThrows(RuntimeException.class, () ->
            manager.executeWithProtection("VENDOR_A", () -> {
                callCount.incrementAndGet();
                throw new RuntimeException("模拟失败");
            })
        );

        assertTrue(callCount.get() >= 1, "应该至少调用一次");
    }

    @Test
    @DisplayName("执行保护操作 - 重试后成功")
    void testExecuteWithProtection_RetryThenSuccess() {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = manager.executeWithProtection("VENDOR_A", () -> {
            int count = callCount.incrementAndGet();
            if (count < 2) {
                throw new RuntimeException("第一次失败");
            }
            return "SUCCESS";
        });

        assertEquals("SUCCESS", result);
        assertTrue(callCount.get() >= 2, "应该重试后成功");
    }

    @Test
    @DisplayName("获取熔断器状态 - 初始状态为CLOSED")
    void testGetCircuitBreakerState_InitialState() {
        manager.getCircuitBreaker("VENDOR_A");

        String state = manager.getCircuitBreakerState("VENDOR_A");

        assertEquals("CLOSED", state);
    }

    @Test
    @DisplayName("获取熔断器状态 - 不存在的熔断器")
    void testGetCircuitBreakerState_NotExist() {
        String state = manager.getCircuitBreakerState("NON_EXISTENT");

        assertEquals("UNKNOWN", state);
    }

    @Test
    @DisplayName("强制打开熔断器")
    void testForceOpen() {
        manager.getCircuitBreaker("VENDOR_A");

        manager.forceOpen("VENDOR_A");

        assertEquals("OPEN", manager.getCircuitBreakerState("VENDOR_A"));
    }

    @Test
    @DisplayName("强制关闭熔断器")
    void testForceClose() {
        manager.getCircuitBreaker("VENDOR_A");
        manager.forceOpen("VENDOR_A");

        manager.forceClose("VENDOR_A");

        assertEquals("CLOSED", manager.getCircuitBreakerState("VENDOR_A"));
    }

    @Test
    @DisplayName("强制打开不存在的熔断器 - 无异常")
    void testForceOpen_NonExistent() {
        assertDoesNotThrow(() -> manager.forceOpen("NON_EXISTENT"));
    }

    @Test
    @DisplayName("强制关闭不存在的熔断器 - 无异常")
    void testForceClose_NonExistent() {
        assertDoesNotThrow(() -> manager.forceClose("NON_EXISTENT"));
    }

    @Test
    @DisplayName("熔断器打开时拒绝调用")
    void testCircuitBreaker_RejectWhenOpen() {
        manager.getCircuitBreaker("VENDOR_A");
        manager.forceOpen("VENDOR_A");

        assertThrows(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class, () ->
            manager.executeWithProtection("VENDOR_A", () -> "SUCCESS")
        );
    }

    @Test
    @DisplayName("多厂商独立熔断")
    void testMultipleVendors_IndependentCircuitBreakers() {
        manager.getCircuitBreaker("VENDOR_A");
        manager.getCircuitBreaker("VENDOR_B");

        manager.forceOpen("VENDOR_A");

        assertEquals("OPEN", manager.getCircuitBreakerState("VENDOR_A"));
        assertEquals("CLOSED", manager.getCircuitBreakerState("VENDOR_B"));
    }
}
