package com.dataplatform.access.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.api.Result;
import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.access.connector.runtime.ScopedConnectorSecretResolver;
import com.dataplatform.common.plugin.runtime.CompiledConnectorPipeline;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutionOutcome;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutor;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import com.dataplatform.masterdata.connector.api.feign.VendorConnectorInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.TransportStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DefaultConnectorVendorExecutorLifecycleTest {

    @Test
    void snapshotSwitchPinsOldRequestAndRoutesNewRequestToNewPipeline() throws Exception {
        VendorConnectorInternalFeignClient connectorClient = mock(VendorConnectorInternalFeignClient.class);
        VendorSecurityInternalFeignClient securityClient = mock(VendorSecurityInternalFeignClient.class);
        VendorConfigInternalFeignClient vendorClient = mock(VendorConfigInternalFeignClient.class);
        PipelineCompiler compiler = mock(PipelineCompiler.class);
        ConnectorPipelineExecutor executor = mock(ConnectorPipelineExecutor.class);
        CompiledConnectorPipeline oldPipeline = mock(CompiledConnectorPipeline.class);
        CompiledConnectorPipeline nextPipeline = mock(CompiledConnectorPipeline.class);
        CompiledConnectorPipeline.RequestLease oldLease = mock(CompiledConnectorPipeline.RequestLease.class);
        CompiledConnectorPipeline.RequestLease nextLease = mock(CompiledConnectorPipeline.RequestLease.class);
        when(oldPipeline.acquire()).thenReturn(oldLease);
        when(nextPipeline.acquire()).thenReturn(nextLease);
        when(compiler.compile(any())).thenReturn(oldPipeline, nextPipeline);
        when(connectorClient.getRuntimeSnapshot(7L)).thenReturn(
                Result.success(snapshot(1, "old")), Result.success(snapshot(2, "new")));
        CountDownLatch oldEntered = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        when(executor.executeWithOutcome(any(CompiledConnectorPipeline.RequestLease.class), any()))
                .thenAnswer(invocation -> {
                    CompiledConnectorPipeline.RequestLease lease = invocation.getArgument(0);
                    if (lease == oldLease) {
                        oldEntered.countDown();
                        assertTrue(releaseOld.await(2, TimeUnit.SECONDS));
                        return new ConnectorPipelineExecutionOutcome(success("old"), false);
                    }
                    return new ConnectorPipelineExecutionOutcome(success("new"), false);
                });
        DefaultConnectorVendorExecutor service = new DefaultConnectorVendorExecutor(
                connectorClient, securityClient, vendorClient, compiler, executor,
                new ScopedConnectorSecretResolver(), new ObjectMapper(), new SimpleMeterRegistry(),
                Clock.systemUTC(), new ConnectorRuntimeProperties(), mock(java.util.concurrent.ExecutorService.class));
        VendorConfigDTO config = new VendorConfigDTO();
        config.setId(7L);
        config.setTimeout(5_000);
        config.setRetryCount(0);
        var callers = Executors.newFixedThreadPool(2);
        try {
            var oldCall = callers.submit(() -> service.execute(config, "DEMO", "TYPE", Map.of()));
            assertTrue(oldEntered.await(2, TimeUnit.SECONDS));
            var newCall = callers.submit(() -> service.execute(config, "DEMO", "TYPE", Map.of()));

            ConnectorExecutionResult next = newCall.get(2, TimeUnit.SECONDS);
            assertEquals("new", next.normalizedData().get("value"));
            verify(oldPipeline).close();
            var order = inOrder(nextPipeline, oldPipeline);
            order.verify(nextPipeline).acquire();
            order.verify(oldPipeline).close();

            releaseOld.countDown();
            ConnectorExecutionResult old = oldCall.get(2, TimeUnit.SECONDS);
            assertEquals("old", old.normalizedData().get("value"));
            verify(oldLease).close();
            verify(nextLease).close();
        } finally {
            releaseOld.countDown();
            callers.shutdownNow();
            service.close();
        }
    }

    private VendorConnectorRuntimeSnapshotDTO snapshot(int version, String hash) {
        ConnectorPipelineStepDTO step = new ConnectorPipelineStepDTO(
                "transport", "TRANSPORT", "test", "1.0.0", 0, true,
                Map.of(), "a".repeat(64));
        return new VendorConnectorRuntimeSnapshotDTO(7L, (long) version, version, hash,
                1, "ACTIVE", List.of(step), null);
    }

    private ConnectorExecutionResult success(String value) {
        return new ConnectorExecutionResult(TransportStatus.SUCCESS, BusinessStatus.SUCCESS,
                Map.of("value", value), null, null, null, BillingSignal.ELIGIBLE,
                CacheSignal.CACHEABLE, RequestDeliveryState.SENT, "test", "1.0.0",
                value, value, List.of());
    }
}
