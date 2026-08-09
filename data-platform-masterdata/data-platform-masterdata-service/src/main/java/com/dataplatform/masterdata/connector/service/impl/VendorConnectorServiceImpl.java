package com.dataplatform.masterdata.connector.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorStageTimingDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorValidationResultDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorDraftDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorSaveDraftRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestResultDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorVersionDTO;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorTestFact;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorTestFactMapper;
import com.dataplatform.masterdata.connector.service.ConnectorConfigSchemaValidator;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.ConnectorSecretReferenceService;
import com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator;
import com.dataplatform.masterdata.connector.service.VendorConnectorService;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.access.connector.api.dto.ConnectorTestPipelineStepDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginStageReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
import com.dataplatform.common.plugin.runtime.ConnectorSnapshotIntegrity;
import com.dataplatform.common.plugin.runtime.ConnectorStageDefinition;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VendorConnectorServiceImpl implements VendorConnectorService {
    private static final Set<String> CAPABILITIES = Set.of(
            "REQUEST_BUILDER", "REQUEST_PROCESSOR", "TRANSPORT",
            "RESPONSE_PROCESSOR", "RESPONSE_PARSER", "RESPONSE_NORMALIZER");
    private static final int MAX_STEPS = 50;
    private static final int MAX_STEP_CONFIG_BYTES = 64 * 1024;

    private final VendorConnectorVersionMapper connectorMapper;
    private final ConnectorPluginVersionMapper pluginVersionMapper;
    private final VendorConfigMapper vendorConfigMapper;
    private final ConnectorConfigSchemaValidator schemaValidator;
    private final ConnectorSecretReferenceService secretReferenceService;
    private final ConnectorPluginReleaseCoordinator releaseCoordinator;
    private final VendorConnectorRuntimeInternalFeignClient runtimeClient;
    private final ConnectorPluginActivationInternalFeignClient activationClient;
    private final VendorConnectorTestFactMapper testFactMapper;
    private final ObjectMapper objectMapper;

    public VendorConnectorServiceImpl(
            VendorConnectorVersionMapper connectorMapper,
            ConnectorPluginVersionMapper pluginVersionMapper,
            VendorConfigMapper vendorConfigMapper,
            ConnectorConfigSchemaValidator schemaValidator,
            ConnectorSecretReferenceService secretReferenceService,
            ConnectorPluginReleaseCoordinator releaseCoordinator,
            VendorConnectorRuntimeInternalFeignClient runtimeClient,
            ConnectorPluginActivationInternalFeignClient activationClient,
            VendorConnectorTestFactMapper testFactMapper,
            ObjectMapper objectMapper) {
        this.connectorMapper = connectorMapper;
        this.pluginVersionMapper = pluginVersionMapper;
        this.vendorConfigMapper = vendorConfigMapper;
        this.schemaValidator = schemaValidator;
        this.secretReferenceService = secretReferenceService;
        this.releaseCoordinator = releaseCoordinator;
        this.runtimeClient = runtimeClient;
        this.activationClient = activationClient;
        this.testFactMapper = testFactMapper;
        this.objectMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    public VendorConnectorVersionDTO active(Long vendorConfigId) {
        VendorConfig config = requireConfig(vendorConfigId);
        if (config.getActiveConnectorVersionId() == null) return null;
        VendorConnectorVersion active = connectorMapper.selectById(config.getActiveConnectorVersionId());
        if (active == null || !vendorConfigId.equals(active.getVendorConfigId())) {
            throw new IllegalStateException("厂商配置活动连接器指针损坏");
        }
        return toVersionDto(active);
    }

    @Override
    public VendorConnectorDraftDTO draft(Long vendorConfigId) {
        VendorConfig config = requireConfig(vendorConfigId);
        VendorConnectorVersion draft = findDraft(vendorConfigId);
        if (draft == null) {
            return new VendorConnectorDraftDTO(null, vendorConfigId, 0,
                    config.getSecurityVersion() == null ? 0 : config.getSecurityVersion(), List.of());
        }
        return toDraftDto(draft);
    }

    @Override
    @Transactional
    public VendorConnectorDraftDTO saveDraft(Long vendorConfigId, VendorConnectorSaveDraftRequestDTO request,
                                              Long actorId) {
        VendorConfig config = requireConfig(vendorConfigId);
        List<ConnectorPipelineStepDTO> normalized = normalize(request.pipelineSnapshot());
        ConnectorValidationResultDTO validation = validatePipeline(
                vendorConfigId, normalized, PluginBindingMode.DRAFT);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }
        VendorConnectorVersion draft = findDraft(vendorConfigId);
        if (draft == null) {
            if (request.expectedDraftVersion() != 0) {
                throw new ConnectorConflictException("连接器草稿版本冲突");
            }
            draft = new VendorConnectorVersion();
            draft.setVendorConfigId(vendorConfigId);
            draft.setDraftVersion(1);
            draft.setPipelineSnapshot(writeJson(normalized));
            draft.setSecurityVersion(config.getSecurityVersion() == null ? 0 : config.getSecurityVersion());
            draft.setStatus("DRAFT");
            draft.setCreatedBy(actorId);
            draft.setUpdatedBy(actorId);
            connectorMapper.insert(draft);
        } else {
            if (!request.expectedDraftVersion().equals(draft.getDraftVersion())) {
                throw new ConnectorConflictException("连接器草稿版本冲突");
            }
            int updated = connectorMapper.updateDraft(
                    draft.getId(),
                    request.expectedDraftVersion(),
                    writeJson(normalized),
                    config.getSecurityVersion() == null ? 0 : config.getSecurityVersion(),
                    request.expectedDraftVersion() + 1,
                    actorId);
            if (updated != 1) throw new ConnectorConflictException("连接器草稿版本冲突");
            draft = findDraft(vendorConfigId);
        }
        return toDraftDto(draft);
    }

    @Override
    public ConnectorValidationResultDTO validate(Long vendorConfigId) {
        requireConfig(vendorConfigId);
        VendorConnectorVersion draft = findDraft(vendorConfigId);
        if (draft == null) {
            return new ConnectorValidationResultDTO(false, List.of("连接器草稿不存在"), List.of(), null);
        }
        return validatePipeline(vendorConfigId, normalize(readPipeline(draft.getPipelineSnapshot())),
                PluginBindingMode.PUBLISH);
    }

    @Override
    public VendorConnectorTestResultDTO test(Long vendorConfigId, VendorConnectorTestRequestDTO request,
                                              Long actorId) {
        requireConfig(vendorConfigId);
        VendorConnectorVersion draft = requireDraft(vendorConfigId);
        List<ConnectorPipelineStepDTO> pipeline = normalize(readPipeline(draft.getPipelineSnapshot()));
        ConnectorValidationResultDTO validation = validatePipeline(vendorConfigId, pipeline, PluginBindingMode.TEST);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }
        VendorConnectorTestReqDTO accessRequest = new VendorConnectorTestReqDTO();
        accessRequest.setVendorConfigId(vendorConfigId);
        accessRequest.setParams(request == null || request.params() == null ? Map.of() : request.params());
        accessRequest.setPipelineSnapshot(pipeline.stream().map(this::toAccessTestStep).toList());
        accessRequest.setSnapshotHash(validation.snapshotHash());
        accessRequest.setHashAlgorithm(validation.hashAlgorithm());
        accessRequest.setIntegrityHash(validation.integrityHash());
        Result<VendorConnectorTestRespDTO> result = runtimeClient.test(accessRequest);
        if (result == null || !Integer.valueOf(200).equals(result.getCode()) || result.getData() == null) {
            throw new IllegalStateException(result == null ? "Access连接器测试服务无响应" : result.getMsg());
        }
        VendorConnectorTestRespDTO response = result.getData();
        VendorConnectorTestResultDTO testResult = new VendorConnectorTestResultDTO(
                response.getSuccess(), response.getErrorCategory(),
                response.getErrorCode(), response.getSafeMessage(), Map.copyOf(response.getNormalizedData()),
                response.getStageTimings().stream().map(timing -> new ConnectorStageTimingDTO(
                        timing.getStageKey(), timing.getCapability(), timing.getPluginId(),
                        timing.getPluginVersion(), timing.getDurationMs())).toList());
        recordTestFact(vendorConfigId, draft, pipeline, validation.snapshotHash(), testResult, actorId);
        return testResult;
    }

    @Override
    @Transactional
    public VendorConnectorVersionDTO publish(Long vendorConfigId, Integer expectedDraftVersion, Long actorId) {
        VendorConfig config = requireConfig(vendorConfigId);
        VendorConnectorVersion draft = requireDraft(vendorConfigId);
        if (!expectedDraftVersion.equals(draft.getDraftVersion())) {
            throw new ConnectorConflictException("连接器草稿版本冲突");
        }
        List<ConnectorPipelineStepDTO> pipeline = normalize(readPipeline(draft.getPipelineSnapshot()));
        ConnectorValidationResultDTO validation = validatePipeline(
                vendorConfigId, pipeline, PluginBindingMode.PUBLISH);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }
        requireSuccessfulTestFact(vendorConfigId, draft.getDraftVersion(), validation.snapshotHash());
        VendorConnectorVersion current = findActive(vendorConfigId);
        if (current != null && validation.snapshotHash().equals(current.getSnapshotHash())) {
            throw new ConnectorConflictException("连接器草稿与当前活动版本完全一致");
        }
        int draftLocked = connectorMapper.update(null, new LambdaUpdateWrapper<VendorConnectorVersion>()
                .eq(VendorConnectorVersion::getId, draft.getId())
                .eq(VendorConnectorVersion::getStatus, "DRAFT")
                .eq(VendorConnectorVersion::getDraftVersion, expectedDraftVersion)
                .set(VendorConnectorVersion::getUpdatedAt, LocalDateTime.now()));
        if (draftLocked != 1) {
            throw new ConnectorConflictException("连接器草稿版本冲突");
        }
        int nextVersion = nextVersion(vendorConfigId);
        VendorConnectorVersion published = immutableVersion(vendorConfigId, nextVersion, pipeline,
                validation.snapshotHash(), validation.hashAlgorithm(), validation.integrityHash(),
                draft.getSecurityVersion(), current == null ? null : current.getId(), actorId);
        connectorMapper.insert(published);
        if (current != null) {
            current.setStatus("SUPERSEDED");
            current.setUpdatedBy(actorId);
            connectorMapper.updateById(current);
        }
        updateActivePointer(config, published, actorId);
        releaseCoordinator.reconcileAfterCommit();
        return toVersionDto(published);
    }

    @Override
    public List<VendorConnectorVersionDTO> history(Long vendorConfigId) {
        requireConfig(vendorConfigId);
        return connectorMapper.selectList(new LambdaQueryWrapper<VendorConnectorVersion>()
                        .eq(VendorConnectorVersion::getVendorConfigId, vendorConfigId)
                        .ne(VendorConnectorVersion::getStatus, "DRAFT")
                        .orderByDesc(VendorConnectorVersion::getVersionNo))
                .stream().map(this::toVersionDto).toList();
    }

    @Override
    @Transactional
    public VendorConnectorVersionDTO rollback(Long vendorConfigId, Integer targetVersion,
                                               Integer expectedConnectorVersion, Long actorId) {
        VendorConfig config = requireConfig(vendorConfigId);
        if (!expectedConnectorVersion.equals(defaultVersion(config.getConnectorVersion()))) {
            throw new ConnectorConflictException("厂商连接器活动版本已变化");
        }
        VendorConnectorVersion target = connectorMapper.selectOne(
                new LambdaQueryWrapper<VendorConnectorVersion>()
                        .eq(VendorConnectorVersion::getVendorConfigId, vendorConfigId)
                        .eq(VendorConnectorVersion::getVersionNo, targetVersion));
        if (target == null) throw new IllegalArgumentException("连接器历史版本不存在");
        List<ConnectorPipelineStepDTO> pipeline = normalize(readPipeline(target.getPipelineSnapshot()));
        ConnectorValidationResultDTO validation = validatePipeline(
                vendorConfigId, pipeline, PluginBindingMode.ROLLBACK);
        if (!validation.valid()) {
            throw new IllegalArgumentException("目标连接器版本当前不可运行: " + String.join("; ", validation.errors()));
        }
        ensureRollbackPluginsReady(pipeline);
        VendorConnectorVersion current = findActive(vendorConfigId);
        VendorConnectorVersion rollback = immutableVersion(vendorConfigId, nextVersion(vendorConfigId), pipeline,
                validation.snapshotHash(), validation.hashAlgorithm(), validation.integrityHash(),
                target.getSecurityVersion(), current == null ? null : current.getId(), actorId);
        connectorMapper.insert(rollback);
        if (current != null) {
            current.setStatus("SUPERSEDED");
            current.setUpdatedBy(actorId);
            connectorMapper.updateById(current);
        }
        updateActivePointer(config, rollback, actorId);
        releaseCoordinator.reconcileAfterCommit();
        return toVersionDto(rollback);
    }

    @Override
    public VendorConnectorRuntimeSnapshotDTO runtimeSnapshot(Long vendorConfigId) {
        VendorConfig config = requireConfig(vendorConfigId);
        if (!"PLUGIN".equals(config.getRuntimeMode()) || config.getActiveConnectorVersionId() == null) {
            return null;
        }
        VendorConnectorVersion version = connectorMapper.selectById(config.getActiveConnectorVersionId());
        if (version == null || !"ACTIVE".equals(version.getStatus())
                || !vendorConfigId.equals(version.getVendorConfigId())) {
            throw new IllegalStateException("活动连接器运行快照不存在或状态无效");
        }
        return new VendorConnectorRuntimeSnapshotDTO(vendorConfigId, version.getId(), version.getVersionNo(),
                version.getSnapshotHash(), version.getHashAlgorithm(), version.getIntegrityHash(),
                version.getSecurityVersion(), version.getStatus(),
                readPipeline(version.getPipelineSnapshot()), version.getPublishedAt());
    }

    private ConnectorValidationResultDTO validatePipeline(Long vendorConfigId,
                                                           List<ConnectorPipelineStepDTO> pipeline,
                                                           PluginBindingMode bindingMode) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (pipeline == null || pipeline.isEmpty()) errors.add("连接器流水线不能为空");
        if (pipeline != null && pipeline.size() > MAX_STEPS) errors.add("连接器流水线不能超过50个步骤");
        Set<String> stageKeys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        int transportCount = 0;
        if (pipeline != null) {
            for (int index = 0; index < pipeline.size(); index++) {
                ConnectorPipelineStepDTO step = pipeline.get(index);
                String path = "steps[" + index + "]";
                if (!StringUtils.hasText(step.stageKey()) || !stageKeys.add(step.stageKey())) {
                    errors.add(path + ".stageKey为空或重复");
                }
                if (!CAPABILITIES.contains(step.capability())) errors.add(path + ".capability不受支持");
                if (step.order() == null || step.order() < 0 || !orders.add(step.order())) {
                    errors.add(path + ".order为空、负数或重复");
                }
                boolean enabled = !Boolean.FALSE.equals(step.enabled());
                if (enabled && "TRANSPORT".equals(step.capability())) transportCount++;
                ConnectorPluginVersion plugin = findPluginVersion(step.pluginId(), step.pluginVersion());
                if (plugin == null) {
                    errors.add(path + "引用的插件版本不存在");
                    continue;
                }
                validateIntegrityBinding(step, plugin, path, errors);
                if (enabled && ("DISABLED".equals(plugin.getStatus()) || "IMPORTED".equals(plugin.getStatus()))) {
                    errors.add(path + "引用的插件版本不可用于新绑定: " + plugin.getStatus());
                } else if (enabled && bindingMode == PluginBindingMode.PUBLISH
                        && !"ACTIVE".equals(plugin.getStatus())) {
                    errors.add(path + "引用的插件版本尚未激活");
                } else if (enabled && bindingMode == PluginBindingMode.TEST
                        && !Set.of("STAGING", "ACTIVE").contains(plugin.getStatus())) {
                    errors.add(path + "引用的插件版本尚未预加载，不能执行受控测试");
                } else if (enabled && bindingMode == PluginBindingMode.ROLLBACK
                        && !Set.of("ACTIVE", "VERIFIED").contains(plugin.getStatus())) {
                    errors.add(path + "引用的历史插件版本不可回滚: " + plugin.getStatus());
                } else if (enabled && bindingMode == PluginBindingMode.DRAFT
                        && !"ACTIVE".equals(plugin.getStatus())) {
                    warnings.add(path + "引用的插件版本尚未激活，发布前必须激活");
                }
                List<String> capabilities = readStrings(plugin.getCapabilities());
                if (!capabilities.contains(step.capability())) errors.add(path + "能力未由插件声明");
                byte[] configBytes = writeJson(step.config() == null ? Map.of() : step.config())
                        .getBytes(StandardCharsets.UTF_8);
                if (configBytes.length > MAX_STEP_CONFIG_BYTES) errors.add(path + ".config超过64KiB");
                try {
                    JsonNode schema = objectMapper.readTree(plugin.getConfigSchemaJson());
                    schemaValidator.validate(schema, step.config(),
                                    ref -> secretReferenceService.exists(vendorConfigId, ref))
                            .forEach(error -> errors.add(path + ": " + error));
                } catch (Exception exception) {
                    errors.add(path + "对应插件Schema损坏");
                }
            }
        }
        if (transportCount != 1) errors.add("启用的流水线必须恰好包含一个TRANSPORT步骤");
        String hash = errors.isEmpty() ? snapshotHash(normalize(pipeline)) : null;
        return new ConnectorValidationResultDTO(errors.isEmpty(), List.copyOf(errors),
                List.copyOf(warnings), hash, ConnectorPipelineDefinition.V2_EMBEDDED, hash);
    }

    private List<ConnectorPipelineStepDTO> normalize(List<ConnectorPipelineStepDTO> pipeline) {
        if (pipeline == null) return List.of();
        List<ConnectorPipelineStepDTO> result = new ArrayList<>();
        for (ConnectorPipelineStepDTO step : pipeline) {
            Map<String, Object> config = step.config() == null ? Map.of() : new LinkedHashMap<>(step.config());
            ConnectorPluginVersion plugin = findPluginVersion(step.pluginId(), step.pluginVersion());
            String configHash = sha256(writeJson(config));
            result.add(new ConnectorPipelineStepDTO(step.stageKey(), step.capability(), step.pluginId(),
                    step.pluginVersion(), step.order(), step.enabled() == null || step.enabled(),
                    Collections.unmodifiableMap(config), configHash,
                    plugin == null ? null : normalizeDigest(plugin.getArtifactSha256()),
                    plugin == null ? null : jsonHash(plugin.getManifestJson()),
                    plugin == null ? null : jsonHash(plugin.getConfigSchemaJson())));
        }
        result.sort(Comparator.comparing(ConnectorPipelineStepDTO::order,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(result);
    }

    private void updateActivePointer(VendorConfig config, VendorConnectorVersion version, Long actorId) {
        int currentConnectorVersion = defaultVersion(config.getConnectorVersion());
        int updated = vendorConfigMapper.update(null, new LambdaUpdateWrapper<VendorConfig>()
                .eq(VendorConfig::getId, config.getId())
                .eq(VendorConfig::getConnectorVersion, currentConnectorVersion)
                .set(VendorConfig::getRuntimeMode, "PLUGIN")
                .set(VendorConfig::getActiveConnectorVersionId, version.getId())
                .set(VendorConfig::getConnectorVersion, currentConnectorVersion + 1)
                .set(VendorConfig::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) throw new ConnectorConflictException("厂商连接器活动版本已变化");
    }

    private VendorConnectorVersion immutableVersion(Long vendorConfigId, int versionNo,
                                                     List<ConnectorPipelineStepDTO> pipeline, String hash,
                                                     String hashAlgorithm, String integrityHash,
                                                     Integer securityVersion, Long previousId, Long actorId) {
        VendorConnectorVersion entity = new VendorConnectorVersion();
        entity.setVendorConfigId(vendorConfigId);
        entity.setVersionNo(versionNo);
        entity.setDraftVersion(0);
        entity.setPipelineSnapshot(writeJson(pipeline));
        entity.setSnapshotHash(hash);
        entity.setHashAlgorithm(hashAlgorithm);
        entity.setIntegrityHash(integrityHash);
        entity.setSecurityVersion(securityVersion == null ? 0 : securityVersion);
        entity.setStatus("ACTIVE");
        entity.setPreviousVersionId(previousId);
        entity.setPublishedAt(LocalDateTime.now());
        entity.setPublishedBy(actorId);
        entity.setCreatedBy(actorId);
        entity.setUpdatedBy(actorId);
        return entity;
    }

    private ConnectorTestPipelineStepDTO toAccessTestStep(ConnectorPipelineStepDTO step) {
        ConnectorTestPipelineStepDTO dto = new ConnectorTestPipelineStepDTO();
        dto.setStageKey(step.stageKey());
        dto.setCapability(step.capability());
        dto.setPluginId(step.pluginId());
        dto.setPluginVersion(step.pluginVersion());
        dto.setOrder(step.order());
        dto.setEnabled(step.enabled());
        dto.setConfig(step.config());
        dto.setConfigHash(step.configHash());
        dto.setArtifactSha256(step.artifactSha256());
        dto.setManifestHash(step.manifestHash());
        dto.setSchemaHash(step.schemaHash());
        return dto;
    }

    private VendorConfig requireConfig(Long id) {
        VendorConfig config = vendorConfigMapper.selectById(id);
        if (config == null) throw new IllegalArgumentException("厂商配置不存在");
        return config;
    }

    private VendorConnectorVersion requireDraft(Long vendorConfigId) {
        VendorConnectorVersion draft = findDraft(vendorConfigId);
        if (draft == null) throw new IllegalArgumentException("连接器草稿不存在");
        return draft;
    }

    private VendorConnectorVersion findDraft(Long vendorConfigId) {
        return connectorMapper.selectOne(new LambdaQueryWrapper<VendorConnectorVersion>()
                .eq(VendorConnectorVersion::getVendorConfigId, vendorConfigId)
                .eq(VendorConnectorVersion::getStatus, "DRAFT"));
    }

    private VendorConnectorVersion findActive(Long vendorConfigId) {
        return connectorMapper.selectOne(new LambdaQueryWrapper<VendorConnectorVersion>()
                .eq(VendorConnectorVersion::getVendorConfigId, vendorConfigId)
                .eq(VendorConnectorVersion::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    private ConnectorPluginVersion findPluginVersion(String pluginId, String version) {
        if (!StringUtils.hasText(pluginId) || !StringUtils.hasText(version)) return null;
        return pluginVersionMapper.selectOne(new LambdaQueryWrapper<ConnectorPluginVersion>()
                .eq(ConnectorPluginVersion::getPluginId, pluginId)
                .eq(ConnectorPluginVersion::getVersion, version));
    }

    private int nextVersion(Long vendorConfigId) {
        return history(vendorConfigId).stream().map(VendorConnectorVersionDTO::versionNo)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private int defaultVersion(Integer value) { return value == null ? 0 : value; }

    private VendorConnectorDraftDTO toDraftDto(VendorConnectorVersion entity) {
        return new VendorConnectorDraftDTO(entity.getId(), entity.getVendorConfigId(),
                entity.getDraftVersion(), entity.getSecurityVersion(), readPipeline(entity.getPipelineSnapshot()));
    }

    private VendorConnectorVersionDTO toVersionDto(VendorConnectorVersion entity) {
        return new VendorConnectorVersionDTO(entity.getId(), entity.getVendorConfigId(), entity.getVersionNo(),
                entity.getSnapshotHash(), entity.getHashAlgorithm(), entity.getIntegrityHash(),
                entity.getSecurityVersion(), entity.getStatus(),
                entity.getPreviousVersionId(), entity.getPublishedAt(), entity.getPublishedBy(),
                readPipeline(entity.getPipelineSnapshot()));
    }

    private List<ConnectorPipelineStepDTO> readPipeline(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConnectorPipelineStepDTO>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("连接器流水线快照损坏", exception);
        }
    }

    private List<String> readStrings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("插件能力元数据损坏", exception);
        }
    }

    private String snapshotHash(List<ConnectorPipelineStepDTO> pipeline) {
        return ConnectorSnapshotIntegrity.v2SnapshotHash(objectMapper,
                pipeline.stream().map(this::stageDefinition).toList());
    }

    private ConnectorStageDefinition stageDefinition(ConnectorPipelineStepDTO step) {
        return new ConnectorStageDefinition(step.stageKey(), StageCapability.valueOf(step.capability()),
                step.pluginId(), step.pluginVersion(), step.order(), !Boolean.FALSE.equals(step.enabled()),
                objectMapper.valueToTree(step.config()), step.configHash(), step.artifactSha256(),
                step.manifestHash(), step.schemaHash());
    }

    private void validateIntegrityBinding(ConnectorPipelineStepDTO step, ConnectorPluginVersion plugin,
                                          String path, List<String> errors) {
        requireDigest(step.artifactSha256(), normalizeDigest(plugin.getArtifactSha256()),
                path + ".artifactSha256", errors);
        requireDigest(step.manifestHash(), jsonHash(plugin.getManifestJson()),
                path + ".manifestHash", errors);
        requireDigest(step.schemaHash(), jsonHash(plugin.getConfigSchemaJson()),
                path + ".schemaHash", errors);
    }

    private void requireDigest(String snapshot, String actual, String path, List<String> errors) {
        if (!StringUtils.hasText(snapshot) || !snapshot.equalsIgnoreCase(actual)) {
            errors.add(path + "与固定插件制品不一致");
        }
    }

    private String jsonHash(String json) {
        try {
            return ConnectorSnapshotIntegrity.sha256(objectMapper, objectMapper.readTree(json));
        } catch (Exception exception) {
            throw new IllegalStateException("插件完整性元数据损坏", exception);
        }
    }

    private String normalizeDigest(String digest) {
        return StringUtils.hasText(digest) ? digest.trim().toLowerCase(java.util.Locale.ROOT) : null;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("连接器配置无法规范化", exception);
        }
    }

    private void ensureRollbackPluginsReady(List<ConnectorPipelineStepDTO> pipeline) {
        Set<String> processed = new HashSet<>();
        for (ConnectorPipelineStepDTO step : pipeline) {
            if (Boolean.FALSE.equals(step.enabled())) continue;
            String key = step.pluginId() + ":" + step.pluginVersion();
            if (!processed.add(key)) continue;
            ConnectorPluginStageReqDTO request = new ConnectorPluginStageReqDTO();
            request.setPluginId(step.pluginId());
            request.setPluginVersion(step.pluginVersion());
            Result<ConnectorPluginActivationSummaryDTO> response = activationClient.stage(request);
            ConnectorPluginActivationSummaryDTO summary = response != null ? response.getData() : null;
            if (summary == null || !Boolean.TRUE.equals(summary.getReady())) {
                throw new ConnectorConflictException("历史插件版本正在预加载，请全部Access实例就绪后重试回滚");
            }
        }
    }

    private void recordTestFact(Long vendorConfigId, VendorConnectorVersion draft,
                                List<ConnectorPipelineStepDTO> pipeline, String snapshotHash,
                                VendorConnectorTestResultDTO result, Long actorId) {
        VendorConnectorTestFact fact = new VendorConnectorTestFact();
        fact.setVendorConfigId(vendorConfigId);
        fact.setDraftVersion(draft.getDraftVersion());
        fact.setSnapshotHash(snapshotHash);
        fact.setPluginBindings(writeJson(pluginBindings(pipeline)));
        fact.setTestSucceeded(Boolean.TRUE.equals(result.success()));
        fact.setSafeErrorCategory(result.errorCategory());
        fact.setSafeErrorCode(result.errorCode());
        fact.setResultDigest(sha256(writeJson(Map.of(
                "success", Boolean.TRUE.equals(result.success()),
                "errorCategory", result.errorCategory() == null ? "" : result.errorCategory(),
                "errorCode", result.errorCode() == null ? "" : result.errorCode(),
                "stageTimings", result.stageTimings()))));
        fact.setTestedBy(actorId);
        fact.setTestedAt(LocalDateTime.now());
        testFactMapper.insert(fact);
    }

    private void requireSuccessfulTestFact(Long vendorConfigId, Integer draftVersion, String snapshotHash) {
        boolean matched = testFactMapper.selectList(new LambdaQueryWrapper<VendorConnectorTestFact>()
                        .eq(VendorConnectorTestFact::getVendorConfigId, vendorConfigId)
                        .eq(VendorConnectorTestFact::getTestSucceeded, true))
                .stream().anyMatch(fact -> draftVersion.equals(fact.getDraftVersion())
                        && snapshotHash.equals(fact.getSnapshotHash()));
        if (!matched) {
            throw new ConnectorConflictException("当前草稿版本尚无对应的成功受控测试，请重新测试后发布");
        }
    }

    private List<String> pluginBindings(List<ConnectorPipelineStepDTO> pipeline) {
        return pipeline.stream().filter(step -> !Boolean.FALSE.equals(step.enabled()))
                .map(step -> step.pluginId() + ":" + step.pluginVersion())
                .distinct().sorted().toList();
    }

    private enum PluginBindingMode {
        DRAFT,
        TEST,
        PUBLISH,
        ROLLBACK
    }
}
