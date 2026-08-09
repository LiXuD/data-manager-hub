package com.dataplatform.access.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.api.dto.ConnectorTestPipelineStepDTO;
import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.access.connector.runtime.ScopedConnectorSecretResolver;
import com.dataplatform.common.plugin.runtime.CompiledConnectorPipeline;
import com.dataplatform.common.plugin.runtime.ConnectorExecutionRequest;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultConnectorVendorExecutorDraftIsolationTest {

    private final java.util.List<java.util.concurrent.ExecutorService> executors = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        executors.forEach(java.util.concurrent.ExecutorService::shutdownNow);
    }

    @Test
    void blockingStageThatIgnoresInterruptStillReturnsAtHostDeadline() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor draftPool = draftPool(1, 1);
        ConnectorPipelineExecutor pipelineExecutor = blockingExecutor(entered, release);
        ConnectorRuntimeProperties properties = new ConnectorRuntimeProperties();
        properties.setTestTimeoutMs(100);
        DefaultConnectorVendorExecutor service = service(pipelineExecutor, draftPool, properties);

        long started = System.nanoTime();
        ConnectorExecutionResult result = service.executeDraft(config(100), "DEMO", steps(), Map.of());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        release.countDown();

        assertEquals(ErrorCategory.TRANSPORT_TIMEOUT, result.errorCategory());
        assertEquals("CONNECTOR_TEST_TIMEOUT", result.errorCode());
        assertEquals(RequestDeliveryState.MAYBE_SENT, result.deliveryState());
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        assertTrue(elapsedMillis < 1_000, "host deadline must not wait for a non-cooperative stage");
    }

    @Test
    void boundedDraftExecutorRejectsWorkWhenWorkerAndQueueAreOccupied() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor draftPool = draftPool(1, 1);
        ConnectorRuntimeProperties properties = new ConnectorRuntimeProperties();
        properties.setTestTimeoutMs(5_000);
        DefaultConnectorVendorExecutor service = service(blockingExecutor(entered, release), draftPool, properties);
        var callers = Executors.newFixedThreadPool(2);
        executors.add(callers);
        try {
            var first = callers.submit(() -> service.executeDraft(config(5_000), "DEMO", steps(), Map.of()));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            var second = callers.submit(() -> service.executeDraft(config(5_000), "DEMO", steps(), Map.of()));
            long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (draftPool.getQueue().isEmpty() && System.nanoTime() < waitUntil) Thread.onSpinWait();
            assertEquals(1, draftPool.getQueue().size());

            ConnectorExecutionResult rejected = service.executeDraft(config(5_000), "DEMO", steps(), Map.of());
            assertEquals(ErrorCategory.PLUGIN_NOT_READY, rejected.errorCategory());
            assertEquals("CONNECTOR_TEST_EXECUTOR_SATURATED", rejected.errorCode());
            assertEquals(RequestDeliveryState.NOT_SENT, rejected.deliveryState());

            release.countDown();
            assertTrue(first.get(2, TimeUnit.SECONDS).successful());
            assertTrue(second.get(2, TimeUnit.SECONDS).successful());
        } finally {
            release.countDown();
        }
    }

    private DefaultConnectorVendorExecutor service(
            ConnectorPipelineExecutor pipelineExecutor,
            ThreadPoolExecutor draftPool,
            ConnectorRuntimeProperties properties) throws Exception {
        PipelineCompiler compiler = mock(PipelineCompiler.class);
        when(compiler.sha256(any())).thenReturn("a".repeat(64));
        when(compiler.compile(any(ConnectorPipelineDefinition.class)))
                .thenReturn(mock(CompiledConnectorPipeline.class));
        return new DefaultConnectorVendorExecutor(
                mock(VendorConnectorInternalFeignClient.class),
                mock(VendorSecurityInternalFeignClient.class),
                mock(VendorConfigInternalFeignClient.class), compiler, pipelineExecutor,
                new ScopedConnectorSecretResolver(), new ObjectMapper(), new SimpleMeterRegistry(),
                Clock.systemUTC(), properties, draftPool);
    }

    private ConnectorPipelineExecutor blockingExecutor(CountDownLatch entered, CountDownLatch release) {
        ConnectorPipelineExecutor executor = mock(ConnectorPipelineExecutor.class);
        AtomicInteger calls = new AtomicInteger();
        when(executor.execute(any(CompiledConnectorPipeline.class), any(ConnectorExecutionRequest.class)))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    while (release.getCount() > 0) {
                        try {
                            release.await();
                        } catch (InterruptedException ignored) {
                            // Deliberately emulate a faulty plugin that ignores cancellation.
                        }
                    }
                    return success();
                });
        return executor;
    }

    private ThreadPoolExecutor draftPool(int workers, int queueCapacity) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "draft-isolation-test");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executors.add(executor);
        return executor;
    }

    private VendorConfigDTO config(int timeout) {
        VendorConfigDTO config = new VendorConfigDTO();
        config.setId(1L);
        config.setTimeout(timeout);
        return config;
    }

    private List<ConnectorTestPipelineStepDTO> steps() {
        ConnectorTestPipelineStepDTO step = new ConnectorTestPipelineStepDTO();
        step.setStageKey("transport");
        step.setCapability("TRANSPORT");
        step.setPluginId("test");
        step.setPluginVersion("1.0.0");
        step.setOrder(0);
        step.setEnabled(true);
        step.setConfig(Map.of());
        step.setConfigHash("a".repeat(64));
        return List.of(step);
    }

    private ConnectorExecutionResult success() {
        return new ConnectorExecutionResult(TransportStatus.SUCCESS, BusinessStatus.SUCCESS, Map.of("ok", true),
                null, null, null, BillingSignal.ELIGIBLE, CacheSignal.CACHEABLE,
                RequestDeliveryState.SENT, "test", "1.0.0", "draft", "a".repeat(64), List.of());
    }
}
