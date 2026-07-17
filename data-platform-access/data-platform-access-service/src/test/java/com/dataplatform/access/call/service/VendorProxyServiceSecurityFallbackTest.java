package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.api.Result;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VendorProxyServiceSecurityFallbackTest {

    private VendorProxyService service;
    private VendorSecurityInternalFeignClient securityClient;

    @BeforeEach
    void setUp() {
        service = new VendorProxyService();
        VendorConfigInternalFeignClient configClient = mock(VendorConfigInternalFeignClient.class);
        securityClient = mock(VendorSecurityInternalFeignClient.class);
        when(configClient.getSecretKey("VENDOR")).thenReturn(Result.success("legacy-secret"));
        ReflectionTestUtils.setField(service, "vendorConfigFeignClient", configClient);
        ReflectionTestUtils.setField(service, "vendorSecurityFeignClient", securityClient);
    }

    @Test
    void shouldFailClosedWhenNewPipelineCannotBeLoaded() {
        VendorConfigDTO config = config(null);
        when(securityClient.getRuntimeSecurity(1L)).thenThrow(new IllegalStateException("unavailable"));

        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "buildAdapterConfig", config, "VENDOR", "TYPE"));
    }

    @Test
    void shouldRetainLegacyFallbackDuringRollingUpgrade() {
        VendorConfigDTO config = config("MD5");
        when(securityClient.getRuntimeSecurity(1L)).thenThrow(new IllegalStateException("unavailable"));

        VendorAdapterConfig result = ReflectionTestUtils.invokeMethod(
                service, "buildAdapterConfig", config, "VENDOR", "TYPE");

        assertEquals("MD5", result.getSignType());
        assertEquals("legacy-secret", result.getSecretKey());
    }

    private VendorConfigDTO config(String signType) {
        VendorConfigDTO config = new VendorConfigDTO();
        config.setId(1L);
        config.setApiUrl("https://example.test/api");
        config.setMethod("POST");
        config.setSignType(signType);
        return config;
    }
}
