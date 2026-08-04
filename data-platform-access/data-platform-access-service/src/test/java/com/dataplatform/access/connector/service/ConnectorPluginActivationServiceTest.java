package com.dataplatform.access.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.access.connector.entity.ConnectorPluginActivation;
import com.dataplatform.access.connector.mapper.ConnectorPluginActivationMapper;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.mock.env.MockEnvironment;

class ConnectorPluginActivationServiceTest {

    private ConnectorPluginActivationMapper mapper;
    private ConnectorPluginInternalFeignClient pluginClient;
    private ConnectorPluginRuntimeOperations runtime;
    private ConnectorPluginActivationService service;
    private AtomicReference<ConnectorPluginActivation> stored;
    private DiscoveryClient discoveryClient;
    private ConnectorRuntimeProperties properties;
    private ObjectProvider<Registration> registrationProvider;

    @BeforeEach
    void setUp() {
        mapper = mock(ConnectorPluginActivationMapper.class);
        pluginClient = mock(ConnectorPluginInternalFeignClient.class);
        runtime = mock(ConnectorPluginRuntimeOperations.class);
        discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances("data-platform-access")).thenReturn(List.of(
                new DefaultServiceInstance("access-1", "data-platform-access", "10.0.0.1", 8080, false)));

        properties = new ConnectorRuntimeProperties();
        properties.setInstanceId("access-e2e-1");
        properties.setHostVersion("test-host");

        registrationProvider = mock(ObjectProvider.class);
        Registration registration = mock(Registration.class);
        when(registration.getHost()).thenReturn("10.0.0.1");
        when(registration.getPort()).thenReturn(8080);
        when(registration.getMetadata()).thenReturn(Map.of());
        when(registrationProvider.orderedStream()).thenAnswer(invocation -> Stream.of(registration));

        stored = new AtomicReference<>();
        when(mapper.selectOne(any())).thenAnswer(invocation -> stored.get());
        when(mapper.selectList(any())).thenAnswer(invocation ->
                stored.get() == null ? List.of() : List.of(stored.get()));
        when(mapper.insert(any(ConnectorPluginActivation.class))).thenAnswer(invocation -> {
            ConnectorPluginActivation value = invocation.getArgument(0);
            value.setId(1L);
            stored.set(value);
            return 1;
        });
        when(mapper.updateById(any(ConnectorPluginActivation.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });

        PluginArtifactDescriptorDTO artifact = artifact();
        when(pluginClient.getArtifact("demo", "1.0.0")).thenReturn(Result.success(artifact));
        service = new ConnectorPluginActivationService(
                mapper, pluginClient, runtime, discoveryClient, properties,
                new SimpleMeterRegistry(), registrationProvider, new MockEnvironment());
    }

    @Test
    void stagesLocalPluginAndReportsReady() {
        var summary = service.requestStage("demo", "1.0.0");

        verify(runtime).preload(artifact());
        assertTrue(summary.getReady());
        assertEquals("READY", summary.getInstances().get(0).getState());
        assertEquals("test-host", summary.getInstances().get(0).getHostVersion());
        assertNotNull(summary.getInstances().get(0).getLoadedAt());
    }

    @Test
    void discoveryAddressRemainsALocalAliasWhenConfiguredInstanceIdDiffers() {
        var summary = service.requestStage("demo", "1.0.0");

        verify(runtime).preload(artifact());
        assertTrue(summary.getReady());
        assertEquals("10.0.0.1:8080", summary.getInstances().get(0).getServiceInstanceId());
    }

    @Test
    void localAliasesIncludeConfiguredIdAndDiscoveryAddress() {
        var aliases = service.localInstanceAliases();
        assertTrue(aliases.contains("access-e2e-1"), aliases::toString);
        assertTrue(aliases.contains("10.0.0.1:8080"), aliases::toString);
    }

    @Test
    void discoveryMetadataUsesStableConnectorInstanceId() {
        ServiceInstance discovered = mock(ServiceInstance.class);
        when(discovered.getHost()).thenReturn("10.0.0.1");
        when(discovered.getPort()).thenReturn(8080);
        when(discovered.getMetadata()).thenReturn(Map.of("connectorInstanceId", "access-e2e-1"));
        when(discoveryClient.getInstances("data-platform-access")).thenReturn(List.of(discovered));

        var summary = service.requestStage("demo", "1.0.0");

        assertTrue(summary.getReady());
        assertEquals("access-e2e-1", summary.getInstances().get(0).getServiceInstanceId());
    }

    @Test
    void concurrentPendingPollsSingleFlightPluginPreload() throws Exception {
        ConnectorPluginActivation activation = pendingActivation();
        stored.set(activation);
        AtomicBoolean loaded = new AtomicBoolean();
        AtomicInteger preloadCalls = new AtomicInteger();
        CountDownLatch preloadEntered = new CountDownLatch(1);
        CountDownLatch allowPreloadToFinish = new CountDownLatch(1);
        when(runtime.isLoaded("demo", "1.0.0")).thenAnswer(invocation -> loaded.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            preloadCalls.incrementAndGet();
            preloadEntered.countDown();
            assertTrue(allowPreloadToFinish.await(5, TimeUnit.SECONDS));
            loaded.set(true);
            return null;
        }).when(runtime).preload(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(service::processPendingActivations);
            Future<?> second = executor.submit(service::processPendingActivations);
            assertTrue(preloadEntered.await(5, TimeUnit.SECONDS));
            allowPreloadToFinish.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, preloadCalls.get());
        assertEquals("READY", stored.get().getState());
        assertNull(stored.get().getSafeErrorCode());
    }

    @Test
    void retryAfterFailureTransitionsToReadyAndClearsSafeErrorMetadata() {
        ConnectorPluginActivation activation = pendingActivation();
        stored.set(activation);
        AtomicInteger attempts = new AtomicInteger();
        when(runtime.isLoaded("demo", "1.0.0")).thenReturn(false);
        org.mockito.Mockito.doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("first attempt failed");
            }
            return null;
        }).when(runtime).preload(any());

        service.processPendingActivations();
        assertEquals("FAILED", stored.get().getState());
        assertNotNull(stored.get().getSafeErrorCode());
        assertNotNull(stored.get().getSafeErrorDigest());

        stored.get().setState("LOADING");
        service.processPendingActivations();

        assertEquals("READY", stored.get().getState());
        assertNull(stored.get().getSafeErrorCode());
        assertNull(stored.get().getSafeErrorDigest());
        verify(runtime, times(2)).preload(any());
    }

    @Test
    void failsClosedAndStoresOnlySafeErrorMetadata() {
        doThrow(new IllegalStateException("secret response body"))
                .when(runtime).preload(any());

        var summary = service.requestStage("demo", "1.0.0");

        assertFalse(summary.getReady());
        assertEquals("FAILED", summary.getInstances().get(0).getState());
        assertEquals("ILLEGALSTATEEXCEPTION", summary.getInstances().get(0).getSafeErrorCode());
        assertNotNull(summary.getInstances().get(0).getSafeErrorDigest());
        assertFalse(summary.getInstances().get(0).getSafeErrorDigest().contains("secret"));
    }

    @Test
    void readinessOpensAfterEmptyRequiredArtifactSync() {
        when(pluginClient.getRequiredArtifacts()).thenReturn(Result.success(List.of()));

        service.synchronizeRequiredArtifacts();

        assertTrue(service.isReady());
    }

    @Test
    void optionalStagingFailureDoesNotCloseReadiness() {
        when(pluginClient.getRequiredArtifacts()).thenReturn(Result.success(List.of()));
        service.synchronizeRequiredArtifacts();
        doThrow(new IllegalStateException("candidate failed")).when(runtime).preload(any());

        service.requestStage("demo", "1.0.0");

        assertTrue(service.isReady());
    }

    @Test
    void requiredArtifactMustBeReadyAndLoaded() {
        when(pluginClient.getRequiredArtifacts()).thenReturn(Result.success(List.of(artifact())));
        when(runtime.isLoaded("demo", "1.0.0")).thenReturn(true);

        service.synchronizeRequiredArtifacts();

        assertTrue(service.isReady());
    }

    @Test
    void releaseOfPreviouslyUnseenVersionPersistsArtifactHash() {
        when(runtime.release("demo", "1.0.0")).thenReturn(true);

        service.requestRelease("demo", "1.0.0");

        assertEquals("a".repeat(64), stored.get().getArtifactSha256());
        assertEquals("RELEASED", stored.get().getState());
    }

    private PluginArtifactDescriptorDTO artifact() {
        return new PluginArtifactDescriptorDTO(
                "demo", "1.0.0", "1.0", "example.Demo", "https://repo/demo.jar",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "signature", "key-1", "{}", "{}", List.of("TRANSPORT"),
                "{}", "1.0.0", "VERIFIED");
    }

    private ConnectorPluginActivation pendingActivation() {
        ConnectorPluginActivation activation = new ConnectorPluginActivation();
        activation.setId(1L);
        activation.setServiceInstanceId("access-e2e-1");
        activation.setPluginId("demo");
        activation.setPluginVersion("1.0.0");
        activation.setArtifactSha256("a".repeat(64));
        activation.setHostVersion("test-host");
        activation.setState("LOADING");
        activation.setCreatedAt(LocalDateTime.now());
        activation.setUpdatedAt(LocalDateTime.now());
        return activation;
    }
}
