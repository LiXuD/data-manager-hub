package com.dataplatform.access.call.service;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleDTO;
import com.dataplatform.masterdata.graylog.api.feign.GraylogInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrayVendorResolverTest {

    private GraylogInternalFeignClient graylogFeignClient;
    private GrayVendorResolver resolver;

    private VendorConfigDTO stableConfig;
    private VendorConfigDTO grayConfig;

    @BeforeEach
    void setUp() {
        graylogFeignClient = mock(GraylogInternalFeignClient.class);
        resolver = new GrayVendorResolver(graylogFeignClient);

        stableConfig = new VendorConfigDTO();
        stableConfig.setVendorId(1L);
        stableConfig.setVendorName("stable-vendor");

        grayConfig = new VendorConfigDTO();
        grayConfig.setVendorId(2L);
        grayConfig.setVendorName("gray-vendor");
    }

    @Test
    void noRuleReturnsNull() {
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(null));

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);

        assertNull(result);
    }

    @Test
    void singleConfigReturnsNull() {
        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig), null);

        assertNull(result);
        verifyNoInteractions(graylogFeignClient);
    }

    @Test
    void nullConfigsReturnsNull() {
        VendorConfigDTO result = resolver.resolve("TEST_API", null, null);

        assertNull(result);
    }

    @Test
    void feignFailureReturnsNull() {
        when(graylogFeignClient.getActiveRule("TEST_API")).thenThrow(new RuntimeException("connection refused"));

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);

        assertNull(result);
    }

    @Test
    void randomTypeWeight100ReturnsGray() {
        GrayRuleDTO rule = createRule("random", null, 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        // weight=100 should always route to gray
        for (int i = 0; i < 20; i++) {
            VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);
            assertSame(grayConfig, result);
        }
    }

    @Test
    void randomTypeWeight0ReturnsNull() {
        GrayRuleDTO rule = createRule("random", null, 0);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        for (int i = 0; i < 20; i++) {
            VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);
            assertNull(result);
        }
    }

    @Test
    void randomTypeWeight50RoughlySplits() {
        GrayRuleDTO rule = createRule("random", null, 50);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        int grayCount = 0;
        int total = 1000;
        for (int i = 0; i < total; i++) {
            VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);
            if (result == grayConfig) {
                grayCount++;
            }
        }
        // Should be roughly 50% +/- 10%
        assertTrue(grayCount > 300 && grayCount < 700, "Expected ~50% gray traffic, got " + grayCount);
    }

    @Test
    void headerConditionMatchReturnsGray() {
        GrayRuleDTO rule = createRule("header", "X-Gray-Test", 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("X-Gray-Test")).thenReturn("true");
        GrayVendorResolver.GrayRequestContext ctx = createContext(mockRequest, null, null);

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), ctx);

        assertSame(grayConfig, result);
    }

    @Test
    void headerConditionNoMatchReturnsNull() {
        GrayRuleDTO rule = createRule("header", "X-Gray-Test", 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("X-Gray-Test")).thenReturn(null);
        GrayVendorResolver.GrayRequestContext ctx = createContext(mockRequest, null, null);

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), ctx);

        assertNull(result);
    }

    @Test
    void callerConditionMatchReturnsGray() {
        GrayRuleDTO rule = createRule("caller", "42", 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        GrayVendorResolver.GrayRequestContext ctx = createContext(null, 42L, null);

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), ctx);

        assertSame(grayConfig, result);
    }

    @Test
    void callerConditionNoMatchReturnsNull() {
        GrayRuleDTO rule = createRule("caller", "99", 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        GrayVendorResolver.GrayRequestContext ctx = createContext(null, 42L, null);

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), ctx);

        assertNull(result);
    }

    @Test
    void ipConditionMatchReturnsGray() {
        GrayRuleDTO rule = createRule("ip", "10.0.0.0/8", 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        GrayVendorResolver.GrayRequestContext ctx = createContext(null, null, "10.1.2.3");

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), ctx);

        assertSame(grayConfig, result);
    }

    @Test
    void ipConditionNoMatchReturnsNull() {
        GrayRuleDTO rule = createRule("ip", "10.0.0.0/8", 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        GrayVendorResolver.GrayRequestContext ctx = createContext(null, null, "192.168.1.1");

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), ctx);

        assertNull(result);
    }

    @Test
    void expiredRuleReturnsNull() {
        GrayRuleDTO rule = createRule("random", null, 100);
        rule.setEndTime(LocalDateTime.now().minusDays(1));
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);

        assertNull(result);
    }

    @Test
    void pendingRuleReturnsNull() {
        GrayRuleDTO rule = createRule("random", null, 100);
        rule.setStartTime(LocalDateTime.now().plusDays(1));
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        VendorConfigDTO result = resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);

        assertNull(result);
    }

    @Test
    void cacheHitDoesNotCallFeignAgain() throws Exception {
        GrayRuleDTO rule = createRule("random", null, 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);
        // Clear the mock call count
        clearInvocations(graylogFeignClient);
        resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);

        verifyNoInteractions(graylogFeignClient);
    }

    @Test
    void cacheExpiredRefetches() throws Exception {
        GrayRuleDTO rule = createRule("random", null, 100);
        when(graylogFeignClient.getActiveRule("TEST_API")).thenReturn(Result.success(rule));

        resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);

        // Force cache to be expired by manipulating the internal cache
        Field cacheField = GrayVendorResolver.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        ConcurrentHashMap<?, ?> cache = (ConcurrentHashMap<?, ?>) cacheField.get(resolver);
        cache.clear();

        clearInvocations(graylogFeignClient);
        resolver.resolve("TEST_API", List.of(stableConfig, grayConfig), null);

        verify(graylogFeignClient, times(1)).getActiveRule("TEST_API");
    }

    // --- helpers ---

    private GrayRuleDTO createRule(String conditionType, String conditionValue, int weight) {
        GrayRuleDTO rule = new GrayRuleDTO();
        rule.setId(1L);
        rule.setRuleName("test-rule");
        rule.setServiceName("TEST_API");
        rule.setConditionType(conditionType);
        rule.setConditionValue(conditionValue);
        rule.setWeight(weight);
        rule.setStatus("active");
        return rule;
    }

    private GrayVendorResolver.GrayRequestContext createContext(HttpServletRequest request, Long callerId, String clientIp) {
        GrayVendorResolver.GrayRequestContext ctx = new GrayVendorResolver.GrayRequestContext();
        ctx.request = request;
        ctx.callerId = callerId;
        ctx.clientIp = clientIp;
        return ctx;
    }
}
