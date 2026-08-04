package com.dataplatform.access.connector.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.access.connector.runtime.ScopedConnectorSecretResolver;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutor;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.masterdata.connector.api.feign.VendorConnectorInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DefaultConnectorVendorExecutorPolicyTest {

    @Test
    void automaticRetryIsRestrictedToSafeHttpMethods() {
        DefaultConnectorVendorExecutor executor = new DefaultConnectorVendorExecutor(
                mock(VendorConnectorInternalFeignClient.class), mock(VendorSecurityInternalFeignClient.class),
                mock(VendorConfigInternalFeignClient.class), mock(PipelineCompiler.class),
                mock(ConnectorPipelineExecutor.class), mock(ScopedConnectorSecretResolver.class),
                new ObjectMapper(), new SimpleMeterRegistry(), Clock.systemUTC(),
                new ConnectorRuntimeProperties());

        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(executor, "safeMethod", "GET")));
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(executor, "safeMethod", "HEAD")));
        assertFalse(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(executor, "safeMethod", "POST")));
        assertFalse(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(executor, "safeMethod", "PATCH")));
    }
}
