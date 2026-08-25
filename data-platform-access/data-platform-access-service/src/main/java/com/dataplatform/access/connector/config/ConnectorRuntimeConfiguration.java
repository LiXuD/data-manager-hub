package com.dataplatform.access.connector.config;

import com.dataplatform.access.connector.artifact.ConnectorPluginArtifactCache;
import com.dataplatform.access.connector.runtime.ConfiguredTrustedSigningKeyProvider;
import com.dataplatform.access.connector.runtime.DefaultConnectorPluginRuntimeOperations;
import com.dataplatform.access.connector.runtime.MicrometerPluginMetricRecorder;
import com.dataplatform.access.connector.runtime.ManifestScopedPluginContextFactory;
import com.dataplatform.access.connector.runtime.MasterdataConnectorPluginMetadataResolver;
import com.dataplatform.access.connector.runtime.ScopedConnectorSecretResolver;
import com.dataplatform.access.connector.service.ConnectorPluginRuntimeOperations;
import com.dataplatform.common.plugin.artifact.PluginArtifactVerifier;
import com.dataplatform.common.plugin.artifact.TrustedSigningKeyProvider;
import com.dataplatform.common.plugin.legacy.LegacyHttpConnectorPlugin;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutor;
import com.dataplatform.common.plugin.runtime.ConnectorPluginRegistry;
import com.dataplatform.common.plugin.runtime.DefaultManagedTaskExecutor;
import com.dataplatform.common.plugin.runtime.DefaultPluginContext;
import com.dataplatform.common.plugin.runtime.DefaultPluginValidationContext;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorPlugin;
import com.dataplatform.common.plugin.runtime.JacksonObjectCodec;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorPlugin;
import com.dataplatform.common.plugin.runtime.PluginLoader;
import com.dataplatform.common.plugin.runtime.PluginContextFactory;
import com.dataplatform.common.plugin.runtime.PluginRuntimeManager;
import com.dataplatform.common.plugin.runtime.Slf4jPluginLogger;
import com.dataplatform.common.plugin.transport.NetworkPolicy;
import com.dataplatform.common.plugin.transport.OkHttpManagedTransport;
import com.dataplatform.plugin.spi.ManagedHttpTransport;
import com.dataplatform.plugin.spi.ManagedTaskExecutor;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ResourceLoader;

@Configuration
@EnableConfigurationProperties(ConnectorRuntimeProperties.class)
public class ConnectorRuntimeConfiguration {

    @Bean
    public Clock connectorClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PluginLogger connectorPluginLogger() {
        return new Slf4jPluginLogger(LoggerFactory.getLogger("connector-plugin"));
    }

    @Bean
    public PluginMetricRecorder connectorPluginMetricRecorder(MeterRegistry meterRegistry) {
        return new MicrometerPluginMetricRecorder(meterRegistry);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService connectorPluginExecutorService() {
        int workers = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256), runnable -> {
                    Thread thread = new Thread(runnable,
                            "connector-plugin-task-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService connectorDraftTestExecutorService() {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), runnable -> {
                    Thread thread = new Thread(runnable,
                            "connector-draft-test-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public ManagedTaskExecutor connectorManagedTaskExecutor(
            @Qualifier("connectorPluginExecutorService") ExecutorService executorService) {
        return new DefaultManagedTaskExecutor(executorService);
    }

    @Bean
    public OkHttpClient connectorOkHttpClient() {
        return new OkHttpClient.Builder().build();
    }

    @Bean
    public ManagedHttpTransport connectorManagedHttpTransport(
            ConnectorRuntimeProperties properties,
            @Qualifier("connectorOkHttpClient") OkHttpClient httpClient) {
        Set<String> hosts = new LinkedHashSet<>(properties.getNetworkAllowedHosts());
        if (hosts.isEmpty()) {
            hosts.add("disabled.invalid");
        }
        NetworkPolicy policy = new NetworkPolicy(
                new LinkedHashSet<>(properties.getNetworkAllowedProtocols()), hosts,
                properties.isAllowPrivateNetworks(),
                Duration.ofMillis(properties.getMaxConnectTimeoutMs()),
                Duration.ofMillis(properties.getMaxReadTimeoutMs()),
                Duration.ofMillis(properties.getMaxTotalTimeoutMs()),
                properties.getMaxResponseBytes());
        return new OkHttpManagedTransport(httpClient, policy);
    }

    @Bean
    public PluginContextFactory connectorPluginContextFactory(
            ConnectorRuntimeProperties properties,
            @Qualifier("connectorOkHttpClient") OkHttpClient httpClient,
            ScopedConnectorSecretResolver secretResolver,
            Clock connectorClock,
            PluginLogger connectorPluginLogger,
            PluginMetricRecorder connectorPluginMetricRecorder,
            ObjectMapper objectMapper,
            ManagedTaskExecutor connectorManagedTaskExecutor) {
        return new ManifestScopedPluginContextFactory(properties, httpClient, secretResolver,
                connectorClock, connectorPluginLogger, connectorPluginMetricRecorder,
                objectMapper, connectorManagedTaskExecutor);
    }

    @Bean
    public PluginContext connectorPluginContext(
            ManagedHttpTransport transport,
            ScopedConnectorSecretResolver secretResolver,
            Clock connectorClock,
            PluginLogger connectorPluginLogger,
            PluginMetricRecorder connectorPluginMetricRecorder,
            ObjectMapper objectMapper,
            ManagedTaskExecutor connectorManagedTaskExecutor) {
        return new DefaultPluginContext(transport, secretResolver, connectorClock,
                connectorPluginLogger, connectorPluginMetricRecorder,
                new JacksonObjectCodec(objectMapper), connectorManagedTaskExecutor);
    }

    @Bean
    public TrustedSigningKeyProvider connectorTrustedSigningKeyProvider(
            ConnectorRuntimeProperties properties, ResourceLoader resourceLoader) {
        return new ConfiguredTrustedSigningKeyProvider(properties, resourceLoader);
    }

    @Bean
    public ConnectorPluginRegistry connectorPluginRegistry() {
        return new ConnectorPluginRegistry();
    }

    @Bean
    public PluginRuntimeManager connectorPluginRuntimeManager(
            ObjectMapper objectMapper,
            TrustedSigningKeyProvider keyProvider,
            PluginContext pluginContext,
            PluginContextFactory pluginContextFactory,
            ConnectorPluginRegistry registry,
            ConnectorRuntimeProperties properties) {
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(objectMapper, keyProvider);
        PluginLoader loader = new PluginLoader(
                pluginContextFactory, properties.getHostVersion(), "1.1");
        PluginRuntimeManager manager = new PluginRuntimeManager(verifier, loader, registry);
        try {
            LegacyHttpConnectorPlugin legacy = new LegacyHttpConnectorPlugin();
            legacy.initialize(pluginContext);
            if (!legacy.selfTest().successful()) {
                throw new IllegalStateException("Built-in legacy-http self-test failed");
            }
            manager.registerBuiltIn(new PluginRuntimeManager.ConnectorPluginRegistration(legacy, pluginContext));
            PlatformCoreConnectorPlugin platformCore = new PlatformCoreConnectorPlugin();
            platformCore.initialize(pluginContext);
            if (!platformCore.selfTest().successful()) {
                throw new IllegalStateException("Built-in platform-core self-test failed");
            }
            manager.registerBuiltIn(
                    new PluginRuntimeManager.ConnectorPluginRegistration(platformCore, pluginContext));
            GenericHttpConnectorPlugin genericHttp = new GenericHttpConnectorPlugin();
            genericHttp.initialize(pluginContext);
            if (!genericHttp.selfTest().successful()) {
                throw new IllegalStateException("Built-in generic-http self-test failed");
            }
            manager.registerBuiltIn(
                    new PluginRuntimeManager.ConnectorPluginRegistration(genericHttp, pluginContext));
        } catch (Exception ex) {
            manager.close();
            throw new IllegalStateException("Unable to initialize built-in connector runtime", ex);
        }
        return manager;
    }

    @Bean
    public ConnectorPluginRuntimeOperations connectorPluginRuntimeOperations(
            ConnectorPluginArtifactCache artifactCache,
            PluginRuntimeManager runtimeManager,
            MasterdataConnectorPluginMetadataResolver metadataResolver) {
        return new DefaultConnectorPluginRuntimeOperations(artifactCache, runtimeManager, metadataResolver);
    }

    @Bean
    public PluginValidationContext connectorPluginValidationContext(
            Clock connectorClock,
            ConnectorRuntimeProperties properties) {
        return new DefaultPluginValidationContext(
                connectorClock, properties.getHostVersion(),
                ref -> ref != null && !ref.isBlank() && ref.length() <= 256);
    }

    @Bean
    public PipelineCompiler connectorPipelineCompiler(
            ConnectorPluginRegistry registry,
            PluginValidationContext validationContext,
            ObjectMapper objectMapper,
            MasterdataConnectorPluginMetadataResolver metadataResolver) {
        return new PipelineCompiler(registry, validationContext, objectMapper, metadataResolver);
    }

    @Bean
    public ConnectorPipelineExecutor connectorPipelineExecutor(
            Clock connectorClock,
            PluginLogger connectorPluginLogger,
            PluginMetricRecorder connectorPluginMetricRecorder,
            ScopedConnectorSecretResolver secretResolver) {
        return new ConnectorPipelineExecutor(
                connectorClock, connectorPluginLogger, connectorPluginMetricRecorder, secretResolver);
    }
}
