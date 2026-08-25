package com.dataplatform.masterdata.connector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Reconciles Access runtimes against Masterdata's active immutable connector bindings. */
@Component
public class ConnectorPluginReleaseCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ConnectorPluginReleaseCoordinator.class);

    private final VendorConnectorVersionMapper connectorMapper;
    private final ConnectorPluginVersionMapper pluginMapper;
    private final ConnectorPluginActivationInternalFeignClient activationClient;
    private final ObjectMapper objectMapper;

    public ConnectorPluginReleaseCoordinator(
            VendorConnectorVersionMapper connectorMapper,
            ConnectorPluginVersionMapper pluginMapper,
            ConnectorPluginActivationInternalFeignClient activationClient,
            ObjectMapper objectMapper) {
        this.connectorMapper = connectorMapper;
        this.pluginMapper = pluginMapper;
        this.activationClient = activationClient;
        this.objectMapper = objectMapper;
    }

    public void reconcileAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { reconcileSafely(); }
            });
        } else {
            reconcileSafely();
        }
    }

    @Scheduled(fixedDelayString = "${connector.control.release-reconcile-ms:30000}")
    public void reconcileSafely() {
        try {
            reconcile();
        } catch (RuntimeException exception) {
            log.warn("连接器未绑定插件版本释放对账失败，将在下一周期重试: {}",
                    exception.getClass().getSimpleName());
        }
    }

    void reconcile() {
        Set<String> required = requiredBindings();
        RuntimeException firstFailure = null;
        for (ConnectorPluginVersion plugin : pluginMapper.selectList(
                new LambdaQueryWrapper<ConnectorPluginVersion>())) {
            String key = key(plugin.getPluginId(), plugin.getVersion());
            if (required.contains(key) || isBuiltIn(plugin)
                    || Set.of("IMPORTED", "STAGING", "STAGING_FAILED").contains(plugin.getStatus())) {
                continue;
            }
            try {
                var response = activationClient.release(plugin.getPluginId(), plugin.getVersion());
                if (response == null || response.getData() == null) {
                    throw new IllegalStateException("Access release response is unavailable");
                }
            } catch (RuntimeException exception) {
                if (firstFailure == null) firstFailure = exception;
            }
        }
        if (firstFailure != null) throw firstFailure;
    }

    private Set<String> requiredBindings() {
        Set<String> required = new LinkedHashSet<>();
        List<VendorConnectorVersion> active = connectorMapper.selectList(
                new LambdaQueryWrapper<VendorConnectorVersion>()
                        .eq(VendorConnectorVersion::getStatus, "ACTIVE"));
        for (VendorConnectorVersion version : active) {
            for (ConnectorPipelineStepDTO step : readPipeline(version.getPipelineSnapshot())) {
                if (!Boolean.FALSE.equals(step.enabled())) {
                    required.add(key(step.pluginId(), step.pluginVersion()));
                }
            }
        }
        return Set.copyOf(required);
    }

    private List<ConnectorPipelineStepDTO> readPipeline(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConnectorPipelineStepDTO>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("活动连接器快照损坏，禁止释放任何插件版本", exception);
        }
    }

    private boolean isBuiltIn(ConnectorPluginVersion plugin) {
        return plugin.getArtifactUri() != null && plugin.getArtifactUri().startsWith("builtin:");
    }

    private String key(String pluginId, String version) { return pluginId + ":" + version; }
}
