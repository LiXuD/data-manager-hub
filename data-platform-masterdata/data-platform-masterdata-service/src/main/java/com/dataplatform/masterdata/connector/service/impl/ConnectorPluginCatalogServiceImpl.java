package com.dataplatform.masterdata.connector.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginStageReqDTO;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPluginDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPluginVersionDTO;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.entity.ConnectorPlugin;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorTestFact;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginMapper;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorTestFactMapper;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.ConnectorPluginCatalogService;
import com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator;
import com.dataplatform.masterdata.connector.service.PluginArtifactVerifier;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectorPluginCatalogServiceImpl implements ConnectorPluginCatalogService {
    private static final String ACTIVE = "ACTIVE";
    private static final String VERIFIED = "VERIFIED";
    private static final String STAGING = "STAGING";
    private static final String STAGING_FAILED = "STAGING_FAILED";
    private static final String DISABLED = "DISABLED";

    private final ConnectorPluginMapper pluginMapper;
    private final ConnectorPluginVersionMapper versionMapper;
    private final VendorConnectorVersionMapper connectorVersionMapper;
    private final VendorConfigMapper vendorConfigMapper;
    private final PluginArtifactVerifier artifactVerifier;
    private final VendorConnectorTestFactMapper testFactMapper;
    private final ConnectorPluginActivationInternalFeignClient activationClient;
    private final ConnectorPluginReleaseCoordinator releaseCoordinator;
    private final ObjectMapper objectMapper;

    public ConnectorPluginCatalogServiceImpl(
            ConnectorPluginMapper pluginMapper,
            ConnectorPluginVersionMapper versionMapper,
            VendorConnectorVersionMapper connectorVersionMapper,
            VendorConfigMapper vendorConfigMapper,
            VendorConnectorTestFactMapper testFactMapper,
            PluginArtifactVerifier artifactVerifier,
            ConnectorPluginActivationInternalFeignClient activationClient,
            ConnectorPluginReleaseCoordinator releaseCoordinator,
            ObjectMapper objectMapper) {
        this.pluginMapper = pluginMapper;
        this.versionMapper = versionMapper;
        this.connectorVersionMapper = connectorVersionMapper;
        this.vendorConfigMapper = vendorConfigMapper;
        this.testFactMapper = testFactMapper;
        this.artifactVerifier = artifactVerifier;
        this.activationClient = activationClient;
        this.releaseCoordinator = releaseCoordinator;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ConnectorPluginDTO> list() {
        return pluginMapper.selectList(new LambdaQueryWrapper<ConnectorPlugin>()
                        .orderByAsc(ConnectorPlugin::getPluginId))
                .stream().map(this::toPluginDto).toList();
    }

    @Override
    public ConnectorPluginDTO get(String pluginId) {
        return toPluginDto(requirePlugin(pluginId));
    }

    @Override
    public List<ConnectorPluginVersionDTO> versions(String pluginId) {
        requirePlugin(pluginId);
        return versionMapper.selectList(new LambdaQueryWrapper<ConnectorPluginVersion>()
                        .eq(ConnectorPluginVersion::getPluginId, pluginId)
                        .orderByDesc(ConnectorPluginVersion::getCreatedAt))
                .stream().map(this::toVersionDto).toList();
    }

    @Override
    @Transactional
    public ConnectorPluginVersionDTO importVersion(PluginImportRequestDTO request, Long actorId) {
        VerifiedPluginArtifact verified = artifactVerifier.verify(request);
        ConnectorPluginVersion existing = findVersion(verified.pluginId(), verified.version());
        if (existing != null) {
            throw new ConnectorConflictException("相同pluginId和version已存在且不可覆盖");
        }
        ConnectorPlugin plugin = findPlugin(verified.pluginId());
        if (plugin == null) {
            plugin = new ConnectorPlugin();
            plugin.setPluginId(verified.pluginId());
            plugin.setDisplayName(verified.displayName());
            plugin.setProvider(verified.provider());
            plugin.setDescription(verified.description());
            plugin.setStatus(ACTIVE);
            plugin.setCreatedBy(actorId);
            plugin.setUpdatedBy(actorId);
            pluginMapper.insert(plugin);
        } else if (!Objects.equals(plugin.getProvider(), verified.provider())) {
            throw new ConnectorConflictException("相同pluginId不能变更provider");
        }
        ConnectorPluginVersion version = fromVerified(verified, actorId);
        versionMapper.insert(version);
        return toVersionDto(version);
    }

    @Override
    public ConnectorPluginVersionDTO verify(String pluginId, String version, Long actorId) {
        ConnectorPluginVersion current = requireVersion(pluginId, version);
        PluginImportRequestDTO request = new PluginImportRequestDTO(
                current.getArtifactUri(), current.getArtifactSha256(),
                current.getDetachedSignature(), current.getSigningKeyId());
        try {
            VerifiedPluginArtifact verified = artifactVerifier.verify(request);
            if (!pluginId.equals(verified.pluginId()) || !version.equals(verified.version())) {
                throw new IllegalArgumentException("重新验证的Manifest身份与已导入版本不一致");
            }
            assertManifestProjection(current, verified);
            persistSuccessfulState(current, ACTIVE.equals(current.getStatus()) ? ACTIVE : VERIFIED,
                    actorId, LocalDateTime.now());
            return toVersionDto(current);
        } catch (RuntimeException exception) {
            current.setStatus(STAGING_FAILED);
            current.setSafeErrorCode("STATIC_VERIFICATION_FAILED");
            current.setSafeErrorDigest(safeDigest(exception.getMessage()));
            current.setUpdatedBy(actorId);
            versionMapper.updateById(current);
            throw exception;
        }
    }

    @Override
    public ConnectorPluginActivationSummaryDTO stage(String pluginId, String version) {
        ConnectorPluginVersion current = requireVersion(pluginId, version);
        if (!List.of(VERIFIED, STAGING_FAILED, STAGING).contains(current.getStatus())) {
            throw new ConnectorConflictException("只有已验证或预加载失败的插件版本可以进入STAGING");
        }
        persistSuccessfulState(current, STAGING, null, null);
        ConnectorPluginStageReqDTO request = new ConnectorPluginStageReqDTO();
        request.setPluginId(pluginId);
        request.setPluginVersion(version);
        try {
            ConnectorPluginActivationSummaryDTO summary = requireSuccess(activationClient.stage(request));
            if (Boolean.FALSE.equals(summary.getReady()) && summary.getInstances().stream()
                    .anyMatch(instance -> "FAILED".equals(instance.getState()))) {
                current.setStatus(STAGING_FAILED);
                current.setSafeErrorCode("ACCESS_PRELOAD_FAILED");
                current.setSafeErrorDigest("至少一个Access实例预加载失败");
                versionMapper.updateById(current);
            }
            return summary;
        } catch (RuntimeException exception) {
            current.setStatus(STAGING_FAILED);
            current.setSafeErrorCode("ACCESS_PRELOAD_UNAVAILABLE");
            current.setSafeErrorDigest(safeDigest(exception.getMessage()));
            versionMapper.updateById(current);
            throw exception;
        }
    }

    @Override
    public ConnectorPluginActivationSummaryDTO activation(String pluginId, String version) {
        requireVersion(pluginId, version);
        return requireSuccess(activationClient.activation(pluginId, version));
    }

    @Override
    @Transactional
    public ConnectorPluginVersionDTO activate(String pluginId, String version, Long actorId) {
        ConnectorPluginVersion current = requireVersion(pluginId, version);
        if (!STAGING.equals(current.getStatus())) {
            throw new ConnectorConflictException("只有STAGING插件版本可以激活");
        }
        ConnectorPluginActivationSummaryDTO summary = activation(pluginId, version);
        if (!Boolean.TRUE.equals(summary.getReady())) {
            throw new ConnectorConflictException("尚未有全部活动Access实例完成预加载");
        }
        if (!successfulDraftTestExists(pluginId, version)) {
            throw new ConnectorConflictException("插件版本尚未被任何厂商草稿成功受控测试，不能激活");
        }
        versionMapper.update(null, new LambdaUpdateWrapper<ConnectorPluginVersion>()
                .eq(ConnectorPluginVersion::getPluginId, pluginId)
                .eq(ConnectorPluginVersion::getStatus, ACTIVE)
                .set(ConnectorPluginVersion::getStatus, VERIFIED)
                .set(ConnectorPluginVersion::getUpdatedBy, actorId)
                .set(ConnectorPluginVersion::getUpdatedAt, LocalDateTime.now()));
        persistSuccessfulState(current, ACTIVE, actorId, null);
        releaseCoordinator.reconcileAfterCommit();
        return toVersionDto(current);
    }

    @Override
    @Transactional
    public ConnectorPluginVersionDTO disable(String pluginId, String version, Long actorId) {
        ConnectorPluginVersion current = requireVersion(pluginId, version);
        if (List.of("IMPORTED", STAGING).contains(current.getStatus())) {
            throw new ConnectorConflictException("导入或预加载中的插件版本不能禁用");
        }
        if (activeBindingExists(pluginId, version)) {
            throw new ConnectorConflictException("插件版本仍被活动厂商连接器绑定，请先迁移或回滚绑定");
        }
        current.setStatus(DISABLED);
        current.setUpdatedBy(actorId);
        versionMapper.updateById(current);
        releaseCoordinator.reconcileAfterCommit();
        return toVersionDto(current);
    }

    private boolean activeBindingExists(String pluginId, String version) {
        List<VendorConnectorVersion> activeVersions = connectorVersionMapper.selectList(
                new LambdaQueryWrapper<VendorConnectorVersion>()
                        .eq(VendorConnectorVersion::getStatus, ACTIVE));
        return activeVersions.stream().flatMap(item -> readPipeline(item.getPipelineSnapshot()).stream())
                .anyMatch(step -> pluginId.equals(step.pluginId()) && version.equals(step.pluginVersion()));
    }

    private boolean successfulDraftTestExists(String pluginId, String version) {
        String binding = pluginId + ":" + version;
        return testFactMapper.selectList(new LambdaQueryWrapper<VendorConnectorTestFact>()
                        .eq(VendorConnectorTestFact::getTestSucceeded, true))
                .stream().anyMatch(fact -> readStrings(fact.getPluginBindings()).contains(binding));
    }

    @Override
    public PluginArtifactDescriptorDTO artifact(String pluginId, String version) {
        ConnectorPluginVersion entity = requireVersion(pluginId, version);
        if ("IMPORTED".equals(entity.getStatus())) {
            throw new ConnectorConflictException("插件制品尚未通过静态验证");
        }
        return toArtifactDto(entity);
    }

    @Override
    public List<PluginArtifactDescriptorDTO> requiredArtifacts() {
        Map<String, PluginArtifactDescriptorDTO> result = new LinkedHashMap<>();
        List<VendorConnectorVersion> activeVersions = new ArrayList<>();
        List<VendorConfig> activeConfigs = vendorConfigMapper.selectList(
                new LambdaQueryWrapper<VendorConfig>()
                        .eq(VendorConfig::getStatus, CommonStatus.ACTIVE.getCode())
                        .eq(VendorConfig::getDeleted, false));
        for (VendorConfig config : activeConfigs) {
            if (!"PLUGIN".equals(config.getRuntimeMode()) || config.getActiveConnectorVersionId() == null) {
                throw new IllegalStateException("ACTIVE_CONNECTOR_BINDING_INVALID");
            }
            VendorConnectorVersion version = connectorVersionMapper.selectById(
                    config.getActiveConnectorVersionId());
            if (version == null || !ACTIVE.equals(version.getStatus())
                    || !config.getId().equals(version.getVendorConfigId())) {
                throw new IllegalStateException("ACTIVE_CONNECTOR_BINDING_INVALID");
            }
            activeVersions.add(version);
        }
        for (VendorConnectorVersion connectorVersion : activeVersions) {
            for (ConnectorPipelineStepDTO step : readPipeline(connectorVersion.getPipelineSnapshot())) {
                if (Boolean.FALSE.equals(step.enabled())) {
                    continue;
                }
                if (PlatformCoreConnectorMetadata.PLUGIN_ID.equals(step.pluginId())) {
                    if (!PlatformCoreConnectorMetadata.VERSION.equals(step.pluginVersion())) {
                        throw new IllegalStateException("PLATFORM_CORE_VERSION_INVALID");
                    }
                    continue;
                }
                String key = step.pluginId() + ":" + step.pluginVersion();
                result.computeIfAbsent(key, ignored -> artifact(step.pluginId(), step.pluginVersion()));
            }
        }
        return List.copyOf(result.values());
    }

    private ConnectorPlugin requirePlugin(String pluginId) {
        ConnectorPlugin plugin = findPlugin(pluginId);
        if (plugin == null) throw new IllegalArgumentException("插件不存在");
        return plugin;
    }

    private ConnectorPlugin findPlugin(String pluginId) {
        return pluginMapper.selectOne(new LambdaQueryWrapper<ConnectorPlugin>()
                .eq(ConnectorPlugin::getPluginId, pluginId));
    }

    private ConnectorPluginVersion requireVersion(String pluginId, String version) {
        ConnectorPluginVersion result = findVersion(pluginId, version);
        if (result == null) throw new IllegalArgumentException("插件版本不存在");
        return result;
    }

    private ConnectorPluginVersion findVersion(String pluginId, String version) {
        return versionMapper.selectOne(new LambdaQueryWrapper<ConnectorPluginVersion>()
                .eq(ConnectorPluginVersion::getPluginId, pluginId)
                .eq(ConnectorPluginVersion::getVersion, version));
    }

    private ConnectorPluginVersion fromVerified(VerifiedPluginArtifact verified, Long actorId) {
        ConnectorPluginVersion version = new ConnectorPluginVersion();
        version.setPluginId(verified.pluginId());
        version.setVersion(verified.version());
        version.setSpiVersion(verified.spiVersion());
        version.setEntryClass(verified.entryClass());
        version.setArtifactUri(verified.artifactUri());
        version.setArtifactSha256(verified.artifactSha256());
        version.setDetachedSignature(verified.detachedSignature());
        version.setSigningKeyId(verified.signingKeyId());
        version.setManifestJson(verified.manifestJson());
        version.setConfigSchemaJson(verified.configSchemaJson());
        version.setCapabilities(writeJson(verified.capabilities()));
        version.setPermissionManifest(verified.permissionManifestJson());
        version.setMinHostVersion(verified.minHostVersion());
        version.setManifestVersion(verified.manifestVersion());
        version.setAuthoringModel(verified.authoringModel().name());
        version.setConnectorKind(enumName(verified.connectorKind()));
        version.setTransportMode(enumName(verified.transportMode()));
        version.setOutputMode(enumName(verified.outputMode()));
        version.setCompatibilityManifest("2".equals(verified.manifestVersion())
                ? verified.compatibilityJson() : null);
        version.setStatus(VERIFIED);
        version.setVerifiedAt(LocalDateTime.now());
        version.setCreatedBy(actorId);
        version.setUpdatedBy(actorId);
        return version;
    }

    private ConnectorPluginDTO toPluginDto(ConnectorPlugin plugin) {
        ConnectorPluginVersion active = versionMapper.selectOne(
                new LambdaQueryWrapper<ConnectorPluginVersion>()
                        .eq(ConnectorPluginVersion::getPluginId, plugin.getPluginId())
                        .eq(ConnectorPluginVersion::getStatus, ACTIVE)
                        .last("LIMIT 1"));
        return new ConnectorPluginDTO(plugin.getId(), plugin.getPluginId(), plugin.getDisplayName(),
                plugin.getProvider(), plugin.getDescription(), plugin.getStatus(),
                active == null ? null : active.getVersion(), activeBindingCount(plugin.getPluginId()),
                plugin.getCreatedAt(), plugin.getUpdatedAt());
    }

    private long activeBindingCount(String pluginId) {
        return connectorVersionMapper.selectList(new LambdaQueryWrapper<VendorConnectorVersion>()
                        .eq(VendorConnectorVersion::getStatus, ACTIVE))
                .stream().filter(item -> readPipeline(item.getPipelineSnapshot()).stream()
                        .anyMatch(step -> !Boolean.FALSE.equals(step.enabled())
                                && pluginId.equals(step.pluginId())))
                .map(VendorConnectorVersion::getVendorConfigId).distinct().count();
    }

    private ConnectorPluginVersionDTO toVersionDto(ConnectorPluginVersion version) {
        return new ConnectorPluginVersionDTO(version.getId(), version.getPluginId(), version.getVersion(),
                version.getSpiVersion(), version.getEntryClass(), version.getArtifactUri(),
                version.getArtifactSha256(), version.getSigningKeyId(), version.getManifestJson(),
                version.getConfigSchemaJson(), readStrings(version.getCapabilities()),
                version.getPermissionManifest(), version.getMinHostVersion(), version.getStatus(), version.getSafeErrorCode(),
                version.getSafeErrorDigest(), version.getVerifiedAt(), version.getCreatedAt());
    }

    private PluginArtifactDescriptorDTO toArtifactDto(ConnectorPluginVersion version) {
        try {
            PluginManifestReader reader = new PluginManifestReader(objectMapper);
            byte[] manifestBytes = version.getManifestJson().getBytes(StandardCharsets.UTF_8);
            PluginManifest manifest = reader.read(manifestBytes);
            String compatibilityJson = "2".equals(manifest.manifestVersion())
                    ? new String(reader.canonicalize(objectMapper.writeValueAsBytes(
                    objectMapper.readTree(manifestBytes).path("compatibility"))), StandardCharsets.UTF_8)
                    : "{}";
            assertManifestProjection(version, manifest,
                    "2".equals(manifest.manifestVersion()) ? compatibilityJson : null);
            return new PluginArtifactDescriptorDTO(version.getPluginId(), version.getVersion(),
                    version.getSpiVersion(), version.getEntryClass(), version.getArtifactUri(),
                    version.getArtifactSha256(), version.getDetachedSignature(), version.getSigningKeyId(),
                    version.getManifestJson(), version.getConfigSchemaJson(), readStrings(version.getCapabilities()),
                    version.getPermissionManifest(), version.getMinHostVersion(), version.getStatus(),
                    manifest.manifestVersion(), manifest.authoringModel().name(),
                    manifest.connectorKind() == null ? null : manifest.connectorKind().name(),
                    manifest.transportMode() == null ? null : manifest.transportMode().name(),
                    manifest.outputMode() == null ? null : manifest.outputMode().name(),
                    compatibilityJson);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("插件Manifest投影损坏", exception);
        }
    }

    private List<String> readStrings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("插件能力元数据损坏", exception);
        }
    }

    private void assertManifestProjection(
            ConnectorPluginVersion version,
            VerifiedPluginArtifact verified) {
        assertManifestProjection(version, verified.manifestVersion(),
                verified.authoringModel().name(), enumName(verified.connectorKind()),
                enumName(verified.transportMode()), enumName(verified.outputMode()),
                "2".equals(verified.manifestVersion()) ? verified.compatibilityJson() : null);
    }

    private void assertManifestProjection(
            ConnectorPluginVersion version,
            PluginManifest manifest,
            String compatibilityJson) {
        assertManifestProjection(version, manifest.manifestVersion(),
                manifest.authoringModel().name(), enumName(manifest.connectorKind()),
                enumName(manifest.transportMode()), enumName(manifest.outputMode()),
                compatibilityJson);
    }

    private void assertManifestProjection(
            ConnectorPluginVersion version,
            String manifestVersion,
            String authoringModel,
            String connectorKind,
            String transportMode,
            String outputMode,
            String compatibilityJson) {
        if (!Objects.equals(version.getManifestVersion(), manifestVersion)
                || !Objects.equals(version.getAuthoringModel(), authoringModel)
                || !Objects.equals(version.getConnectorKind(), connectorKind)
                || !Objects.equals(version.getTransportMode(), transportMode)
                || !Objects.equals(version.getOutputMode(), outputMode)
                || !sameJson(version.getCompatibilityManifest(), compatibilityJson)) {
            throw new IllegalStateException("插件Manifest索引投影与签名Manifest不一致");
        }
    }

    private boolean sameJson(String left, String right) {
        if (left == null || right == null) return left == null && right == null;
        try {
            return Objects.equals(objectMapper.readTree(left), objectMapper.readTree(right));
        } catch (Exception exception) {
            throw new IllegalStateException("插件Manifest兼容投影损坏", exception);
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private List<ConnectorPipelineStepDTO> readPipeline(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConnectorPipelineStepDTO>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("连接器流水线快照损坏", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("插件元数据无法序列化", exception);
        }
    }

    private <T> T requireSuccess(Result<T> result) {
        if (result == null || !Objects.equals(200, result.getCode()) || result.getData() == null) {
            throw new IllegalStateException(result == null ? "Access激活服务无响应" : result.getMsg());
        }
        return result.getData();
    }

    /**
     * Persists a successful catalog transition and explicitly clears any failure left by an
     * earlier attempt. MyBatis-Plus entity updates omit null values, so updateById cannot clear
     * safe_error_code/safe_error_digest reliably.
     */
    private void persistSuccessfulState(ConnectorPluginVersion current, String status,
                                        Long actorId, LocalDateTime verifiedAt) {
        LocalDateTime updatedAt = LocalDateTime.now();
        current.setStatus(status);
        current.setSafeErrorCode(null);
        current.setSafeErrorDigest(null);
        current.setUpdatedAt(updatedAt);
        if (actorId != null) {
            current.setUpdatedBy(actorId);
        }
        if (verifiedAt != null) {
            current.setVerifiedAt(verifiedAt);
        }
        versionMapper.update(null, new LambdaUpdateWrapper<ConnectorPluginVersion>()
                .eq(ConnectorPluginVersion::getId, current.getId())
                .set(ConnectorPluginVersion::getStatus, status)
                .set(ConnectorPluginVersion::getSafeErrorCode, null)
                .set(ConnectorPluginVersion::getSafeErrorDigest, null)
                .set(ConnectorPluginVersion::getUpdatedAt, updatedAt)
                .set(actorId != null, ConnectorPluginVersion::getUpdatedBy, actorId)
                .set(verifiedAt != null, ConnectorPluginVersion::getVerifiedAt, verifiedAt));
    }

    private String safeDigest(String message) {
        try {
            String value = message == null ? "no-message" : message;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return "digest-unavailable";
        }
    }
}
