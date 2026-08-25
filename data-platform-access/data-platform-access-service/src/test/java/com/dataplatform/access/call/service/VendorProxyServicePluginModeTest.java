package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.api.Result;
import com.dataplatform.access.connector.service.ConnectorVendorExecutor;
import com.dataplatform.common.circuitbreaker.CircuitBreakerManager;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.TransportStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VendorProxyServicePluginModeTest {

    private VendorProxyService service;
    private ConnectorVendorExecutor connectorExecutor;
    private VendorConfigInternalFeignClient configClient;
    private VendorInternalFeignClient vendorClient;

    @BeforeEach
    void setUp() {
        service = new VendorProxyService();
        connectorExecutor = mock(ConnectorVendorExecutor.class);
        configClient = mock(VendorConfigInternalFeignClient.class);
        vendorClient = mock(VendorInternalFeignClient.class);
        ReflectionTestUtils.setField(service, "connectorVendorExecutor", connectorExecutor);
        ReflectionTestUtils.setField(service, "vendorConfigFeignClient", configClient);
        ReflectionTestUtils.setField(service, "vendorFeignClient", vendorClient);
        ReflectionTestUtils.setField(service, "circuitBreakerManager", new CircuitBreakerManager());
    }

    @Test
    void rejectsNonPluginConfigurationWithoutExecutingAnyRuntime() {
        VendorConfigDTO config = new VendorConfigDTO();
        config.setRuntimeMode("LEGACY");

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service,
                "executeConfiguredRuntime", config, "VENDOR", "PERSON", Map.of("id", "1"));

        assertFalse(Boolean.TRUE.equals(result.get("success")));
        assertEquals("CONFIGURATION_ERROR", result.get("errorCategory"));
        assertEquals("CONFIGURATION_ERROR", result.get("errorCode"));
        assertEquals("PLUGIN_RUNTIME_REQUIRED", result.get("connectorErrorCode"));
        assertEquals("NOT_SENT", result.get("deliveryState"));
        verify(connectorExecutor, never()).execute(config, "VENDOR", "PERSON", Map.of("id", "1"));
    }

    @Test
    void neverReplaysAStaticAdapterAfterPluginFailure() {
        VendorConfigDTO config = pluginConfig();
        when(connectorExecutor.execute(config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1")))
                .thenReturn(failure(ErrorCategory.TRANSPORT_CONNECTION_ERROR, RequestDeliveryState.NOT_SENT));

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service,
                "executeConfiguredRuntime", config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1"));

        assertFalse(Boolean.TRUE.equals(result.get("success")));
        assertEquals("NOT_SENT", result.get("deliveryState"));
        assertNull(result.get("runtimeFallbackFrom"));
        verify(connectorExecutor).execute(config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1"));
    }

    @Test
    void forwardsPlatformRequestIdToConnectorExecutor() {
        VendorConfigDTO config = activePluginConfig(1L, null);
        Map<String, Object> params = Map.of("id", "1");
        when(connectorExecutor.execute(config, "PLUGIN-VENDOR", "PERSON", params, "platform-123"))
                .thenReturn(success("plugin", "1.0.0"));

        Map<String, Object> result = service.callVendor(
                "PLUGIN-VENDOR", "PERSON", params, config, "platform-123");

        assertTrue(Boolean.TRUE.equals(result.get("success")));
        verify(connectorExecutor).execute(
                config, "PLUGIN-VENDOR", "PERSON", params, "platform-123");
    }

    @Test
    void backupVendorRequiresExplicitNotSentAndWhitelistedCategory() {
        assertTrue(shouldFallback(ErrorCategory.TRANSPORT_CONNECTION_ERROR, "NOT_SENT"));
        assertTrue(shouldFallback(ErrorCategory.TRANSPORT_TIMEOUT, "NOT_SENT"));
        assertFalse(shouldFallback(ErrorCategory.TRANSPORT_CONNECTION_ERROR, "SENT"));
        assertFalse(shouldFallback(ErrorCategory.TRANSPORT_TIMEOUT, "MAYBE_SENT"));
        assertFalse(shouldFallback(ErrorCategory.TRANSPORT_CONNECTION_ERROR, null));
        assertFalse(shouldFallback(ErrorCategory.PLUGIN_NOT_READY, "NOT_SENT"));
        assertFalse(shouldFallback(ErrorCategory.CONFIGURATION_ERROR, "NOT_SENT"));
        assertFalse(shouldFallback(ErrorCategory.TRANSPORT_HTTP_ERROR, "NOT_SENT"));
        assertFalse(shouldFallback(ErrorCategory.RESPONSE_PARSE_ERROR, "NOT_SENT"));
        assertFalse(shouldFallback(ErrorCategory.PLUGIN_INTERNAL_ERROR, "NOT_SENT"));
    }

    @Test
    void publicCallUsesBackupExactlyOnceForExplicitNotSentTransportFailure() {
        VendorConfigDTO primary = activePluginConfig(1L, 2L);
        VendorConfigDTO backup = activePluginConfig(2L, null);
        VendorInfoDTO backupVendor = activeVendor(2L, "BACKUP");
        Map<String, Object> params = Map.of("id", "1");
        when(configClient.getByVendorCodeAndDataTypeCode("PRIMARY", "PERSON"))
                .thenReturn(Result.success(primary));
        when(vendorClient.getById(2L)).thenReturn(Result.success(backupVendor));
        when(configClient.getByVendorIdAndDataTypeCode(2L, "PERSON"))
                .thenReturn(Result.success(backup));
        when(connectorExecutor.execute(primary, "PRIMARY", "PERSON", params))
                .thenReturn(failure(ErrorCategory.TRANSPORT_CONNECTION_ERROR, RequestDeliveryState.NOT_SENT));
        when(connectorExecutor.execute(backup, "BACKUP", "PERSON", params))
                .thenReturn(success("backup-plugin", "2.1.0"));

        Map<String, Object> result = service.callVendor("PRIMARY", "PERSON", params);

        assertTrue(Boolean.TRUE.equals(result.get("success")));
        assertEquals(2L, result.get("actualVendorId"));
        assertEquals("BACKUP", result.get("actualVendorCode"));
        assertEquals("backup-plugin", result.get("pluginId"));
        assertEquals("2.1.0", result.get("pluginVersion"));
        assertEquals("PRIMARY", result.get("fallbackFrom"));
        verify(connectorExecutor, times(1)).execute(primary, "PRIMARY", "PERSON", params);
        verify(connectorExecutor, times(1)).execute(backup, "BACKUP", "PERSON", params);
    }

    @Test
    void explicitRouteUsesExactFallbackWithoutFollowingFallbackConfigChain() {
        VendorConfigDTO primary = activePluginConfig(1L, 99L);
        VendorConfigDTO fallback = activePluginConfig(2L, 3L);
        Map<String, Object> params = Map.of("id", "1");
        when(connectorExecutor.execute(primary, "PRIMARY", "PERSON", params))
                .thenReturn(failure(ErrorCategory.TRANSPORT_CONNECTION_ERROR, RequestDeliveryState.NOT_SENT));
        when(connectorExecutor.execute(fallback, "BACKUP", "PERSON", params))
                .thenReturn(success("backup-plugin", "2.1.0"));

        Map<String, Object> result = service.callVendor(
                "PRIMARY", "BACKUP", "PERSON", params, primary, fallback);

        assertTrue(Boolean.TRUE.equals(result.get("success")));
        assertEquals(2L, result.get("actualVendorId"));
        assertEquals("BACKUP", result.get("actualVendorCode"));
        assertEquals("PRIMARY", result.get("fallbackFrom"));
        verify(connectorExecutor, times(1)).execute(primary, "PRIMARY", "PERSON", params);
        verify(connectorExecutor, times(1)).execute(fallback, "BACKUP", "PERSON", params);
        verifyNoInteractions(vendorClient, configClient);
    }

    @Test
    void explicitRouteNeverFallsBackAfterMaybeSentOrSentFailure() {
        for (RequestDeliveryState deliveryState : new RequestDeliveryState[]{
                RequestDeliveryState.MAYBE_SENT, RequestDeliveryState.SENT}) {
            setUp();
            VendorConfigDTO primary = activePluginConfig(1L, 2L);
            VendorConfigDTO fallback = activePluginConfig(2L, null);
            Map<String, Object> params = Map.of("delivery", deliveryState.name());
            when(connectorExecutor.execute(primary, "PRIMARY", "PERSON", params))
                    .thenReturn(failure(ErrorCategory.TRANSPORT_TIMEOUT, deliveryState));

            Map<String, Object> result = service.callVendor(
                    "PRIMARY", "BACKUP", "PERSON", params, primary, fallback);

            assertFalse(Boolean.TRUE.equals(result.get("success")));
            verify(connectorExecutor, times(1)).execute(primary, "PRIMARY", "PERSON", params);
            verify(connectorExecutor, never()).execute(fallback, "BACKUP", "PERSON", params);
        }
    }

    @Test
    void publicCallNeverUsesBackupForSentMaybeSentMissingDeliveryOrNonWhitelistedFailure() {
        assertPublicCallDoesNotFallback(ErrorCategory.TRANSPORT_CONNECTION_ERROR, RequestDeliveryState.SENT);
        assertPublicCallDoesNotFallback(ErrorCategory.TRANSPORT_TIMEOUT, RequestDeliveryState.MAYBE_SENT);
        assertPublicCallDoesNotFallback(ErrorCategory.TRANSPORT_TIMEOUT, null);
        assertPublicCallDoesNotFallback(ErrorCategory.RESPONSE_PARSE_ERROR, RequestDeliveryState.NOT_SENT);
    }

    @Test
    void notSentTransportFailureCanNeverRemainBillingEligible() {
        VendorConfigDTO config = pluginConfig();
        ConnectorExecutionResult failure = new ConnectorExecutionResult(
                TransportStatus.CONNECTION_ERROR, BusinessStatus.UNKNOWN, Map.of(),
                ErrorCategory.TRANSPORT_CONNECTION_ERROR, "CONNECTOR_FAILED", "Vendor call failed",
                BillingSignal.ELIGIBLE, CacheSignal.NOT_CACHEABLE, RequestDeliveryState.NOT_SENT,
                "demo", "1.0.0", "1", "a".repeat(64), List.of());
        when(connectorExecutor.execute(config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1")))
                .thenReturn(failure);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service,
                "executeConfiguredRuntime", config, "PLUGIN-VENDOR", "PERSON", Map.of("id", "1"));

        assertEquals("INELIGIBLE", result.get("billingSignal"));
        assertEquals("NOT_SENT", result.get("deliveryState"));
    }

    private void assertPublicCallDoesNotFallback(ErrorCategory category, RequestDeliveryState deliveryState) {
        setUp();
        VendorConfigDTO primary = activePluginConfig(1L, 2L);
        Map<String, Object> params = Map.of("case", category.name() + String.valueOf(deliveryState));
        when(configClient.getByVendorCodeAndDataTypeCode("PRIMARY", "PERSON"))
                .thenReturn(Result.success(primary));
        when(connectorExecutor.execute(primary, "PRIMARY", "PERSON", params))
                .thenReturn(failure(category, deliveryState));

        Map<String, Object> result = service.callVendor("PRIMARY", "PERSON", params);

        assertFalse(Boolean.TRUE.equals(result.get("success")));
        verify(connectorExecutor, times(1)).execute(primary, "PRIMARY", "PERSON", params);
        verify(vendorClient, never()).getById(2L);
        verify(configClient, never()).getByVendorIdAndDataTypeCode(2L, "PERSON");
    }

    private boolean shouldFallback(ErrorCategory category, String deliveryState) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("errorCategory", category.name());
        if (deliveryState != null) result.put("deliveryState", deliveryState);
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service, "shouldFallback", result));
    }

    private VendorConfigDTO pluginConfig() {
        VendorConfigDTO config = new VendorConfigDTO();
        config.setRuntimeMode("PLUGIN");
        return config;
    }

    private VendorConfigDTO activePluginConfig(Long vendorId, Long fallbackVendorId) {
        VendorConfigDTO config = pluginConfig();
        config.setVendorId(vendorId);
        config.setFallbackVendorId(fallbackVendorId);
        config.setStatus("active");
        return config;
    }

    private VendorInfoDTO activeVendor(Long vendorId, String vendorCode) {
        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(vendorId);
        vendor.setVendorCode(vendorCode);
        vendor.setStatus("active");
        return vendor;
    }

    private ConnectorExecutionResult failure(ErrorCategory category, RequestDeliveryState deliveryState) {
        return new ConnectorExecutionResult(
                TransportStatus.CONNECTION_ERROR, BusinessStatus.UNKNOWN, Map.of(),
                category, "CONNECTOR_FAILED", "Vendor call failed",
                BillingSignal.UNKNOWN, CacheSignal.NOT_CACHEABLE, deliveryState,
                "demo", "1.0.0", "1", "a".repeat(64), List.of());
    }

    private ConnectorExecutionResult success(String pluginId, String pluginVersion) {
        return new ConnectorExecutionResult(
                TransportStatus.SUCCESS, BusinessStatus.SUCCESS, Map.of("source", "backup"),
                null, null, null,
                BillingSignal.ELIGIBLE, CacheSignal.CACHEABLE, RequestDeliveryState.SENT,
                pluginId, pluginVersion, "2", "b".repeat(64), List.of());
    }
}
