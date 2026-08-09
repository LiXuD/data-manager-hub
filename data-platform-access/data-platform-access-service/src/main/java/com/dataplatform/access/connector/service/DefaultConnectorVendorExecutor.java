package com.dataplatform.access.connector.service;

import com.dataplatform.api.Result;
import com.dataplatform.access.connector.api.dto.ConnectorTestPipelineStepDTO;
import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.common.plugin.runtime.CompiledConnectorPipeline;
import com.dataplatform.common.plugin.runtime.ConnectorExecutionRequest;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutor;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineExecutionOutcome;
import com.dataplatform.common.plugin.runtime.ConnectorSnapshotIntegrity;
import com.dataplatform.common.plugin.runtime.ConnectorStageDefinition;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.access.connector.runtime.ScopedConnectorSecretResolver;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import com.dataplatform.masterdata.connector.api.feign.VendorConnectorInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorRuntimeSecurityDTO;
import com.dataplatform.masterdata.vendor.api.dto.ConnectorSecretResolutionDTO;
import com.dataplatform.masterdata.vendor.api.dto.ConnectorSecretResolutionRequestDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ConnectorErrorPolicy;
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
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Compiles fixed Masterdata snapshots and executes them with pinned plugin handles. */
@Service
public class DefaultConnectorVendorExecutor implements ConnectorVendorExecutor, ConnectorPipelineRetirement,
        AutoCloseable {

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
    private final ExecutorService draftTestExecutor;
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
            ConnectorRuntimeProperties runtimeProperties,
            @Qualifier("connectorDraftTestExecutorService") ExecutorService draftTestExecutor) {
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
        this.draftTestExecutor = draftTestExecutor;
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
        long testLimit = Math.max(100L, runtimeProperties.getTestTimeoutMs());
        long deadlineMillis = Math.min(timeout(config), testLimit);
        Future<ConnectorExecutionResult> future;
        try {
            future = draftTestExecutor.submit(
                    () -> executeDraftWorker(config, vendorCode, steps, safeParameters, deadlineMillis));
        } catch (RejectedExecutionException exception) {
            return failure(ErrorCategory.PLUGIN_NOT_READY, "CONNECTOR_TEST_EXECUTOR_SATURATED",
                    "Connector test capacity is temporarily exhausted", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        }
        try {
            return future.get(deadlineMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return failure(ErrorCategory.TRANSPORT_TIMEOUT, "CONNECTOR_TEST_TIMEOUT",
                    "Connector test exceeded its deadline", RequestDeliveryState.MAYBE_SENT,
                    null, null, "draft", null);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "CONNECTOR_TEST_INTERRUPTED",
                    "Connector test was interrupted", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        } catch (ExecutionException exception) {
            return failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "CONNECTOR_TEST_RUNTIME_ERROR",
                    "Connector test runtime failed", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        }
    }

    private ConnectorExecutionResult executeDraftWorker(
            VendorConfigDTO config,
            String vendorCode,
            List<ConnectorTestPipelineStepDTO> steps,
            Map<String, Object> parameters,
            long deadlineMillis) {
        try {
            ConnectorPipelineDefinition definition = draftDefinition(steps);
            return secretResolver.withSecretProvider(refs -> resolveSecrets(config.getId(), refs), () -> {
                try {
                    try (CompiledConnectorPipeline pipeline = compiler.compile(definition)) {
                        ConnectorExecutionRequest request = new ConnectorExecutionRequest(
                                parameters, vendorCode, clock.instant().plusMillis(deadlineMillis),
                                () -> Thread.currentThread().isInterrupted());
                        return executor.execute(pipeline, request);
                    }
                } catch (ConnectorException exception) {
                    return failure(exception.category(), exception.errorCode(),
                            exception.safeMessage(), exception.deliveryState(),
                            null, null, "draft", null);
                } catch (IllegalStateException exception) {
                    ConnectorException connectorException = connectorException(exception);
                    if (connectorException != null) {
                        return failure(connectorException.category(), connectorException.errorCode(),
                                connectorException.safeMessage(), connectorException.deliveryState(),
                                null, null, "draft", null);
                    }
                    return failure(ErrorCategory.PLUGIN_NOT_READY, "PLUGIN_NOT_READY",
                            "Connector plugin is not ready", RequestDeliveryState.NOT_SENT,
                            null, null, "draft", null);
                } catch (IllegalArgumentException exception) {
                    return failure(ErrorCategory.CONFIGURATION_ERROR, "CONNECTOR_CONFIGURATION_ERROR",
                            "Connector configuration is invalid", RequestDeliveryState.NOT_SENT,
                            null, null, "draft", null);
                } catch (Exception exception) {
                    return failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "CONNECTOR_TEST_RUNTIME_ERROR",
                            "Connector test runtime failed", RequestDeliveryState.NOT_SENT,
                            null, null, "draft", null);
                }
            });
        } catch (IllegalStateException exception) {
            return failure(ErrorCategory.PLUGIN_NOT_READY, "PLUGIN_NOT_READY",
                    "Connector plugin is not ready", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        } catch (IllegalArgumentException exception) {
            return failure(ErrorCategory.CONFIGURATION_ERROR, "CONNECTOR_CONFIGURATION_ERROR",
                    "Connector configuration is invalid", RequestDeliveryState.NOT_SENT,
                    null, null, "draft", null);
        } catch (Exception exception) {
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
            try (CompiledConnectorPipeline.RequestLease pipelineLease = acquirePipeline(config.getId(), snapshot)) {
                Instant deadline = clock.instant().plusMillis(timeout(config));
                ConnectorExecutionRequest request = new ConnectorExecutionRequest(
                        parameters, vendorCode, deadline, () -> Thread.currentThread().isInterrupted());
                ConnectorExecutionResult result = executeWithRetry(
                        config, pipelineLease, request, parameters);
                recordMetrics(result);
                return result;
            }
        } catch (ConnectorException ex) {
            return failure(ex.category(), ex.errorCode(), ex.safeMessage(), ex.deliveryState(),
                    null, null, null, null);
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
            CompiledConnectorPipeline.RequestLease pipelineLease,
            ConnectorExecutionRequest request,
            Map<String, Object> parameters) {
        int maxRetries = Math.max(0, Math.min(config.getRetryCount() != null ? config.getRetryCount() : 0, 10));
        // Fail closed for unsafe methods until both plugin-declared idempotency and a platform key
        // are available as an explicit runtime signal. A caller parameter alone is not sufficient.
        ConnectorExecutionResult result;
        int attempt = 0;
        do {
            ConnectorPipelineExecutionOutcome outcome = secretResolver.withSecretProvider(
                    refs -> resolveSecrets(config.getId(), refs),
                    () -> executor.executeWithOutcome(pipelineLease, request));
            result = outcome.result();
            if (!outcome.requestRetryPermitted() || !retryable(result) || attempt >= maxRetries
                    || !clock.instant().isBefore(request.deadline())) {
                return result;
            }
            attempt++;
        } while (true);
    }

    private synchronized CompiledConnectorPipeline.RequestLease acquirePipeline(
            Long vendorConfigId, VendorConnectorRuntimeSnapshotDTO snapshot) throws ConnectorException {
        CachedPipeline cached = pipelines.get(vendorConfigId);
        if (cached != null && cached.snapshotHash().equals(snapshot.snapshotHash())) {
            return cached.pipeline().acquire();
        }
        ConnectorPipelineDefinition definition = new ConnectorPipelineDefinition(
                String.valueOf(snapshot.versionNo()), snapshot.snapshotHash(),
                snapshot.hashAlgorithm(), snapshot.integrityHash(),
                snapshot.pipelineSnapshot().stream().map(this::stage).toList());
        CompiledConnectorPipeline compiled = compiler.compile(definition);
        CachedPipeline previous = pipelines.put(vendorConfigId,
                new CachedPipeline(snapshot.snapshotHash(), compiled));
        CompiledConnectorPipeline.RequestLease requestLease = compiled.acquire();
        if (previous != null) {
            previous.pipeline().close();
        }
        return requestLease;
    }

    private ConnectorStageDefinition stage(ConnectorPipelineStepDTO step) {
        return new ConnectorStageDefinition(
                step.stageKey(), StageCapability.valueOf(step.capability()), step.pluginId(),
                step.pluginVersion(), step.order(), !Boolean.FALSE.equals(step.enabled()),
                objectMapper.valueToTree(step.config()), step.configHash(), step.artifactSha256(),
                step.manifestHash(), step.schemaHash());
    }

    private ConnectorPipelineDefinition draftDefinition(List<ConnectorTestPipelineStepDTO> steps) {
        List<ConnectorStageDefinition> definitions = steps.stream().map(step ->
                new ConnectorStageDefinition(step.getStageKey(),
                        StageCapability.valueOf(step.getCapability()), step.getPluginId(),
                        step.getPluginVersion(), step.getOrder(), !Boolean.FALSE.equals(step.getEnabled()),
                        objectMapper.valueToTree(step.getConfig()), step.getConfigHash(),
                        step.getArtifactSha256(), step.getManifestHash(), step.getSchemaHash())).toList();
        String snapshotHash = ConnectorSnapshotIntegrity.v2SnapshotHash(objectMapper, definitions);
        return new ConnectorPipelineDefinition("draft", snapshotHash,
                ConnectorPipelineDefinition.V2_EMBEDDED, snapshotHash, definitions);
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

    private Map<String, String> resolveSecrets(Long vendorConfigId, Set<String> refs) {
        if (refs == null || refs.isEmpty()) return Map.of();
        ConnectorSecretResolutionRequestDTO request = new ConnectorSecretResolutionRequestDTO();
        request.setVendorConfigId(vendorConfigId);
        request.setSecretRefs(refs);
        Result<ConnectorSecretResolutionDTO> response = securityClient.resolveConnectorSecrets(request);
        ConnectorSecretResolutionDTO resolution = response != null ? response.getData() : null;
        Map<String, String> resolved = resolution == null ? null : resolution.getResolvedSecrets();
        if (resolved == null || !resolved.keySet().equals(refs)) {
            throw new IllegalArgumentException("Connector stage secret references are unavailable");
        }
        return Map.copyOf(resolved);
    }

    private boolean retryable(ConnectorExecutionResult result) {
        return result.errorCategory() != null
                && ConnectorErrorPolicy.forCategory(result.errorCategory()).retryAllowed();
    }

    @Override
    public void retirePipelinesUsing(String pluginId, String pluginVersion) {
        pipelines.forEach((vendorConfigId, cached) -> {
            boolean usesVersion = cached.pipeline().definition().stages().stream()
                    .anyMatch(stage -> pluginId.equals(stage.pluginId())
                            && pluginVersion.equals(stage.pluginVersion()));
            if (usesVersion && pipelines.remove(vendorConfigId, cached)) {
                cached.pipeline().close();
            }
        });
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
                category, code, com.dataplatform.common.plugin.runtime.ConnectorSafeMessageSanitizer.sanitize(
                        message, secretResolver.sensitiveValues()), BillingSignal.UNKNOWN, CacheSignal.NOT_CACHEABLE,
                delivery, pluginId, pluginVersion, pipelineVersion, snapshotHash, List.of());
    }

    @Override
    public synchronized void close() {
        pipelines.values().forEach(item -> item.pipeline().close());
        pipelines.clear();
    }

    private record CachedPipeline(String snapshotHash, CompiledConnectorPipeline pipeline) {
    }
}
