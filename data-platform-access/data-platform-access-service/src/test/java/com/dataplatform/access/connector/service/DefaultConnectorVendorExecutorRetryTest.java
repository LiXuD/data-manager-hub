package com.dataplatform.access.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.access.connector.runtime.ScopedConnectorSecretResolver;
import com.dataplatform.common.plugin.runtime.CompiledConnectorPipeline;
import com.dataplatform.common.plugin.runtime.ConnectorExecutionRequest;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutionOutcome;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutor;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.masterdata.connector.api.feign.VendorConnectorInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.TransportStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DefaultConnectorVendorExecutorRetryTest {

    private ConnectorPipelineExecutor pipelineExecutor;
    private DefaultConnectorVendorExecutor vendorExecutor;
    private CompiledConnectorPipeline.RequestLease pipelineLease;
    private ConnectorExecutionRequest request;

    @BeforeEach
    void setUp() {
        pipelineExecutor = mock(ConnectorPipelineExecutor.class);
        pipelineLease = mock(CompiledConnectorPipeline.RequestLease.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);
        request = new ConnectorExecutionRequest(Map.of(), "DEMO",
                clock.instant().plusSeconds(10), () -> false);
        vendorExecutor = new DefaultConnectorVendorExecutor(
                mock(VendorConnectorInternalFeignClient.class),
                mock(VendorSecurityInternalFeignClient.class),
                mock(VendorConfigInternalFeignClient.class),
                mock(PipelineCompiler.class), pipelineExecutor, new ScopedConnectorSecretResolver(),
                new ObjectMapper(), new SimpleMeterRegistry(), clock, new ConnectorRuntimeProperties(),
                mock(java.util.concurrent.ExecutorService.class));
    }

    @Test
    void keyedIdempotentPostCanRetryWithinPlatformLimit() {
        when(pipelineExecutor.executeWithOutcome(same(pipelineLease), any(ConnectorExecutionRequest.class)))
                .thenReturn(outcome(timeout(), true), outcome(success(), true));

        ConnectorExecutionResult result = invokeRetry(1);

        assertEquals(TransportStatus.SUCCESS, result.transportStatus());
        org.mockito.ArgumentCaptor<ConnectorExecutionRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ConnectorExecutionRequest.class);
        verify(pipelineExecutor, times(2)).executeWithOutcome(same(pipelineLease), captor.capture());
        assertEquals(List.of(1, 2), captor.getAllValues().stream()
                .map(ConnectorExecutionRequest::attemptNo).toList());
        assertEquals(1, captor.getAllValues().stream()
                .map(ConnectorExecutionRequest::requestId).distinct().count());
        assertEquals(1, captor.getAllValues().stream()
                .map(ConnectorExecutionRequest::deadline).distinct().count());
    }

    @Test
    void nonIdempotentPostNeverRetries() {
        when(pipelineExecutor.executeWithOutcome(same(pipelineLease), any(ConnectorExecutionRequest.class)))
                .thenReturn(outcome(timeout(), false));

        ConnectorExecutionResult result = invokeRetry(5);

        assertEquals(ErrorCategory.TRANSPORT_TIMEOUT, result.errorCategory());
        verify(pipelineExecutor).executeWithOutcome(same(pipelineLease), any(ConnectorExecutionRequest.class));
    }

    @Test
    void pluginCannotRaiseRetryCountAbovePlatformCap() {
        when(pipelineExecutor.executeWithOutcome(same(pipelineLease), any(ConnectorExecutionRequest.class)))
                .thenReturn(outcome(timeout(), true));

        invokeRetry(1000);

        verify(pipelineExecutor, times(11)).executeWithOutcome(
                same(pipelineLease), any(ConnectorExecutionRequest.class));
    }

    private ConnectorExecutionResult invokeRetry(int retryCount) {
        VendorConfigDTO config = new VendorConfigDTO();
        config.setRetryCount(retryCount);
        return ReflectionTestUtils.invokeMethod(vendorExecutor, "executeWithRetry",
                config, pipelineLease, request, Map.of());
    }

    private ConnectorPipelineExecutionOutcome outcome(
            ConnectorExecutionResult result, boolean retryPermitted) {
        return new ConnectorPipelineExecutionOutcome(result, retryPermitted);
    }

    private ConnectorExecutionResult timeout() {
        return new ConnectorExecutionResult(TransportStatus.TIMEOUT, BusinessStatus.UNKNOWN, Map.of(),
                ErrorCategory.TRANSPORT_TIMEOUT, "TRANSPORT_TIMEOUT", "Vendor request timed out",
                BillingSignal.UNKNOWN, CacheSignal.NOT_CACHEABLE, RequestDeliveryState.MAYBE_SENT,
                "demo", "1.0.0", "1", "a".repeat(64), List.of());
    }

    private ConnectorExecutionResult success() {
        return new ConnectorExecutionResult(TransportStatus.SUCCESS, BusinessStatus.SUCCESS, Map.of("ok", true),
                null, null, null, BillingSignal.ELIGIBLE, CacheSignal.CACHEABLE,
                RequestDeliveryState.SENT, "demo", "1.0.0", "1", "a".repeat(64), List.of());
    }
}
