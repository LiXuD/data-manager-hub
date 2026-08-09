package com.dataplatform.access.connector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.access.connector.entity.ConnectorPluginActivation;
import com.dataplatform.access.connector.mapper.ConnectorPluginActivationMapper;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Coordinates plugin preloading across Access instances through Access-owned activation facts. */
@Service
public class ConnectorPluginActivationService {

    private static final String ACCESS_SERVICE = "data-platform-access";
    private static final String CONNECTOR_INSTANCE_ID_METADATA = "connectorInstanceId";

    private final ConnectorPluginActivationMapper mapper;
    private final ConnectorPluginInternalFeignClient pluginClient;
    private final ConnectorPluginRuntimeOperations runtime;
    private final DiscoveryClient discoveryClient;
    private final ConnectorRuntimeProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<Registration> localRegistrationProvider;
    private final ObjectProvider<ConnectorPipelineRetirement> pipelineRetirementProvider;
    private final String localInstanceId;
    private final String localDiscoveryAddress;
    private final ConcurrentMap<String, Object> versionMonitors = new ConcurrentHashMap<>();
    private volatile boolean initialSyncCompleted;
    private volatile String initialSyncErrorCode;
    private volatile Set<String> requiredArtifactKeys = Set.of();

    public ConnectorPluginActivationService(
            ConnectorPluginActivationMapper mapper,
            ConnectorPluginInternalFeignClient pluginClient,
            ConnectorPluginRuntimeOperations runtime,
            DiscoveryClient discoveryClient,
            ConnectorRuntimeProperties properties,
            MeterRegistry meterRegistry,
            ObjectProvider<Registration> localRegistrationProvider,
            ObjectProvider<ConnectorPipelineRetirement> pipelineRetirementProvider,
            Environment environment) {
        this.mapper = mapper;
        this.pluginClient = pluginClient;
        this.runtime = runtime;
        this.discoveryClient = discoveryClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.localRegistrationProvider = localRegistrationProvider;
        this.pipelineRetirementProvider = pipelineRetirementProvider;
        this.localInstanceId = resolveLocalInstanceId(properties, environment);
        this.localDiscoveryAddress = resolveLocalDiscoveryAddress(environment);
        meterRegistry.gauge("connector_plugin_active_versions", runtime,
                ConnectorPluginRuntimeOperations::loadedVersionCount);
        meterRegistry.gauge("connector_plugin_classloaders", runtime,
                ConnectorPluginRuntimeOperations::isolatedClassLoaderCount);
    }

    public ConnectorPluginActivationSummaryDTO requestStage(String pluginId, String pluginVersion) {
        validateCoordinates(pluginId, pluginVersion);
        PluginArtifactDescriptorDTO artifact = requireArtifact(pluginId, pluginVersion);
        LocalDateTime now = LocalDateTime.now();
        for (String instanceId : activeInstanceIds()) {
            ConnectorPluginActivation existing = find(instanceId, pluginId, pluginVersion);
            if (existing != null
                    && ConnectorActivationState.READY.name().equals(existing.getState())
                    && artifact.artifactSha256().equalsIgnoreCase(existing.getArtifactSha256())
                    && runtimeLoadedForLocal(existing)) {
                continue;
            }
            ConnectorPluginActivation activation = existing != null ? existing : new ConnectorPluginActivation();
            activation.setServiceInstanceId(instanceId);
            activation.setPluginId(pluginId);
            activation.setPluginVersion(pluginVersion);
            activation.setArtifactSha256(artifact.artifactSha256());
            activation.setHostVersion(properties.getHostVersion());
            activation.setState(ConnectorActivationState.LOADING.name());
            activation.setLoadedAt(null);
            activation.setLastHeartbeatAt(now);
            activation.setSafeErrorCode(null);
            activation.setSafeErrorDigest(null);
            save(activation, now);
        }
        processPendingActivations();
        return summary(pluginId, pluginVersion);
    }

    public ConnectorPluginActivationSummaryDTO requestRelease(String pluginId, String pluginVersion) {
        validateCoordinates(pluginId, pluginVersion);
        PluginArtifactDescriptorDTO artifact = requireArtifact(pluginId, pluginVersion);
        LocalDateTime now = LocalDateTime.now();
        for (String instanceId : activeInstanceIds()) {
            ConnectorPluginActivation activation = find(instanceId, pluginId, pluginVersion);
            if (activation == null) {
                activation = new ConnectorPluginActivation();
                activation.setServiceInstanceId(instanceId);
                activation.setPluginId(pluginId);
                activation.setPluginVersion(pluginVersion);
            activation.setHostVersion(properties.getHostVersion());
            activation.setArtifactSha256(artifact.artifactSha256());
            }
            activation.setState(ConnectorActivationState.RELEASING.name());
            activation.setLastHeartbeatAt(now);
            activation.setSafeErrorCode(null);
            activation.setSafeErrorDigest(null);
            save(activation, now);
        }
        processPendingActivations();
        return summary(pluginId, pluginVersion);
    }

    public ConnectorPluginActivationSummaryDTO summary(String pluginId, String pluginVersion) {
        validateCoordinates(pluginId, pluginVersion);
        List<ConnectorPluginActivation> records = mapper.selectList(new LambdaQueryWrapper<ConnectorPluginActivation>()
                .eq(ConnectorPluginActivation::getPluginId, pluginId)
                .eq(ConnectorPluginActivation::getPluginVersion, pluginVersion));
        List<ConnectorPluginActivationDTO> instances = new ArrayList<>();
        boolean ready = true;
        for (String instanceId : activeInstanceIds()) {
            ConnectorPluginActivation record = records.stream()
                    .filter(item -> instanceId.equals(item.getServiceInstanceId()))
                    .findFirst()
                    .orElse(null);
            if (record == null) {
                ConnectorPluginActivationDTO missing = new ConnectorPluginActivationDTO();
                missing.setServiceInstanceId(instanceId);
                missing.setPluginId(pluginId);
                missing.setPluginVersion(pluginVersion);
                missing.setState("MISSING");
                instances.add(missing);
                ready = false;
            } else {
                instances.add(toDto(record));
                ready &= ConnectorActivationState.READY.name().equals(record.getState());
            }
        }
        ConnectorPluginActivationSummaryDTO result = new ConnectorPluginActivationSummaryDTO();
        result.setPluginId(pluginId);
        result.setPluginVersion(pluginVersion);
        result.setReady(ready && !instances.isEmpty());
        result.setInstances(instances);
        return result;
    }

    /** Preloads all plugin versions referenced by active published connector bindings. */
    public void synchronizeRequiredArtifacts() {
        try {
            Result<List<PluginArtifactDescriptorDTO>> response = pluginClient.getRequiredArtifacts();
            if (response == null || response.getData() == null) {
                String message = response != null ? response.getMsg() : null;
                if (message != null && message.contains("ACTIVE_CONNECTOR_BINDING_INVALID")) {
                    throw new IllegalStateException("ACTIVE_CONNECTOR_BINDING_INVALID");
                }
                throw new IllegalStateException("REQUIRED_PLUGIN_ARTIFACTS_UNAVAILABLE");
            }
            Set<String> required = new LinkedHashSet<>();
            for (PluginArtifactDescriptorDTO artifact : response.getData()) {
                required.add(key(artifact.pluginId(), artifact.version()));
                requestStage(artifact.pluginId(), artifact.version());
            }
            requiredArtifactKeys = Set.copyOf(required);
            initialSyncCompleted = true;
            initialSyncErrorCode = null;
        } catch (RuntimeException ex) {
            initialSyncCompleted = false;
            initialSyncErrorCode = safeErrorCode(ex);
            throw ex;
        }
    }

    @Scheduled(fixedDelayString = "${connector.runtime.activation-poll-interval-ms:2000}")
    public void processPendingActivations() {
        List<ConnectorPluginActivation> pending = mapper.selectList(
                new LambdaQueryWrapper<ConnectorPluginActivation>()
                        .in(ConnectorPluginActivation::getServiceInstanceId, localInstanceAliases())
                        .in(ConnectorPluginActivation::getState,
                                ConnectorActivationState.LOADING.name(),
                                ConnectorActivationState.RELEASING.name()));
        for (ConnectorPluginActivation activation : pending) {
            if (ConnectorActivationState.LOADING.name().equals(activation.getState())) {
                preload(activation);
            } else {
                release(activation);
            }
        }
    }

    @Scheduled(fixedDelayString = "${connector.runtime.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        mapper.update(null, new LambdaUpdateWrapper<ConnectorPluginActivation>()
                .in(ConnectorPluginActivation::getServiceInstanceId, localInstanceAliases())
                .ne(ConnectorPluginActivation::getState, ConnectorActivationState.RELEASED.name())
                .set(ConnectorPluginActivation::getLastHeartbeatAt, LocalDateTime.now())
                .set(ConnectorPluginActivation::getUpdatedAt, LocalDateTime.now()));
    }

    public boolean isReady() {
        if (!initialSyncCompleted) {
            return false;
        }
        for (String required : requiredArtifactKeys) {
            String[] coordinates = required.split(":", 2);
            ConnectorPluginActivation activation = findLocal(coordinates[0], coordinates[1]);
            if (activation == null || !ConnectorActivationState.READY.name().equals(activation.getState())
                    || !runtime.isLoaded(coordinates[0], coordinates[1])) {
                return false;
            }
        }
        return true;
    }

    public String readinessErrorCode() {
        return initialSyncErrorCode;
    }

    public String localInstanceId() {
        return localInstanceId;
    }

    private void preload(ConnectorPluginActivation activation) {
        synchronized (versionMonitor(activation.getPluginId(), activation.getPluginVersion())) {
            try {
                PluginArtifactDescriptorDTO artifact = requireArtifact(
                        activation.getPluginId(), activation.getPluginVersion());
                boolean newlyLoaded = false;
                if (!runtime.isLoaded(activation.getPluginId(), activation.getPluginVersion())) {
                    runtime.preload(artifact);
                    newlyLoaded = true;
                }
                LocalDateTime now = LocalDateTime.now();
                activation.setArtifactSha256(artifact.artifactSha256());
                activation.setState(ConnectorActivationState.READY.name());
                activation.setLoadedAt(now);
                activation.setLastHeartbeatAt(now);
                activation.setSafeErrorCode(null);
                activation.setSafeErrorDigest(null);
                save(activation, now);
                if (newlyLoaded) {
                    meterRegistry.counter("connector_plugin_load_total",
                            "pluginId", activation.getPluginId(),
                            "pluginVersion", activation.getPluginVersion()).increment();
                }
                if (activation.getCreatedAt() != null) {
                    meterRegistry.timer("connector_plugin_activation_lag_seconds",
                                    "pluginId", activation.getPluginId(),
                                    "pluginVersion", activation.getPluginVersion(),
                                    "instanceId", localInstanceId)
                            .record(Duration.between(activation.getCreatedAt(), now));
                }
            } catch (RuntimeException ex) {
                markFailed(activation, ex);
            }
        }
    }

    private void markReleaseFailed(ConnectorPluginActivation activation, RuntimeException ex) {
        LocalDateTime now = LocalDateTime.now();
        activation.setState(ConnectorActivationState.RELEASING.name());
        activation.setLastHeartbeatAt(now);
        activation.setSafeErrorCode(safeErrorCode(ex));
        activation.setSafeErrorDigest(digest(ex));
        save(activation, now);
        meterRegistry.counter("connector_plugin_release_failures_total",
                "pluginId", activation.getPluginId(),
                "pluginVersion", activation.getPluginVersion(),
                "errorCategory", activation.getSafeErrorCode()).increment();
    }

    private void release(ConnectorPluginActivation activation) {
        synchronized (versionMonitor(activation.getPluginId(), activation.getPluginVersion())) {
            try {
                pipelineRetirementProvider.orderedStream().forEach(
                        retirement -> retirement.retirePipelinesUsing(
                                activation.getPluginId(), activation.getPluginVersion()));
                boolean released = runtime.release(activation.getPluginId(), activation.getPluginVersion());
                if (!released && runtime.isLoaded(activation.getPluginId(), activation.getPluginVersion())) {
                    return;
                }
                LocalDateTime now = LocalDateTime.now();
                activation.setState(ConnectorActivationState.RELEASED.name());
                activation.setLastHeartbeatAt(now);
                activation.setSafeErrorCode(null);
                activation.setSafeErrorDigest(null);
                save(activation, now);
            } catch (RuntimeException ex) {
                markReleaseFailed(activation, ex);
            }
        }
    }

    private Object versionMonitor(String pluginId, String pluginVersion) {
        return versionMonitors.computeIfAbsent(key(pluginId, pluginVersion), ignored -> new Object());
    }

    private void markFailed(ConnectorPluginActivation activation, RuntimeException ex) {
        LocalDateTime now = LocalDateTime.now();
        activation.setState(ConnectorActivationState.FAILED.name());
        activation.setLastHeartbeatAt(now);
        activation.setSafeErrorCode(safeErrorCode(ex));
        activation.setSafeErrorDigest(digest(ex));
        save(activation, now);
        meterRegistry.counter("connector_plugin_load_failures_total",
                "pluginId", activation.getPluginId(),
                "pluginVersion", activation.getPluginVersion(),
                "errorCategory", activation.getSafeErrorCode()).increment();
    }

    private PluginArtifactDescriptorDTO requireArtifact(String pluginId, String pluginVersion) {
        Result<PluginArtifactDescriptorDTO> response = pluginClient.getArtifact(pluginId, pluginVersion);
        PluginArtifactDescriptorDTO artifact = response != null ? response.getData() : null;
        if (artifact == null) {
            throw new IllegalStateException("Plugin artifact is unavailable");
        }
        if (!pluginId.equals(artifact.pluginId()) || !pluginVersion.equals(artifact.version())) {
            throw new IllegalStateException("Plugin artifact coordinates mismatch");
        }
        if (!StringUtils.hasText(artifact.artifactSha256())) {
            throw new IllegalStateException("Plugin artifact hash is missing");
        }
        return artifact;
    }

    private ConnectorPluginActivation find(String instanceId, String pluginId, String pluginVersion) {
        return mapper.selectOne(new LambdaQueryWrapper<ConnectorPluginActivation>()
                .eq(ConnectorPluginActivation::getServiceInstanceId, instanceId)
                .eq(ConnectorPluginActivation::getPluginId, pluginId)
                .eq(ConnectorPluginActivation::getPluginVersion, pluginVersion)
                .last("LIMIT 1"));
    }

    private void save(ConnectorPluginActivation activation, LocalDateTime now) {
        activation.setUpdatedAt(now);
        if (activation.getId() == null) {
            activation.setCreatedAt(now);
            mapper.insert(activation);
        } else {
            mapper.updateById(activation);
        }
    }

    private Set<String> activeInstanceIds() {
        Set<String> ids = new LinkedHashSet<>();
        List<ServiceInstance> instances = discoveryClient.getInstances(ACCESS_SERVICE);
        if (instances != null) {
            for (ServiceInstance instance : instances) {
                ids.add(instanceIdentity(instance));
            }
        }
        if (ids.isEmpty()) {
            ids.add(localInstanceId);
        }
        return ids;
    }

    private boolean runtimeLoadedForLocal(ConnectorPluginActivation activation) {
        return !localInstanceAliases().contains(activation.getServiceInstanceId())
                || runtime.isLoaded(activation.getPluginId(), activation.getPluginVersion());
    }

    private ConnectorPluginActivation findLocal(String pluginId, String pluginVersion) {
        List<ConnectorPluginActivation> records = mapper.selectList(
                new LambdaQueryWrapper<ConnectorPluginActivation>()
                        .in(ConnectorPluginActivation::getServiceInstanceId, localInstanceAliases())
                        .eq(ConnectorPluginActivation::getPluginId, pluginId)
                        .eq(ConnectorPluginActivation::getPluginVersion, pluginVersion));
        return records.stream()
                .filter(record -> ConnectorActivationState.READY.name().equals(record.getState()))
                .findFirst()
                .orElseGet(() -> records.stream().findFirst().orElse(null));
    }

    Set<String> localInstanceAliases() {
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(localInstanceId);
        Registration registration = localRegistrationProvider.orderedStream().findFirst().orElse(null);
        if (registration != null) {
            aliases.add(instanceIdentity(registration));
            aliases.add(hostAndPort(registration));
        }
        aliases.add(localDiscoveryAddress);
        aliases.removeIf(value -> !StringUtils.hasText(value));
        return aliases;
    }

    private String instanceIdentity(ServiceInstance instance) {
        String stableId = instance.getMetadata() != null
                ? instance.getMetadata().get(CONNECTOR_INSTANCE_ID_METADATA)
                : null;
        return StringUtils.hasText(stableId) ? stableId.trim() : hostAndPort(instance);
    }

    private String hostAndPort(ServiceInstance instance) {
        return instance.getHost() + ":" + instance.getPort();
    }

    private ConnectorPluginActivationDTO toDto(ConnectorPluginActivation activation) {
        ConnectorPluginActivationDTO dto = new ConnectorPluginActivationDTO();
        dto.setServiceInstanceId(activation.getServiceInstanceId());
        dto.setPluginId(activation.getPluginId());
        dto.setPluginVersion(activation.getPluginVersion());
        dto.setArtifactSha256(activation.getArtifactSha256());
        dto.setHostVersion(activation.getHostVersion());
        dto.setState(activation.getState());
        dto.setLoadedAt(activation.getLoadedAt());
        dto.setLastHeartbeatAt(activation.getLastHeartbeatAt());
        dto.setSafeErrorCode(activation.getSafeErrorCode());
        dto.setSafeErrorDigest(activation.getSafeErrorDigest());
        return dto;
    }

    private void validateCoordinates(String pluginId, String pluginVersion) {
        if (!StringUtils.hasText(pluginId) || !StringUtils.hasText(pluginVersion)) {
            throw new IllegalArgumentException("pluginId and pluginVersion are required");
        }
    }

    private String key(String pluginId, String version) {
        return pluginId + ":" + version;
    }

    private String safeErrorCode(RuntimeException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().contains("ACTIVE_CONNECTOR_BINDING_INVALID")) {
                return "ACTIVE_CONNECTOR_BINDING_INVALID";
            }
            current = current.getCause();
        }
        String name = ex.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_]", "_");
        return name.toUpperCase(Locale.ROOT);
    }

    private String digest(RuntimeException ex) {
        try {
            String value = ex.getClass().getName() + ":" + String.valueOf(ex.getMessage());
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return "digest-unavailable";
        }
    }

    private static String resolveLocalInstanceId(
            ConnectorRuntimeProperties properties, Environment environment) {
        if (StringUtils.hasText(properties.getInstanceId())) {
            return properties.getInstanceId().trim();
        }
        return resolveLocalDiscoveryAddress(environment);
    }

    private static String resolveLocalDiscoveryAddress(Environment environment) {
        String host = environment.getProperty("spring.cloud.nacos.discovery.ip");
        if (!StringUtils.hasText(host)) {
            host = environment.getProperty("spring.cloud.client.ip-address");
        }
        if (!StringUtils.hasText(host)) {
            host = environment.getProperty("HOSTNAME", "localhost");
        }
        String port = environment.getProperty("server.port", "8080");
        return host + ":" + port;
    }
}
