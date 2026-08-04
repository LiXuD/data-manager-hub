package com.dataplatform.access.connector.service;

import com.dataplatform.api.Result;
import com.dataplatform.access.connector.api.dto.ConnectorTestPipelineStepDTO;
import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.common.plugin.runtime.CompiledConnectorPipeline;
import com.dataplatform.common.plugin.runtime.ConnectorExecutionRequest;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutor;
import com.dataplatform.common.plugin.runtime.ConnectorStageDefinition;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.access.connector.runtime.ScopedConnectorSecretResolver;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import com.dataplatform.masterdata.connector.api.feign.VendorConnectorInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorRuntimeSecurityDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.TransportStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Compiles fixed Masterdata snapshots and executes them with pinned plugin handles. */
@Service
public class DefaultConnectorVendorExecutor implements ConnectorVendorExecutor, AutoCloseable {

    private final VendorConnectorInternalFeignClient connectorClient;
    private final VendorSecurityInternalFeignClient securityClient;
    private final VendorConfigInternalFeignClient vendorConfigClient;
    private final PipelineCompiler compiler;
    private final ConnectorPipelineExecutor executor;
    private final ScopedConnectorSecretResolver secretResolver;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ConnectorRuntimeProperties runtimeProperties;
    private final ConcurrentHashMap<Long, CachedPipeline> pipelines = new ConcurrentHashMap<>();

    public DefaultConnectorVendorExecutor(
            VendorConnectorInternalFeignClient connectorClient,
            VendorSecurityInternalFeignClient securityClient,
            VendorConfigInternalFeignClient vendorConfigClient,
            PipelineCompiler compiler,
            ConnectorPipelineExecutor executor,
            ScopedConnectorSecretResolver secretResolver,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            Clock connectorClock,
            ConnectorRuntimeProperties runtimeProperties) {
        this.connectorClient = connectorClient;
        this.securityClient = securityClient;
        this.vendorConfigClient = vendorConfigClient;
        this.compiler = compiler;
        this.executor = executor;
        this.secretResolver = secretResolver;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.clock = connectorClock;
        this.runtimeProperties = runtimeProperties;
    }

    /** Executes an unpublished draft once. It deliberately bypasses fallback, billing, cache and call records. */
    public ConnectorExecutionResult executeDraft(
            VendorConfigDTO config,
            String vendorCode,
            List<ConnectorTestPipelineStepDTO> steps,
            Map<String, Object> parameters) {
        if (config == null || config.getId() == null || steps == null || steps.isEmpty()) {
            return failure(ErrorCategory.CONFIGURATION_ERROR, "DRAFT_CONFIGURATION_MISSING",
                    "Connector test configuration is incomplete", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        }
        Map<String, Object> safeParameters = parameters != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(parameters)) : Map.of();
        try {
            Map<String, String> secrets = resolveSecrets(config, vendorCode);
            ConnectorPipelineDefinition definition = draftDefinition(steps);
            return secretResolver.withSecrets(secrets, () -> {
                try (CompiledConnectorPipeline pipeline = compiler.compile(definition)) {
                    long configured = timeout(config);
                    long testLimit = Math.max(100L, runtimeProperties.getTestTimeoutMs());
                    Instant deadline = clock.instant().plusMillis(Math.min(configured, testLimit));
                    ConnectorExecutionRequest request = new ConnectorExecutionRequest(
                            safeParameters, vendorCode, deadline,
                            () -> Thread.currentThread().isInterrupted());
                    return executor.execute(pipeline, request);
                }
            });
        } catch (IllegalStateException ex) {
            ConnectorException connectorException = connectorException(ex);
            if (connectorException != null) {
                return failure(connectorException.category(), connectorException.errorCode(),
                        connectorException.safeMessage(), connectorException.deliveryState(),
                        null, null, "draft", null);
            }
            return failure(ErrorCategory.PLUGIN_NOT_READY, "PLUGIN_NOT_READY",
                    "Connector plugin is not ready", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        } catch (IllegalArgumentException ex) {
            return failure(ErrorCategory.CONFIGURATION_ERROR, "CONNECTOR_CONFIGURATION_ERROR",
                    "Connector configuration is invalid", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        } catch (Exception ex) {
            return failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "CONNECTOR_TEST_RUNTIME_ERROR",
                    "Connector test runtime failed", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        }
    }

    private ConnectorException connectorException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectorException connectorException) return connectorException;
            current = current.getCause();
        }
        return null;
    }

    @Override
    public ConnectorExecutionResult execute(
            VendorConfigDTO config,
            String vendorCode,
            String dataTypeCode,
            Map<String, Object> parameters) {
        if (config == null || config.getId() == null) {
            return failure(ErrorCategory.CONFIGURATION_ERROR, "VENDOR_CONFIG_ID_MISSING",
                    "Vendor connector configuration is incomplete", RequestDeliveryState.NOT_SENT,
                    null, null, null, null);
        }
        try {
            VendorConnectorRuntimeSnapshotDTO snapshot = requireSnapshot(config.getId());
            Map<String, String> secrets = resolveSecrets(config, vendorCode);
            CompiledConnectorPipeline pipeline = secretResolver.withSecrets(secrets,
                    () -> pipeline(config.getId(), snapshot));
            Instant deadline = clock.instant().plusMillis(timeout(config));
            ConnectorExecutionRequest request = new ConnectorExecutionRequest(
                    parameters, vendorCode, deadline, () -> Thread.currentThread().isInterrupted());
            ConnectorExecutionResult result = executeWithRetry(config, pipeline, request, secrets, parameters);
            recordMetrics(result);
            return result;
        } catch (IllegalStateException ex) {
            return failure(ErrorCategory.PLUGIN_NOT_READY, "PLUGIN_NOT_READY",
                    "Connector plugin is not ready", RequestDeliveryState.NOT_SENT,
                    null, null, null, null);
        } catch (IllegalArgumentException ex) {
            return failure(ErrorCategory.CONFIGURATION_ERROR, "CONNECTOR_CONFIGURATION_ERROR",
                    "Connector configuration is invalid", RequestDeliveryState.NOT_SENT,
                    null, null, null, null);
        } catch (RuntimeException ex) {
            return failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "CONNECTOR_RUNTIME_ERROR",
                    "Connector runtime failed", RequestDeliveryState.NOT_SENT,
                    null, null, null, null);
        }
    }

    private ConnectorExecutionResult executeWithRetry(
            VendorConfigDTO config,
            CompiledConnectorPipeline pipeline,
            ConnectorExecutionRequest request,
            Map<String, String> secrets,
            Map<String, Object> parameters) {
        int maxRetries = Math.max(0, Math.min(config.getRetryCount() != null ? config.getRetryCount() : 0, 10));
        // Fail closed for unsafe methods until both plugin-declared idempotency and a platform key
        // are available as an explicit runtime signal. A caller parameter alone is not sufficient.
        boolean retryPermitted = safeMethod(config.getMethod());
        ConnectorExecutionResult result;
        int attempt = 0;
        do {
            result = secretResolver.withSecrets(secrets, () -> executor.execute(pipeline, request));
            if (!retryPermitted || !retryable(result) || attempt >= maxRetries
                    || !clock.instant().isBefore(request.deadline())) {
                return result;
            }
            attempt++;
        } while (true);
    }

    private synchronized CompiledConnectorPipeline pipeline(
            Long vendorConfigId, VendorConnectorRuntimeSnapshotDTO snapshot) throws Exception {
        CachedPipeline cached = pipelines.get(vendorConfigId);
        if (cached != null && cached.snapshotHash().equals(snapshot.snapshotHash())) {
            return cached.pipeline();
        }
        ConnectorPipelineDefinition definition = new ConnectorPipelineDefinition(
                String.valueOf(snapshot.versionNo()), snapshot.snapshotHash(),
                snapshot.pipelineSnapshot().stream().map(this::stage).toList());
        CompiledConnectorPipeline compiled = compiler.compile(definition);
        CachedPipeline previous = pipelines.put(vendorConfigId,
                new CachedPipeline(snapshot.snapshotHash(), compiled));
        if (previous != null) {
            previous.pipeline().close();
        }
        return compiled;
    }

    private ConnectorStageDefinition stage(ConnectorPipelineStepDTO step) {
        return new ConnectorStageDefinition(
                step.stageKey(), StageCapability.valueOf(step.capability()), step.pluginId(),
                step.pluginVersion(), step.order(), !Boolean.FALSE.equals(step.enabled()),
                objectMapper.valueToTree(step.config()), step.configHash());
    }

    private ConnectorPipelineDefinition draftDefinition(List<ConnectorTestPipelineStepDTO> steps) {
        List<ConnectorStageDefinition> definitions = steps.stream().map(step ->
                new ConnectorStageDefinition(step.getStageKey(),
                        StageCapability.valueOf(step.getCapability()), step.getPluginId(),
                        step.getPluginVersion(), step.getOrder(), !Boolean.FALSE.equals(step.getEnabled()),
                        objectMapper.valueToTree(step.getConfig()), step.getConfigHash())).toList();
        String snapshotHash = compiler.sha256(objectMapper.valueToTree(steps));
        return new ConnectorPipelineDefinition("draft", snapshotHash, definitions);
    }

    private VendorConnectorRuntimeSnapshotDTO requireSnapshot(Long vendorConfigId) {
        Result<VendorConnectorRuntimeSnapshotDTO> response = connectorClient.getRuntimeSnapshot(vendorConfigId);
        VendorConnectorRuntimeSnapshotDTO snapshot = response != null ? response.getData() : null;
        if (snapshot == null || !"ACTIVE".equals(snapshot.status())
                || snapshot.versionNo() == null || snapshot.snapshotHash() == null
                || snapshot.pipelineSnapshot() == null || snapshot.pipelineSnapshot().isEmpty()) {
            throw new IllegalArgumentException("Active connector snapshot is unavailable");
        }
        return snapshot;
    }

    private Map<String, String> resolveSecrets(VendorConfigDTO config, String vendorCode) {
        Map<String, String> secrets = new LinkedHashMap<>();
        Result<String> vendorSecret = vendorConfigClient.getSecretKey(vendorCode);
        if (vendorSecret != null && vendorSecret.getData() != null) {
            secrets.put("vendor.secretKey", vendorSecret.getData());
        }
        Result<VendorRuntimeSecurityDTO> securityResponse = securityClient.getRuntimeSecurity(config.getId());
        VendorRuntimeSecurityDTO security = securityResponse != null ? securityResponse.getData() : null;
        if (security != null && security.getResolvedSecrets() != null) {
            secrets.putAll(security.getResolvedSecrets());
        }
        return Map.copyOf(secrets);
    }

    private boolean retryable(ConnectorExecutionResult result) {
        return result.errorCategory() == ErrorCategory.TRANSPORT_TIMEOUT
                || result.errorCategory() == ErrorCategory.TRANSPORT_CONNECTION_ERROR;
    }

    private boolean safeMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private long timeout(VendorConfigDTO config) {
        int configured = config.getTimeout() != null ? config.getTimeout() : 30_000;
        return Math.max(100, Math.min(configured, 60_000));
    }

    private void recordMetrics(ConnectorExecutionResult result) {
        String pluginId = result.pluginId() != null ? result.pluginId() : "unknown";
        String pluginVersion = result.pluginVersion() != null ? result.pluginVersion() : "unknown";
        String errorCategory = result.errorCategory() != null ? result.errorCategory().name() : "NONE";
        meterRegistry.counter("connector_execution_total",
                "pluginId", pluginId, "pluginVersion", pluginVersion,
                "errorCategory", errorCategory).increment();
        if (result.errorCategory() != null) {
            meterRegistry.counter("connector_execution_errors_total",
                    "pluginId", pluginId, "pluginVersion", pluginVersion,
                    "errorCategory", errorCategory).increment();
        }
        result.stageTimings().forEach(timing -> meterRegistry.timer("connector_stage_duration_seconds",
                "pluginId", timing.pluginId(), "pluginVersion", timing.pluginVersion(),
                "capability", timing.capability().name())
                .record(timing.duration()));
    }

    private ConnectorExecutionResult failure(
            ErrorCategory category, String code, String message, RequestDeliveryState delivery,
            String pluginId, String pluginVersion, String pipelineVersion, String snapshotHash) {
        return new ConnectorExecutionResult(TransportStatus.FAILED, BusinessStatus.UNKNOWN, Map.of(),
                category, code, message, BillingSignal.UNKNOWN, CacheSignal.NOT_CACHEABLE,
                delivery, pluginId, pluginVersion, pipelineVersion, snapshotHash, List.of());
    }

    @Override
    public void close() {
        pipelines.values().forEach(item -> item.pipeline().close());
        pipelines.clear();
    }

    private record CachedPipeline(String snapshotHash, CompiledConnectorPipeline pipeline) {
    }
}
