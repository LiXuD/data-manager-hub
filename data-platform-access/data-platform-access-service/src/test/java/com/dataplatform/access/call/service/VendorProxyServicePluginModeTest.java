package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.service.ConnectorVendorExecutor;
import com.dataplatform.api.Result;
import com.dataplatform.common.adapter.VendorAdapter;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.adapter.VendorAdapterFactory;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.TransportStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VendorProxyServicePluginModeTest {

    private VendorProxyService service;
    private ConnectorVendorExecutor connectorExecutor;
    private AtomicInteger legacyCalls;

    @BeforeEach
    void setUp() {
        service = new VendorProxyService();
        connectorExecutor = mock(ConnectorVendorExecutor.class);
        VendorConfigInternalFeignClient configClient = mock(VendorConfigInternalFeignClient.class);
        when(configClient.getSecretKey("PLUGIN-VENDOR")).thenReturn(Result.success(null));
        ReflectionTestUtils.setField(service, "vendorConfigFeignClient", configClient);
        ReflectionTestUtils.setField(service, "connectorVendorExecutor", connectorExecutor);
        legacyCalls = new AtomicInteger();
        VendorAdapterFactory.registerAdapter("PLUGIN-VENDOR", legacyAdapter());
    }

    @AfterEach
    void tearDown() {
        VendorAdapterFactory.clearCache();
    }

    @Test
    void fallsBackToLegacyOnlyWhenPluginRequestWasNotSent() {
        VendorConfigDTO config = pluginConfig();
        when(connectorExecutor.execute(config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1")))
                .thenReturn(failure(RequestDeliveryState.NOT_SENT));

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service,
                "executeConfiguredRuntime", config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1"));

        assertTrue(Boolean.TRUE.equals(result.get("success")));
        assertEquals("PLUGIN", result.get("runtimeFallbackFrom"));
        assertEquals(1, legacyCalls.get());
    }

    @Test
    void neverReplaysLegacyWhenPluginMayHaveSentRequest() {
        VendorConfigDTO config = pluginConfig();
        when(connectorExecutor.execute(config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1")))
                .thenReturn(failure(RequestDeliveryState.MAYBE_SENT));

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service,
                "executeConfiguredRuntime", config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1"));

        assertFalse(Boolean.TRUE.equals(result.get("success")));
        assertEquals("MAYBE_SENT", result.get("deliveryState"));
        assertEquals(0, legacyCalls.get());
    }

    @Test
    void neverReplaysLegacyWhenPluginConfirmedRequestWasSent() {
        VendorConfigDTO config = pluginConfig();
        when(connectorExecutor.execute(config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1")))
                .thenReturn(failure(RequestDeliveryState.SENT));

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service,
                "executeConfiguredRuntime", config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1"));

        assertFalse(Boolean.TRUE.equals(result.get("success")));
        assertEquals("SENT", result.get("deliveryState"));
        assertEquals(0, legacyCalls.get());
    }

    @Test
    void pluginFallbackVendorDecisionRequiresNotSent() {
        Map<String, Object> maybeSent = Map.of(
                "errorCode", "CONNECTION_ERROR", "deliveryState", "MAYBE_SENT");
        Map<String, Object> notSent = Map.of(
                "errorCode", "CONNECTION_ERROR", "deliveryState", "NOT_SENT");

        assertFalse(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service, "shouldFallback", maybeSent)));
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service, "shouldFallback", notSent)));
    }

    private VendorConfigDTO pluginConfig() {
        VendorConfigDTO config = new VendorConfigDTO();
        config.setRuntimeMode("PLUGIN");
        config.setApiUrl("https://example.test");
        config.setMethod("POST");
        return config;
    }

    private ConnectorExecutionResult failure(RequestDeliveryState deliveryState) {
        return new ConnectorExecutionResult(
                TransportStatus.CONNECTION_ERROR, BusinessStatus.UNKNOWN, Map.of(),
                ErrorCategory.TRANSPORT_CONNECTION_ERROR, "CONNECTION_ERROR", "Vendor connection failed",
                BillingSignal.UNKNOWN, CacheSignal.NOT_CACHEABLE, deliveryState,
                "demo", "1.0.0", "1", "a".repeat(64), List.of());
    }

    private VendorAdapter legacyAdapter() {
        return new VendorAdapter() {
            @Override public String getVendorCode() { return "PLUGIN-VENDOR"; }
            @Override public boolean supports(String dataTypeCode) { return true; }
            @Override public Map<String, Object> execute(VendorAdapterConfig config, Map<String, Object> params) {
                legacyCalls.incrementAndGet();
                return new java.util.HashMap<>(Map.of("success", true, "data", Map.of("source", "legacy")));
            }
            @Override public Map<String, Object> transformRequest(Map<String, Object> params, String mapping) {
                return params;
            }
            @Override public Map<String, Object> transformResponse(Map<String, Object> response, String mapping) {
                return response;
            }
        };
    }
}
