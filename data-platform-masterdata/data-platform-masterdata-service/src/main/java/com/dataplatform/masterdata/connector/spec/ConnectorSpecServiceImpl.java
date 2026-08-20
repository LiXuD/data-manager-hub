package com.dataplatform.masterdata.connector.spec;

import com.dataplatform.access.connector.api.dto.ConnectorTestPipelineStepDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginStageReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.runtime.ConnectorSnapshotIntegrity;
import com.dataplatform.common.plugin.runtime.ConnectorStageDefinition;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorMetadata;
import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.dataplatform.common.security.pipeline.SecurityStepType;
import com.dataplatform.masterdata.connector.api.dto.ConnectorExecutionPlanDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecCatalogDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConversionPreviewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConvertRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDraftViewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecHistoryDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecPublishRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecRollbackRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecSaveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecTestRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecValidationDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecVersionDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorStageTimingDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestResultDTO;
import com.dataplatform.masterdata.connector.compiler.ConnectorCompilationPurpose;
import com.dataplatform.masterdata.connector.compiler.ConnectorPluginCatalogStatus;
import com.dataplatform.masterdata.connector.compiler.ConnectorSpecCompilationInput;
import com.dataplatform.masterdata.connector.compiler.ConnectorSpecCompilationResult;
import com.dataplatform.masterdata.connector.compiler.ConnectorSpecCompiler;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator;
import com.dataplatform.masterdata.connector.service.LegacyHttpConversionPolicy;
import com.dataplatform.masterdata.connector.service.LegacyHttpConversionResult;
import com.dataplatform.masterdata.connector.service.LegacyHttpSpecConverter;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectorSpecServiceImpl implements ConnectorSpecService {
    private static final String SIMPLE = "SIMPLE_CONNECTOR";
    private static final String LEGACY = "ADVANCED_LEGACY";
    private static final Pattern SECRET_REF = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,255}");
    private static final Set<String> SECURITY_STEP_FIELDS = Set.of(
            "id", "stepKey", "direction", "stepType", "stepName", "sortNo", "enabled", "config");
    private static final int MAX_TEST_PAYLOAD_BYTES = 64 * 1024;
    private static final long MAX_STAGE_DURATION_MS = 86_400_000L;

    private final ConnectorSpecFactsMapper factsMapper;
    private final ConnectorSpecDraftMapper draftMapper;
    private final ConnectorSpecLifecycleMapper lifecycleMapper;
    private final ConnectorSpecPublishMapper publishMapper;
    private final VendorConnectorRuntimeInternalFeignClient runtimeClient;
    private final ConnectorPluginActivationInternalFeignClient activationClient;
    private final ConnectorPluginReleaseCoordinator releaseCoordinator;
    private final ObjectMapper objectMapper;
    private final ConnectorSpecCompiler compiler;
    private final ConnectorSpecMetrics metrics;
    private final LegacyHttpSpecConverter legacyConverter;

    public ConnectorSpecServiceImpl(ConnectorSpecFactsMapper factsMapper,
                                    ConnectorSpecDraftMapper draftMapper,
                                    ConnectorSpecLifecycleMapper lifecycleMapper,
                                    ConnectorSpecPublishMapper publishMapper,
                                    VendorConnectorRuntimeInternalFeignClient runtimeClient,
                                    ConnectorPluginActivationInternalFeignClient activationClient,
                                    ConnectorPluginReleaseCoordinator releaseCoordinator,
                                    ObjectMapper objectMapper,
                                    ConnectorSpecMetrics metrics,
                                    LegacyHttpSpecConverter legacyConverter) {
        this.factsMapper = factsMapper;
        this.draftMapper = draftMapper;
        this.lifecycleMapper = lifecycleMapper;
        this.publishMapper = publishMapper;
        this.runtimeClient = runtimeClient;
        this.activationClient = activationClient;
        this.releaseCoordinator = releaseCoordinator;
        this.objectMapper = objectMapper;
        this.compiler = new ConnectorSpecCompiler(objectMapper);
        this.metrics = metrics;
        this.legacyConverter = legacyConverter;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecCatalogDTO catalog(Long configId) {
        TransactionFacts facts = loadFacts(configId);
        List<Candidate> candidates = compatibleCandidates(facts, null);
        Map<String, List<Candidate>> grouped = new TreeMap<>();
        candidates.forEach(item -> grouped.computeIfAbsent(item.entity().getPluginId(), ignored ->
                new ArrayList<>()).add(item));
        List<ConnectorSpecCatalogDTO.Entry> result = new ArrayList<>();
        grouped.forEach((pluginId, versions) -> {
            versions.sort(candidateComparator());
            ConnectorSpecFactsMapper.CatalogPluginFacts plugin = requirePluginFacts(
                    pluginId, versions.stream().map(Candidate::manifest).toList());
            Candidate recommended = versions.stream().filter(Candidate::active)
                    .max((left, right) -> compareSemver(left.entity().getVersion(),
                            right.entity().getVersion())).orElse(null);
            Candidate representative = recommended == null ? versions.getFirst() : recommended;
            result.add(new ConnectorSpecCatalogDTO.Entry(pluginId, plugin.getDisplayName(),
                    plugin.getProvider(), plugin.getDescription(), representative.manifest().connectorKind().name(),
                    representative.manifest().transportMode().name(), representative.manifest().outputMode().name(),
                    recommended == null ? null : recommended.entity().getVersion(), versions.size(),
                    schemaMap(representative.manifest().configSchema()), compatibility(representative.manifest())));
        });
        Map<String, Integer> ranks = new LinkedHashMap<>();
        candidates.forEach(item -> ranks.merge(item.entity().getPluginId(), matchRank(item.manifest(), facts), Math::min));
        result.sort(Comparator.comparingInt((ConnectorSpecCatalogDTO.Entry item) -> ranks.get(item.pluginId()))
                .thenComparing(ConnectorSpecCatalogDTO.Entry::pluginId));
        return new ConnectorSpecCatalogDTO(result);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ConnectorSpecCatalogDTO.Version> versions(Long configId, String pluginId) {
        if (pluginId == null || pluginId.isBlank()) throw new IllegalArgumentException("插件ID不能为空");
        TransactionFacts facts = loadFacts(configId);
        List<Candidate> candidates = compatibleCandidates(facts, pluginId);
        if (candidates.isEmpty()) throw new ConnectorSpecNotFoundException("兼容的连接器插件版本不存在");
        ConnectorSpecFactsMapper.CatalogPluginFacts plugin = requirePluginFacts(
                pluginId, candidates.stream().map(Candidate::manifest).toList());
        candidates.sort(candidateComparator());
        return candidates.stream().map(item -> new ConnectorSpecCatalogDTO.Version(
                item.entity().getPluginId(), item.entity().getVersion(), item.entity().getStatus(),
                true, item.active(), plugin.getDisplayName(), plugin.getProvider(), plugin.getDescription(),
                item.manifest().connectorKind().name(), item.manifest().transportMode().name(),
                item.manifest().outputMode().name(), schemaMap(item.manifest().configSchema()),
                compatibility(item.manifest()))).toList();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecDraftViewDTO draft(Long configId) {
        TransactionFacts facts = loadFacts(configId);
        VendorConnectorVersion draft = factsMapper.findDraft(configId);
        return draft == null ? ConnectorSpecDraftViewDTO.empty(configId) : view(draft, facts);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecDraftViewDTO saveDraft(Long configId, ConnectorSpecSaveRequestDTO request,
                                               Long actorId) {
        validateSaveRequest(request);
        if (actorId == null || actorId <= 0) throw new IllegalArgumentException("ACTOR_ID_INVALID");
        TransactionFacts facts = loadFacts(configId);
        VendorConnectorVersion current = factsMapper.findDraft(configId);
        if (current != null && LEGACY.equals(current.getAuthoringMode())) {
            throw new ConnectorConflictException("LEGACY_PIPELINE_REQUIRES_CONVERSION");
        }
        if (current != null && !SIMPLE.equals(current.getAuthoringMode())) {
            throw new ConnectorConflictException("CONNECTOR_DRAFT_AUTHORING_MODE_INVALID");
        }
        int expected = request.getExpectedDraftVersion();
        if (expected == Integer.MAX_VALUE) throw new IllegalArgumentException("DRAFT_VERSION_OVERFLOW");
        if (current == null && expected != 0
                || current != null && !Objects.equals(current.getDraftVersion(), expected)) {
            throw new ConnectorConflictException("连接器草稿版本冲突");
        }
        BoundCompilation compiled = compile(facts, request.getConnectorSpec(),
                ConnectorCompilationPurpose.DRAFT);
        String pipeline = writeJson(compiled.result().pipelineSteps());
        if (current == null) {
            ConnectorSpecDraftMapper.DraftWrite write = new ConnectorSpecDraftMapper.DraftWrite();
            write.setVendorConfigId(configId);
            write.setPipelineSnapshot(pipeline);
            write.setConnectorSpec(compiled.result().canonicalSpec());
            write.setSpecHash(compiled.result().specHash());
            write.setCompilerVersion(compiled.result().compilerVersion());
            write.setCompileHash(compiled.result().compileHash());
            write.setSecurityVersion(Math.toIntExact(compiled.result().securityVersion()));
            write.setActorId(actorId);
            if (draftMapper.insertDraft(write) != 1) {
                throw new ConnectorConflictException("连接器草稿版本冲突");
            }
        } else if (draftMapper.updateDraft(current.getId(), configId, expected, expected + 1,
                pipeline, compiled.result().canonicalSpec(), compiled.result().specHash(),
                compiled.result().compilerVersion(), compiled.result().compileHash(),
                Math.toIntExact(compiled.result().securityVersion()), actorId) != 1) {
            throw new ConnectorConflictException("连接器草稿版本冲突");
        }
        VendorConnectorVersion saved = factsMapper.findDraft(configId);
        if (saved == null) throw new IllegalStateException("CONNECTOR_DRAFT_WRITE_LOST");
        return view(saved, facts);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecValidationDTO validate(Long configId) {
        try {
            TransactionFacts facts = loadFacts(configId);
            VendorConnectorVersion draft = factsMapper.findDraft(configId);
            if (draft == null) throw new ConnectorSpecNotFoundException("连接器草稿不存在");
            if (!SIMPLE.equals(draft.getAuthoringMode())) {
                return invalidValidation("LEGACY_PIPELINE_REQUIRES_CONVERSION");
            }
            ConnectorSpecDTO spec = readSpec(draft.getConnectorSpec());
            BoundCompilation compiled = compile(facts, spec, ConnectorCompilationPurpose.VALIDATE);
            if (!storedCompilationMatches(draft, compiled.result())) {
                return invalidValidation("CONNECTOR_DRAFT_FACTS_DRIFTED");
            }
            return new ConnectorSpecValidationDTO(true, null, compiled.result().specHash(),
                    compiled.result().compilerVersion(), compiled.result().compileHash(),
                    compiled.result().snapshotHash());
        } catch (ConnectorSpecNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return invalidValidation(safeErrorCode(exception));
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConnectorExecutionPlanDTO executionPlan(Long configId, Integer version) {
        TransactionFacts facts = loadFacts(configId);
        VendorConnectorVersion selected;
        if (version != null) {
            if (version <= 0) throw new IllegalArgumentException("历史版本必须为正整数");
            selected = factsMapper.findHistory(configId, version);
        } else {
            selected = factsMapper.findDraft(configId);
            if (selected == null && facts.vendor().getActiveConnectorVersionId() != null) {
                selected = factsMapper.findConnectorById(configId,
                        facts.vendor().getActiveConnectorVersionId());
            }
        }
        if (selected == null) throw new ConnectorSpecNotFoundException("连接器执行计划不存在");
        List<ConnectorPipelineStepDTO> steps = readPipeline(selected.getPipelineSnapshot());
        String snapshotHash;
        if (SIMPLE.equals(selected.getAuthoringMode())) {
            assertSimplePlanPluginsBound(steps);
            snapshotHash = assertStoredSimple(selected, steps);
        } else {
            snapshotHash = selected.getSnapshotHash();
        }
        List<ConnectorExecutionPlanDTO.Stage> redacted = steps.stream()
                .filter(step -> !Boolean.FALSE.equals(step.enabled()))
                .map(step -> new ConnectorExecutionPlanDTO.Stage(step.stageKey(), step.capability(),
                        step.pluginId(), step.pluginVersion(), step.order(), step.configHash(),
                        prefix(step.artifactSha256()), prefix(step.manifestHash()), prefix(step.schemaHash()),
                        source(step))).toList();
        return new ConnectorExecutionPlanDTO(selected.getId(), selected.getVersionNo(),
                selected.getDraftVersion(), selected.getAuthoringMode(), prefix(snapshotHash), redacted);
    }

    @Override
    public VendorConnectorTestResultDTO test(
            Long configId, ConnectorSpecTestRequestDTO request, Long actorId) {
        if (actorId == null || actorId <= 0) throw new IllegalArgumentException("ACTOR_ID_INVALID");
        if (request == null || !request.unknownFieldNames().isEmpty()) {
            throw new IllegalArgumentException("CONNECTOR_SPEC_TEST_REQUEST_INVALID");
        }
        Map<String, Object> params = jsonObjectSnapshot(request.getParams(), "CONNECTOR_TEST_PARAMS_INVALID");
        if (writeJson(params).getBytes(StandardCharsets.UTF_8).length > MAX_TEST_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("CONNECTOR_TEST_PARAMS_TOO_LARGE");
        }
        TransactionFacts facts = loadFacts(configId);
        VendorConnectorVersion draft = factsMapper.findDraft(configId);
        if (draft == null) throw new ConnectorSpecNotFoundException("连接器草稿不存在");
        if (!SIMPLE.equals(draft.getAuthoringMode())) {
            throw new ConnectorConflictException("LEGACY_PIPELINE_REQUIRES_CONVERSION");
        }
        BoundCompilation compiled = compile(facts, readSpec(draft.getConnectorSpec()),
                ConnectorCompilationPurpose.TEST);
        if (!storedCompilationMatches(draft, compiled.result())
                || !jsonEquals(draft.getConnectorSpec(), compiled.result().canonicalSpec())) {
            throw new ConnectorConflictException("CONNECTOR_DRAFT_FACTS_DRIFTED");
        }

        VendorConnectorTestReqDTO accessRequest = new VendorConnectorTestReqDTO();
        accessRequest.setVendorConfigId(configId);
        accessRequest.setParams(params);
        accessRequest.setPipelineSnapshot(compiled.result().pipelineSteps().stream()
                .map(this::toAccessTestStep).toList());
        accessRequest.setSnapshotHash(compiled.result().snapshotHash());
        accessRequest.setHashAlgorithm("V2_EMBEDDED");
        accessRequest.setIntegrityHash(compiled.result().snapshotHash());
        Result<VendorConnectorTestRespDTO> result = runtimeClient.test(accessRequest);
        VendorConnectorTestResultDTO validated = validateTestResponse(result, compiled.result());
        ConnectorSpecLifecycleMapper.TestFactWrite fact = testFact(
                draft, compiled.result(), validated, actorId);
        if (lifecycleMapper.insertTestFact(fact) != 1) {
            throw new ConnectorConflictException("CONNECTOR_DRAFT_CHANGED_DURING_TEST");
        }
        return validated;
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecVersionDTO publish(
            Long configId, ConnectorSpecPublishRequestDTO request, Long actorId) {
        validatePublishRequest(configId, request, actorId);
        ConnectorSpecPublishMapper.ControlFacts control = publishMapper.lockControl(configId);
        if (control == null) throw new ConnectorSpecNotFoundException("厂商接口配置不存在");
        if (!Objects.equals(configId, control.getId()) || !"PLUGIN".equals(control.getRuntimeMode())
                || control.getConnectorVersion() == null || control.getConnectorVersion() < 0
                || control.getConnectorVersion() == Integer.MAX_VALUE) {
            throw new IllegalStateException("CONNECTOR_CONTROL_FACTS_INVALID");
        }

        VendorConnectorVersion draft = publishMapper.lockDraft(configId);
        if (draft == null) throw new ConnectorSpecNotFoundException("连接器草稿不存在");
        if (draft.getId() == null || draft.getId() <= 0
                || !Objects.equals(configId, draft.getVendorConfigId())
                || !"DRAFT".equals(draft.getStatus()) || !SIMPLE.equals(draft.getAuthoringMode())) {
            throw new ConnectorConflictException("CONNECTOR_DRAFT_FACTS_INVALID");
        }
        if (!Objects.equals(request.getExpectedDraftVersion(), draft.getDraftVersion())) {
            throw new ConnectorConflictException("连接器草稿版本冲突");
        }
        if (draft.getSnapshotHash() != null || draft.getHashAlgorithm() != null
                || draft.getIntegrityHash() != null) {
            throw new IllegalStateException("CONNECTOR_DRAFT_PUBLISHED_FACTS_INVALID");
        }

        TransactionFacts facts = loadFacts(configId);
        BoundCompilation compiled = compile(facts, readSpec(draft.getConnectorSpec()),
                ConnectorCompilationPurpose.PUBLISH);
        if (!storedCompilationMatches(draft, compiled.result())
                || !jsonEquals(draft.getConnectorSpec(), compiled.result().canonicalSpec())) {
            throw new ConnectorConflictException("CONNECTOR_DRAFT_FACTS_DRIFTED");
        }
        if (!publishMapper.hasSuccessfulTestFact(configId, draft.getDraftVersion(),
                compiled.result().specHash(), compiled.result().snapshotHash(),
                compiled.result().compileHash())) {
            throw new ConnectorConflictException("CONNECTOR_SUCCESSFUL_TEST_REQUIRED");
        }
        ensurePluginsReady(compiled.result().pipelineSteps());

        VendorConnectorVersion current = requireActiveBinding(
                configId, control.getActiveConnectorVersionId(), publishMapper.lockActive(configId));
        if (current != null && SIMPLE.equals(current.getAuthoringMode())
                && Objects.equals(current.getCompileHash(), compiled.result().compileHash())
                && Objects.equals(current.getSnapshotHash(), compiled.result().snapshotHash())) {
            throw new ConnectorConflictException("CONNECTOR_VERSION_ALREADY_ACTIVE");
        }
        Integer maxVersion = publishMapper.maxVersionNo(configId);
        if (maxVersion == null || maxVersion < 0 || maxVersion == Integer.MAX_VALUE) {
            throw new IllegalStateException("CONNECTOR_VERSION_SEQUENCE_INVALID");
        }

        LocalDateTime publishedAt = LocalDateTime.now();
        ConnectorSpecPublishMapper.PublishedWrite row = publishedWrite(
                draft, compiled.result(), maxVersion + 1,
                current == null ? null : current.getId(), actorId, publishedAt);
        if (publishMapper.insertPublished(row) != 1 || row.getId() == null || row.getId() <= 0) {
            throw new ConnectorConflictException("CONNECTOR_DRAFT_CHANGED_DURING_PUBLISH");
        }
        if (current != null && publishMapper.supersedeActive(
                current.getId(), configId, actorId, publishedAt) != 1) {
            throw new ConnectorConflictException("ACTIVE_CONNECTOR_CHANGED_DURING_PUBLISH");
        }
        if (publishMapper.casActivePointer(configId, control.getConnectorVersion(),
                control.getActiveConnectorVersionId(), row.getId(), publishedAt) != 1) {
            throw new ConnectorConflictException("ACTIVE_CONNECTOR_POINTER_CHANGED_DURING_PUBLISH");
        }
        releaseCoordinator.reconcileAfterCommit();
        return new ConnectorSpecVersionDTO(row.getId(), row.getVendorConfigId(), row.getVersionNo(),
                SIMPLE, row.getSpecHash(), row.getCompilerVersion(), row.getCompileHash(),
                row.getSnapshotHash(), "V2_EMBEDDED", row.getSnapshotHash(), row.getSecurityVersion(),
                "ACTIVE", row.getPreviousVersionId(), row.getPublishedAt(), row.getActorId());
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecHistoryDTO history(Long configId) {
        requireVendorConfig(configId);
        List<VendorConnectorVersion> versions = publishMapper.findHistoryVersions(configId);
        if (versions == null) throw new IllegalStateException("CONNECTOR_HISTORY_FACTS_INVALID");
        List<ConnectorSpecHistoryDTO.Version> result = new ArrayList<>();
        Integer previous = null;
        Set<Integer> versionNumbers = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (VendorConnectorVersion version : versions) {
            if (version == null || !Objects.equals(configId, version.getVendorConfigId())
                    || version.getVersionNo() == null || version.getVersionNo() <= 0
                    || "DRAFT".equals(version.getStatus())
                    || previous != null && version.getVersionNo() > previous
                    || !versionNumbers.add(version.getVersionNo()) || !ids.add(version.getId())) {
                throw new IllegalStateException("CONNECTOR_HISTORY_FACTS_INVALID");
            }
            previous = version.getVersionNo();
            result.add(historyVersion(version));
        }
        return new ConnectorSpecHistoryDTO(result);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecVersionDTO rollback(
            Long configId, Integer version, ConnectorSpecRollbackRequestDTO request, Long actorId) {
        validateRollbackRequest(configId, version, request, actorId);
        ConnectorSpecPublishMapper.ControlFacts control = publishMapper.lockControl(configId);
        validateRollbackControl(configId, request, control);

        VendorConnectorVersion target = publishMapper.lockTarget(configId, version);
        if (target == null) throw new ConnectorSpecNotFoundException("连接器历史版本不存在");
        validateRollbackTarget(configId, version, target);

        VendorConnectorVersion current = requireActiveBinding(
                configId, control.getActiveConnectorVersionId(), publishMapper.lockActive(configId));
        if (current != null && Objects.equals(current.getId(), target.getId())) {
            throw new ConnectorConflictException("CONNECTOR_VERSION_ALREADY_ACTIVE");
        }

        List<ConnectorPipelineStepDTO> steps = readPipeline(target.getPipelineSnapshot());
        if (SIMPLE.equals(target.getAuthoringMode())) {
            assertSimplePlanPluginsBound(steps);
            assertStoredSimple(target, steps);
        } else if (LEGACY.equals(target.getAuthoringMode())) {
            validateLegacyPipeline(steps);
        } else {
            throw new IllegalStateException("CONNECTOR_HISTORY_AUTHORING_MODE_INVALID");
        }
        ensurePluginsReady(steps);

        Integer maxVersion = publishMapper.maxVersionNo(configId);
        if (maxVersion == null || maxVersion < 0 || maxVersion == Integer.MAX_VALUE) {
            throw new IllegalStateException("CONNECTOR_VERSION_SEQUENCE_INVALID");
        }
        LocalDateTime publishedAt = LocalDateTime.now();
        ConnectorSpecPublishMapper.RollbackWrite row = rollbackWrite(target, maxVersion + 1,
                current == null ? null : current.getId(), actorId, publishedAt);
        if (publishMapper.insertRollback(row) != 1 || row.getId() == null || row.getId() <= 0) {
            throw new ConnectorConflictException("CONNECTOR_TARGET_CHANGED_DURING_ROLLBACK");
        }
        if (current != null && publishMapper.supersedeActive(
                current.getId(), configId, actorId, publishedAt) != 1) {
            throw new ConnectorConflictException("ACTIVE_CONNECTOR_CHANGED_DURING_ROLLBACK");
        }
        if (publishMapper.casActivePointer(configId, control.getConnectorVersion(),
                control.getActiveConnectorVersionId(), row.getId(), publishedAt) != 1) {
            throw new ConnectorConflictException("ACTIVE_CONNECTOR_POINTER_CHANGED_DURING_ROLLBACK");
        }
        releaseCoordinator.reconcileAfterCommit();
        return new ConnectorSpecVersionDTO(row.getId(), row.getVendorConfigId(), row.getVersionNo(),
                row.getAuthoringMode(), row.getSpecHash(), row.getCompilerVersion(), row.getCompileHash(),
                row.getSnapshotHash(), row.getHashAlgorithm(), row.getIntegrityHash(), row.getSecurityVersion(),
                "ACTIVE", row.getPreviousVersionId(), row.getPublishedAt(), row.getActorId());
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecUpgradePreviewDTO upgradePreview(
            Long configId, ConnectorSpecUpgradePreviewRequestDTO request) {
        validateUpgradePreviewRequest(configId, request);
        TransactionFacts facts = loadFacts(configId);
        VendorConnectorVersion draft = factsMapper.findDraft(configId);
        if (draft == null) throw new ConnectorSpecNotFoundException("连接器草稿不存在");
        if (LEGACY.equals(draft.getAuthoringMode())) {
            throw new ConnectorConflictException("LEGACY_PIPELINE_REQUIRES_CONVERSION");
        }
        if (!SIMPLE.equals(draft.getAuthoringMode()) || draft.getId() == null || draft.getId() <= 0
                || !Objects.equals(configId, draft.getVendorConfigId())
                || !"DRAFT".equals(draft.getStatus())) {
            throw new IllegalStateException("CONNECTOR_DRAFT_FACTS_INVALID");
        }
        if (!Objects.equals(request.getExpectedDraftVersion(), draft.getDraftVersion())) {
            throw new ConnectorConflictException("连接器草稿版本冲突");
        }

        ConnectorSpecDTO currentSpec = readSpec(draft.getConnectorSpec());
        BoundCompilation current = compile(facts, currentSpec, ConnectorCompilationPurpose.VALIDATE);
        if (!storedCompilationMatches(draft, current.result())
                || !jsonEquals(draft.getConnectorSpec(), current.result().canonicalSpec())) {
            throw new IllegalStateException("CONNECTOR_DRAFT_FACTS_DRIFTED");
        }
        String pluginId = currentSpec.getPlugin().getPluginId();
        String currentVersion = currentSpec.getPlugin().getPluginVersion();
        String targetVersion = request.getTargetPluginVersion();
        if (Objects.equals(currentVersion, targetVersion)) {
            throw new ConnectorConflictException("CONNECTOR_PLUGIN_VERSION_UNCHANGED");
        }
        validateExplicitPluginVersion(currentVersion);
        validateExplicitPluginVersion(targetVersion);
        if (compareUpgradeVersions(targetVersion, currentVersion) < 0) {
            throw new ConnectorConflictException("CONNECTOR_PLUGIN_VERSION_DOWNGRADE_FORBIDDEN");
        }

        ConnectorPluginVersion targetEntity = factsMapper.findPluginVersion(pluginId, targetVersion);
        if (targetEntity == null) throw new ConnectorSpecNotFoundException("目标连接器插件版本不存在");
        PluginManifest targetManifest = bindManifest(targetEntity);
        requireBindablePlugin(pluginId, List.of(targetManifest));
        if (!("ACTIVE".equals(targetEntity.getStatus()) || "STAGING".equals(targetEntity.getStatus()))) {
            throw new ConnectorConflictException("TARGET_PLUGIN_STATUS_INVALID");
        }
        if (!Objects.equals(pluginId, targetEntity.getPluginId())
                || !Objects.equals(targetVersion, targetManifest.version())) {
            throw new IllegalStateException("PLUGIN_SIGNED_PROJECTION_DRIFT");
        }
        if (!targetManifest.compatibility().supportsVendor(facts.vendor().getVendorCode())
                || !targetManifest.compatibility().supportsDataType(facts.vendor().getDataTypeCode())) {
            throw new ConnectorConflictException("TARGET_PLUGIN_COMPATIBILITY_MISMATCH");
        }

        ConnectorSpecDTO targetSpec = copySpecWithVersion(currentSpec, targetVersion);
        ConnectorSpecUpgradePreviewDTO.PluginCoordinate currentCoordinate =
                new ConnectorSpecUpgradePreviewDTO.PluginCoordinate(pluginId, currentVersion);
        ConnectorSpecUpgradePreviewDTO.PluginCoordinate targetCoordinate =
                new ConnectorSpecUpgradePreviewDTO.PluginCoordinate(pluginId, targetVersion);
        List<ConnectorSpecUpgradePreviewDTO.SchemaChange> schemaChanges = schemaDiff(
                current.manifest().configSchema(), targetManifest.configSchema());
        List<ConnectorSpecUpgradePreviewDTO.ConfigChange> configChanges = configDiff(
                targetSpec.getConfig(), current.manifest().configSchema(),
                targetManifest.configSchema(), schemaChanges);
        try {
            BoundCompilation preview = compile(facts, targetSpec, ConnectorCompilationPurpose.VALIDATE);
            return new ConnectorSpecUpgradePreviewDTO(currentCoordinate, targetCoordinate, true,
                    null, null, schemaChanges, configChanges,
                    planDiff(current.result().pipelineSteps(), preview.result().pipelineSteps()),
                    preview.result().specHash(), preview.result().snapshotHash(),
                    preview.result().compileHash());
        } catch (IllegalArgumentException exception) {
            String code = safeErrorCode(exception);
            if (!previewValidationError(code)) throw exception;
            return new ConnectorSpecUpgradePreviewDTO(currentCoordinate, targetCoordinate, false,
                    code, safePreviewMessage(code), schemaChanges, configChanges,
                    null, null, null, null);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecConversionPreviewDTO convertPreview(Long configId) {
        TransactionFacts facts = loadFacts(configId);
        VendorConnectorVersion draft = factsMapper.findDraft(configId);
        if (draft == null) throw new ConnectorSpecNotFoundException("连接器草稿不存在");
        requireLegacyDraft(configId, draft);
        LegacyHttpConversionResult result = legacyConverter.convert(
                readPipeline(draft.getPipelineSnapshot()), conversionPolicy(facts));
        return conversionPreview(result);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ConnectorSpecDraftViewDTO convert(
            Long configId, ConnectorSpecConvertRequestDTO request, Long actorId) {
        validateConvertRequest(configId, request, actorId);
        ConnectorSpecPublishMapper.ControlFacts control = publishMapper.lockControl(configId);
        if (control == null) throw new ConnectorSpecNotFoundException("厂商接口配置不存在");
        if (!Objects.equals(configId, control.getId()) || !"PLUGIN".equals(control.getRuntimeMode())) {
            throw new IllegalStateException("CONNECTOR_CONTROL_FACTS_INVALID");
        }
        VendorConnectorVersion draft = publishMapper.lockDraft(configId);
        if (draft == null) throw new ConnectorSpecNotFoundException("连接器草稿不存在");
        requireLegacyDraft(configId, draft);
        if (!Objects.equals(request.getExpectedDraftVersion(), draft.getDraftVersion())) {
            throw new ConnectorConflictException("连接器草稿版本冲突");
        }
        if (draft.getDraftVersion() == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("DRAFT_VERSION_OVERFLOW");
        }

        TransactionFacts facts = loadFacts(configId);
        try {
            LegacyHttpConversionResult conversion = legacyConverter.convert(
                    readPipeline(draft.getPipelineSnapshot()), conversionPolicy(facts));
            if (!conversion.convertible()) {
                metrics.conversionFailure("NOT_CONVERTIBLE");
                throw new LegacyPipelineNotConvertibleException(conversionPreview(conversion));
            }
            BoundCompilation compiled = compile(facts, conversion.connectorSpec(),
                    ConnectorCompilationPurpose.DRAFT);
            int nextVersion = draft.getDraftVersion() + 1;
            if (draftMapper.convertLegacyDraft(draft.getId(), configId, draft.getDraftVersion(),
                    nextVersion, writeJson(compiled.result().pipelineSteps()),
                    compiled.result().canonicalSpec(), compiled.result().specHash(),
                    compiled.result().compilerVersion(), compiled.result().compileHash(),
                    Math.toIntExact(compiled.result().securityVersion()), actorId) != 1) {
                metrics.conversionFailure("CONFLICT");
                throw new ConnectorConflictException("连接器草稿版本冲突");
            }
            VendorConnectorVersion saved = factsMapper.findDraft(configId);
            if (saved == null) throw new IllegalStateException("CONNECTOR_DRAFT_WRITE_LOST");
            metrics.conversionSuccess();
            return view(saved, facts);
        } catch (ConnectorConflictException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            metrics.conversionFailure("INVALID");
            throw exception;
        }
    }

    private ConnectorSpecConversionPreviewDTO conversionPreview(LegacyHttpConversionResult result) {
        return new ConnectorSpecConversionPreviewDTO(result.convertible(),
                result.preflight().classification().name(),
                result.convertible() ? null : "LEGACY_PIPELINE_NOT_CONVERTIBLE",
                result.preflight().reasons().stream().map(reason ->
                        new ConnectorSpecConversionPreviewDTO.Reason(reason.code().name(),
                                reason.stepIndex(), reason.stageKey(), reason.detail())).toList(),
                result.connectorSpec());
    }

    private LegacyHttpConversionPolicy conversionPolicy(TransactionFacts facts) {
        Integer timeout = facts.vendor().getTimeout();
        if (timeout == null || timeout <= 0) {
            throw new IllegalStateException("CONNECTOR_PLATFORM_POLICY_INVALID");
        }
        return new LegacyHttpConversionPolicy(timeout);
    }

    private void requireLegacyDraft(Long configId, VendorConnectorVersion draft) {
        if (draft.getId() == null || draft.getId() <= 0
                || !Objects.equals(configId, draft.getVendorConfigId())
                || !"DRAFT".equals(draft.getStatus())) {
            throw new IllegalStateException("CONNECTOR_DRAFT_FACTS_INVALID");
        }
        if (SIMPLE.equals(draft.getAuthoringMode())) {
            throw new ConnectorConflictException("CONNECTOR_DRAFT_ALREADY_SIMPLE");
        }
        if (!LEGACY.equals(draft.getAuthoringMode()) || draft.getDraftVersion() == null
                || draft.getDraftVersion() <= 0) {
            throw new IllegalStateException("CONNECTOR_DRAFT_FACTS_INVALID");
        }
    }

    private void validateConvertRequest(
            Long configId, ConnectorSpecConvertRequestDTO request, Long actorId) {
        if (configId == null || configId <= 0 || request == null
                || !request.unknownFieldNames().isEmpty()
                || request.getExpectedDraftVersion() == null
                || request.getExpectedDraftVersion() <= 0) {
            throw new IllegalArgumentException("CONNECTOR_SPEC_CONVERT_REQUEST_INVALID");
        }
        if (actorId == null || actorId <= 0) throw new IllegalArgumentException("ACTOR_ID_INVALID");
    }

    private void validateUpgradePreviewRequest(
            Long configId, ConnectorSpecUpgradePreviewRequestDTO request) {
        if (configId == null || configId <= 0 || request == null
                || !request.unknownFieldNames().isEmpty()
                || request.getExpectedDraftVersion() == null
                || request.getExpectedDraftVersion() <= 0
                || request.getTargetPluginVersion() == null
                || request.getTargetPluginVersion().isBlank()
                || !request.getTargetPluginVersion().equals(request.getTargetPluginVersion().trim())) {
            throw new IllegalArgumentException("CONNECTOR_UPGRADE_PREVIEW_REQUEST_INVALID");
        }
    }

    private void validateExplicitPluginVersion(String version) {
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")) {
            throw new IllegalArgumentException("TARGET_PLUGIN_VERSION_INVALID");
        }
    }

    private int compareUpgradeVersions(String left, String right) {
        String[] leftBuild = left.split("\\+", 2);
        String[] rightBuild = right.split("\\+", 2);
        String[] leftPre = leftBuild[0].split("-", 2);
        String[] rightPre = rightBuild[0].split("-", 2);
        String[] leftCore = leftPre[0].split("\\.");
        String[] rightCore = rightPre[0].split("\\.");
        for (int index = 0; index < 3; index++) {
            int compared = new BigInteger(leftCore[index]).compareTo(new BigInteger(rightCore[index]));
            if (compared != 0) return compared;
        }
        if (leftPre.length == 1 && rightPre.length == 1) return 0;
        if (leftPre.length == 1) return 1;
        if (rightPre.length == 1) return -1;
        String[] leftParts = leftPre[1].split("\\.");
        String[] rightParts = rightPre[1].split("\\.");
        for (int index = 0; index < Math.min(leftParts.length, rightParts.length); index++) {
            String leftPart = leftParts[index];
            String rightPart = rightParts[index];
            boolean leftNumeric = leftPart.matches("[0-9]+");
            boolean rightNumeric = rightPart.matches("[0-9]+");
            int compared;
            if (leftNumeric && rightNumeric) {
                compared = new BigInteger(leftPart).compareTo(new BigInteger(rightPart));
            } else if (leftNumeric != rightNumeric) {
                compared = leftNumeric ? -1 : 1;
            } else {
                compared = leftPart.compareTo(rightPart);
            }
            if (compared != 0) return compared;
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private ConnectorSpecDTO copySpecWithVersion(ConnectorSpecDTO current, String targetVersion) {
        List<ConnectorSpecDTO.ResponseMapping> mappings = current.getResponseMapping() == null ? null
                : current.getResponseMapping().stream().map(mapping ->
                new ConnectorSpecDTO.ResponseMapping(mapping.getTargetField(), mapping.getSourcePath(),
                        mapping.getSourceType(), deepJsonCopy(mapping.getDefaultValue()),
                        mapping.getTransformType())).toList();
        return new ConnectorSpecDTO(current.getSpecVersion(),
                new ConnectorSpecDTO.PluginRef(current.getPlugin().getPluginId(), targetVersion),
                jsonObjectSnapshot(current.getConfig(), "CONNECTOR_SPEC_INVALID"), mappings);
    }

    private List<ConnectorSpecUpgradePreviewDTO.SchemaChange> schemaDiff(
            JsonNode currentSchema, JsonNode targetSchema) {
        Map<String, SchemaFact> current = flattenSchema(currentSchema);
        Map<String, SchemaFact> target = flattenSchema(targetSchema);
        Set<String> paths = new java.util.TreeSet<>();
        paths.addAll(current.keySet());
        paths.addAll(target.keySet());
        List<ConnectorSpecUpgradePreviewDTO.SchemaChange> changes = new ArrayList<>();
        for (String path : paths) {
            SchemaFact left = current.get(path);
            SchemaFact right = target.get(path);
            String kind = schemaChangeKind(left, right);
            if (kind == null) continue;
            boolean secret = left != null && left.secretRef() || right != null && right.secretRef();
            changes.add(new ConnectorSpecUpgradePreviewDTO.SchemaChange(path, kind,
                    left == null ? null : left.type(), right == null ? null : right.type(),
                    left != null && left.required(), right != null && right.required(), secret));
        }
        return List.copyOf(changes);
    }

    private Map<String, SchemaFact> flattenSchema(JsonNode schema) {
        Map<String, SchemaFact> result = new TreeMap<>();
        flattenSchemaObject(schema, "", false, result);
        return Map.copyOf(result);
    }

    private void flattenSchemaObject(JsonNode schema, String path, boolean required,
                                     Map<String, SchemaFact> result) {
        if (schema == null || !schema.isObject()) return;
        if (!path.isEmpty()) {
            result.put(path, new SchemaFact(schemaType(schema), required,
                    enumDigest(schema.path("enum")), schema.path("x-secret-ref").asBoolean(false)));
        }
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) return;
        Set<String> requiredNames = new HashSet<>();
        if (schema.path("required").isArray()) {
            schema.path("required").forEach(item -> {
                if (item.isTextual()) requiredNames.add(item.asText());
            });
        }
        List<String> names = new ArrayList<>();
        properties.fieldNames().forEachRemaining(names::add);
        names.stream().sorted().forEach(name -> flattenSchemaObject(properties.get(name),
                path + "/" + escapePointer(name), requiredNames.contains(name), result));
    }

    private String schemaChangeKind(SchemaFact current, SchemaFact target) {
        if (current == null) return "ADDED";
        if (target == null) return "REMOVED";
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(current.type(), target.type())) changes.add("TYPE_CHANGED");
        if (current.required() != target.required()) changes.add("REQUIRED_CHANGED");
        if (!Objects.equals(current.enumDigest(), target.enumDigest())) changes.add("ENUM_CHANGED");
        if (current.secretRef() != target.secretRef()) changes.add("SECRET_CLASSIFICATION_CHANGED");
        return changes.isEmpty() ? null : String.join("+", changes);
    }

    private List<ConnectorSpecUpgradePreviewDTO.ConfigChange> configDiff(
            Map<String, Object> config, JsonNode currentSchema, JsonNode targetSchema,
            List<ConnectorSpecUpgradePreviewDTO.SchemaChange> schemaChanges) {
        Map<String, SchemaFact> current = flattenSchema(currentSchema);
        Map<String, SchemaFact> target = flattenSchema(targetSchema);
        Set<String> paths = new java.util.TreeSet<>();
        paths.addAll(flattenConfig(objectMapper.valueToTree(config)));
        schemaChanges.stream().map(ConnectorSpecUpgradePreviewDTO.SchemaChange::path)
                .forEach(paths::add);
        List<ConnectorSpecUpgradePreviewDTO.ConfigChange> changes = new ArrayList<>();
        for (String path : paths) {
            SchemaFact left = current.get(path);
            SchemaFact right = target.get(path);
            boolean configured = configPathExists(objectMapper.valueToTree(config), path);
            String kind = configured && right == null ? "CONFIG_FIELD_REMOVED"
                    : configured && right != null && left != null
                    && !Objects.equals(left.type(), right.type()) ? "CONFIG_TYPE_CHANGED"
                    : !configured && right != null && right.required() ? "REQUIRED_VALUE_MISSING"
                    : schemaChanges.stream().anyMatch(change -> change.path().equals(path))
                    ? "SCHEMA_CHANGED" : null;
            if (kind == null) continue;
            boolean secret = left != null && left.secretRef() || right != null && right.secretRef();
            changes.add(new ConnectorSpecUpgradePreviewDTO.ConfigChange(path, kind,
                    left == null ? null : left.type(), right == null ? null : right.type(),
                    right != null && right.required(), secret));
        }
        return List.copyOf(changes);
    }

    private Set<String> flattenConfig(JsonNode config) {
        Set<String> paths = new java.util.TreeSet<>();
        flattenConfigNode(config, "", paths);
        return Set.copyOf(paths);
    }

    private void flattenConfigNode(JsonNode node, String path, Set<String> paths) {
        if (node == null || !node.isObject()) return;
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.stream().sorted().forEach(name -> {
            String child = path + "/" + escapePointer(name);
            paths.add(child);
            flattenConfigNode(node.get(name), child, paths);
        });
    }

    private boolean configPathExists(JsonNode config, String pointer) {
        return config != null && !config.at(pointer).isMissingNode();
    }

    private ConnectorSpecUpgradePreviewDTO.PlanDiff planDiff(
            List<ConnectorPipelineStepDTO> current, List<ConnectorPipelineStepDTO> target) {
        Map<String, ConnectorPipelineStepDTO> left = new TreeMap<>();
        Map<String, ConnectorPipelineStepDTO> right = new TreeMap<>();
        current.forEach(step -> left.put(step.stageKey(), step));
        target.forEach(step -> right.put(step.stageKey(), step));
        Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());
        int added = 0;
        int removed = 0;
        int coordinates = 0;
        int configHashes = 0;
        int digests = 0;
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            ConnectorPipelineStepDTO before = left.get(key);
            ConnectorPipelineStepDTO after = right.get(key);
            boolean different = false;
            if (before == null) { added++; different = true; }
            else if (after == null) { removed++; different = true; }
            else {
                if (!Objects.equals(before.pluginId(), after.pluginId())
                        || !Objects.equals(before.pluginVersion(), after.pluginVersion())) {
                    coordinates++; different = true;
                }
                if (!Objects.equals(before.configHash(), after.configHash())) {
                    configHashes++; different = true;
                }
                if (!Objects.equals(before.artifactSha256(), after.artifactSha256())
                        || !Objects.equals(before.manifestHash(), after.manifestHash())
                        || !Objects.equals(before.schemaHash(), after.schemaHash())) {
                    digests++; different = true;
                }
            }
            if (different) changed.add(key);
        }
        return new ConnectorSpecUpgradePreviewDTO.PlanDiff(
                added, removed, coordinates, configHashes, digests, changed);
    }

    private String schemaType(JsonNode schema) {
        JsonNode type = schema.path("type");
        if (type.isTextual()) return type.asText();
        if (type.isArray()) {
            List<String> values = new ArrayList<>();
            type.forEach(item -> values.add(item.asText()));
            return String.join("|", values.stream().sorted().toList());
        }
        return null;
    }

    private String enumDigest(JsonNode values) {
        if (!values.isArray()) return null;
        return ConnectorSnapshotIntegrity.sha256(new ObjectMapper(), values);
    }

    private String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private boolean previewValidationError(String code) {
        return Set.of("CONNECTOR_CONFIG_INVALID", "SECRET_REF_NOT_OWNED",
                "RESPONSE_MAPPING_INVALID", "RESPONSE_MAPPING_FORBIDDEN",
                "PLUGIN_COMPATIBILITY_MISMATCH", "SECURITY_PIPELINE_INVALID",
                "SECURITY_VERSION_INVALID", "CONNECTOR_SPEC_TOO_LARGE").contains(code);
    }

    private String safePreviewMessage(String code) {
        return switch (code) {
            case "CONNECTOR_CONFIG_INVALID" -> "当前配置不满足目标版本 Schema";
            case "SECRET_REF_NOT_OWNED" -> "目标版本引用了不可用的秘密配置";
            case "PLUGIN_COMPATIBILITY_MISMATCH" -> "目标版本与当前厂商或数据类型不兼容";
            case "SECURITY_PIPELINE_INVALID", "SECURITY_VERSION_INVALID" ->
                    "目标版本与当前安全配置不兼容";
            default -> "当前 ConnectorSpec 无法使用目标插件版本";
        };
    }

    private ConnectorSpecHistoryDTO.Version historyVersion(VendorConnectorVersion version) {
        validatePublishedMetadata(version);
        ConnectorSpecDTO spec = null;
        if (SIMPLE.equals(version.getAuthoringMode())) {
            List<ConnectorPipelineStepDTO> steps = readPipeline(version.getPipelineSnapshot());
            assertSimplePlanPluginsBound(steps);
            assertStoredSimple(version, steps);
            spec = readSpec(version.getConnectorSpec());
            validateStoredSpec(spec, steps);
        } else if (LEGACY.equals(version.getAuthoringMode())) {
            validateLegacyPipeline(readPipeline(version.getPipelineSnapshot()));
            if (version.getConnectorSpec() != null || version.getSpecHash() != null
                    || version.getCompilerVersion() != null || version.getCompileHash() != null) {
                throw new IllegalStateException("CONNECTOR_HISTORY_LEGACY_FACTS_INVALID");
            }
        } else {
            throw new IllegalStateException("CONNECTOR_HISTORY_AUTHORING_MODE_INVALID");
        }
        return new ConnectorSpecHistoryDTO.Version(version.getId(), version.getVendorConfigId(),
                version.getVersionNo(), version.getAuthoringMode(), spec, version.getSpecHash(),
                version.getCompilerVersion(), version.getCompileHash(), version.getSnapshotHash(),
                version.getHashAlgorithm(), version.getIntegrityHash(), version.getSecurityVersion(),
                version.getStatus(), version.getPreviousVersionId(), version.getPublishedAt(),
                version.getPublishedBy());
    }

    private void validatePublishedMetadata(VendorConnectorVersion version) {
        if (version.getId() == null || version.getId() <= 0
                || version.getVendorConfigId() == null || version.getVendorConfigId() <= 0
                || version.getVersionNo() == null || version.getVersionNo() <= 0
                || version.getDraftVersion() == null || version.getDraftVersion() != 0
                || version.getSecurityVersion() == null || version.getSecurityVersion() < 0
                || version.getSnapshotHash() == null || version.getSnapshotHash().isBlank()
                || version.getHashAlgorithm() == null || version.getHashAlgorithm().isBlank()
                || version.getIntegrityHash() == null || version.getIntegrityHash().isBlank()
                || version.getPreviousVersionId() != null && version.getPreviousVersionId() <= 0
                || !("ACTIVE".equals(version.getStatus()) || "SUPERSEDED".equals(version.getStatus()))
                || version.getPublishedAt() == null
                || SIMPLE.equals(version.getAuthoringMode())
                && (version.getPublishedBy() == null || version.getPublishedBy() <= 0)
                || version.getPublishedBy() != null && version.getPublishedBy() <= 0) {
            throw new IllegalStateException("CONNECTOR_HISTORY_FACTS_INVALID");
        }
    }

    private void requireVendorConfig(Long configId) {
        if (configId == null || configId <= 0) throw new IllegalArgumentException("厂商配置ID无效");
        ConnectorSpecFactsMapper.VendorFacts facts = factsMapper.findVendorFacts(configId);
        if (facts == null || !Objects.equals(configId, facts.getVendorConfigId())) {
            throw new ConnectorSpecNotFoundException("厂商接口配置不存在");
        }
    }

    private void validateRollbackRequest(Long configId, Integer version,
                                         ConnectorSpecRollbackRequestDTO request, Long actorId) {
        if (configId == null || configId <= 0 || version == null || version <= 0
                || request == null || !request.unknownFieldNames().isEmpty()
                || request.getExpectedConnectorVersion() == null
                || request.getExpectedConnectorVersion() < 0) {
            throw new IllegalArgumentException("CONNECTOR_SPEC_ROLLBACK_REQUEST_INVALID");
        }
        if (actorId == null || actorId <= 0) throw new IllegalArgumentException("ACTOR_ID_INVALID");
    }

    private void validateRollbackControl(Long configId, ConnectorSpecRollbackRequestDTO request,
                                         ConnectorSpecPublishMapper.ControlFacts control) {
        if (control == null) throw new ConnectorSpecNotFoundException("厂商接口配置不存在");
        if (!Objects.equals(configId, control.getId()) || !"PLUGIN".equals(control.getRuntimeMode())
                || control.getConnectorVersion() == null || control.getConnectorVersion() < 0
                || control.getConnectorVersion() == Integer.MAX_VALUE) {
            throw new IllegalStateException("CONNECTOR_CONTROL_FACTS_INVALID");
        }
        if (!Objects.equals(request.getExpectedConnectorVersion(), control.getConnectorVersion())) {
            throw new ConnectorConflictException("CONNECTOR_VERSION_CONFLICT");
        }
    }

    private void validateRollbackTarget(Long configId, Integer version, VendorConnectorVersion target) {
        validatePublishedMetadata(target);
        if (target.getId() == null || target.getId() <= 0
                || !Objects.equals(configId, target.getVendorConfigId())
                || !Objects.equals(version, target.getVersionNo())
                || !("ACTIVE".equals(target.getStatus()) || "SUPERSEDED".equals(target.getStatus()))) {
            throw new IllegalStateException("CONNECTOR_ROLLBACK_TARGET_INVALID");
        }
    }

    private ConnectorSpecPublishMapper.RollbackWrite rollbackWrite(
            VendorConnectorVersion target, Integer versionNo, Long previousVersionId,
            Long actorId, LocalDateTime publishedAt) {
        ConnectorSpecPublishMapper.RollbackWrite row = new ConnectorSpecPublishMapper.RollbackWrite();
        row.setTargetId(target.getId());
        row.setVendorConfigId(target.getVendorConfigId());
        row.setTargetVersionNo(target.getVersionNo());
        row.setVersionNo(versionNo);
        row.setPipelineSnapshot(target.getPipelineSnapshot());
        row.setSnapshotHash(target.getSnapshotHash());
        row.setHashAlgorithm(target.getHashAlgorithm());
        row.setIntegrityHash(target.getIntegrityHash());
        row.setAuthoringMode(target.getAuthoringMode());
        row.setConnectorSpec(target.getConnectorSpec());
        row.setSpecHash(target.getSpecHash());
        row.setCompilerVersion(target.getCompilerVersion());
        row.setCompileHash(target.getCompileHash());
        row.setSecurityVersion(target.getSecurityVersion());
        row.setPreviousVersionId(previousVersionId);
        row.setPublishedAt(publishedAt);
        row.setActorId(actorId);
        return row;
    }

    private void validatePublishRequest(
            Long configId, ConnectorSpecPublishRequestDTO request, Long actorId) {
        if (configId == null || configId <= 0 || request == null
                || !request.unknownFieldNames().isEmpty()
                || request.getExpectedDraftVersion() == null
                || request.getExpectedDraftVersion() <= 0) {
            throw new IllegalArgumentException("CONNECTOR_SPEC_PUBLISH_REQUEST_INVALID");
        }
        if (actorId == null || actorId <= 0) throw new IllegalArgumentException("ACTOR_ID_INVALID");
    }

    private VendorConnectorVersion requireActiveBinding(
            Long configId, Long activeId, List<VendorConnectorVersion> active) {
        if (active == null) throw new IllegalStateException("ACTIVE_CONNECTOR_BINDING_INVALID");
        if (activeId == null) {
            if (!active.isEmpty()) throw new IllegalStateException("ACTIVE_CONNECTOR_BINDING_INVALID");
            return null;
        }
        if (active.size() != 1) throw new IllegalStateException("ACTIVE_CONNECTOR_BINDING_INVALID");
        VendorConnectorVersion current = active.getFirst();
        if (current == null || !Objects.equals(activeId, current.getId())
                || !Objects.equals(configId, current.getVendorConfigId())
                || !"ACTIVE".equals(current.getStatus())) {
            throw new IllegalStateException("ACTIVE_CONNECTOR_BINDING_INVALID");
        }
        return current;
    }

    private ConnectorSpecPublishMapper.PublishedWrite publishedWrite(
            VendorConnectorVersion draft, ConnectorSpecCompilationResult compiled,
            Integer versionNo, Long previousVersionId, Long actorId, LocalDateTime publishedAt) {
        ConnectorSpecPublishMapper.PublishedWrite row = new ConnectorSpecPublishMapper.PublishedWrite();
        row.setDraftId(draft.getId());
        row.setVendorConfigId(draft.getVendorConfigId());
        row.setExpectedDraftVersion(draft.getDraftVersion());
        row.setVersionNo(versionNo);
        row.setPipelineSnapshot(writeJson(compiled.pipelineSteps()));
        row.setSnapshotHash(compiled.snapshotHash());
        row.setConnectorSpec(compiled.canonicalSpec());
        row.setSpecHash(compiled.specHash());
        row.setCompilerVersion(compiled.compilerVersion());
        row.setCompileHash(compiled.compileHash());
        row.setSecurityVersion(Math.toIntExact(compiled.securityVersion()));
        row.setPreviousVersionId(previousVersionId);
        row.setPublishedAt(publishedAt);
        row.setActorId(actorId);
        return row;
    }

    private void ensurePluginsReady(List<ConnectorPipelineStepDTO> pipeline) {
        Map<String, PluginCoordinate> coordinates = new TreeMap<>();
        for (ConnectorPipelineStepDTO step : pipeline) {
            if (step == null || Boolean.FALSE.equals(step.enabled())) continue;
            String pluginId = step.pluginId();
            String pluginVersion = step.pluginVersion();
            if (pluginId == null || pluginId.isBlank()
                    || pluginVersion == null || pluginVersion.isBlank()) {
                throw new ConnectorConflictException("CONNECTOR_PLUGIN_NOT_READY");
            }
            if (PlatformCoreConnectorMetadata.PLUGIN_ID.equals(pluginId)) {
                if (!PlatformCoreConnectorMetadata.VERSION.equals(pluginVersion)) {
                    throw new ConnectorConflictException("CONNECTOR_PLUGIN_NOT_READY");
                }
                continue;
            }
            coordinates.putIfAbsent(pluginId + ":" + pluginVersion,
                    new PluginCoordinate(pluginId, pluginVersion));
        }
        for (PluginCoordinate coordinate : coordinates.values()) {
            ConnectorPluginStageReqDTO request = new ConnectorPluginStageReqDTO();
            request.setPluginId(coordinate.pluginId());
            request.setPluginVersion(coordinate.pluginVersion());
            final Result<ConnectorPluginActivationSummaryDTO> response;
            try {
                response = activationClient.stage(request);
            } catch (RuntimeException exception) {
                throw new ConnectorConflictException("CONNECTOR_PLUGIN_NOT_READY");
            }
            if (response == null || !Integer.valueOf(200).equals(response.getCode())) {
                throw new ConnectorConflictException("CONNECTOR_PLUGIN_NOT_READY");
            }
            ConnectorPluginActivationSummaryDTO summary = response.getData();
            if (summary == null || !Objects.equals(coordinate.pluginId(), summary.getPluginId())
                    || !Objects.equals(coordinate.pluginVersion(), summary.getPluginVersion())
                    || !Boolean.TRUE.equals(summary.getReady())
                    || summary.getInstances() == null || summary.getInstances().isEmpty()) {
                throw new ConnectorConflictException("CONNECTOR_PLUGIN_NOT_READY");
            }
            for (ConnectorPluginActivationDTO instance : summary.getInstances()) {
                if (instance == null || !Objects.equals(coordinate.pluginId(), instance.getPluginId())
                        || !Objects.equals(coordinate.pluginVersion(), instance.getPluginVersion())
                        || !"READY".equals(instance.getState())) {
                    throw new ConnectorConflictException("CONNECTOR_PLUGIN_NOT_READY");
                }
            }
        }
    }

    private void validateLegacyPipeline(List<ConnectorPipelineStepDTO> steps) {
        if (steps.isEmpty()) throw new IllegalStateException("CONNECTOR_LEGACY_PIPELINE_INVALID");
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        long transports = 0;
        for (ConnectorPipelineStepDTO step : steps) {
            if (step == null || step.stageKey() == null || step.stageKey().isBlank()
                    || step.capability() == null || step.capability().isBlank()
                    || step.pluginId() == null || step.pluginId().isBlank()
                    || step.pluginVersion() == null || step.pluginVersion().isBlank()
                    || step.order() == null || step.enabled() == null
                    || !keys.add(step.stageKey()) || !orders.add(step.order())) {
                throw new IllegalStateException("CONNECTOR_LEGACY_PIPELINE_INVALID");
            }
            try {
                StageCapability.valueOf(step.capability());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("CONNECTOR_LEGACY_PIPELINE_INVALID");
            }
            if (!Boolean.FALSE.equals(step.enabled()) && "TRANSPORT".equals(step.capability())) {
                transports++;
            }
        }
        if (transports != 1) throw new IllegalStateException("CONNECTOR_LEGACY_PIPELINE_INVALID");
    }

    private void validateStoredSpec(ConnectorSpecDTO spec, List<ConnectorPipelineStepDTO> steps) {
        if (spec == null || !"1".equals(spec.getSpecVersion())
                || !spec.unknownFieldNames().isEmpty() || spec.getPlugin() == null
                || !spec.getPlugin().unknownFieldNames().isEmpty()
                || spec.getPlugin().getPluginId() == null || spec.getPlugin().getPluginId().isBlank()
                || spec.getPlugin().getPluginVersion() == null
                || spec.getPlugin().getPluginVersion().isBlank() || spec.getConfig() == null) {
            throw new IllegalStateException("CONNECTOR_SPEC_INVALID");
        }
        if (spec.getResponseMapping() != null && spec.getResponseMapping().stream()
                .anyMatch(mapping -> mapping == null || !mapping.unknownFieldNames().isEmpty())) {
            throw new IllegalStateException("CONNECTOR_SPEC_INVALID");
        }
        ConnectorPipelineStepDTO builder = steps.stream()
                .filter(step -> "connector.request-builder".equals(step.stageKey())
                        && !Boolean.FALSE.equals(step.enabled())).findFirst()
                .orElseThrow(() -> new IllegalStateException("CONNECTOR_SPEC_INVALID"));
        if (!Objects.equals(spec.getPlugin().getPluginId(), builder.pluginId())
                || !Objects.equals(spec.getPlugin().getPluginVersion(), builder.pluginVersion())) {
            throw new IllegalStateException("CONNECTOR_SPEC_INVALID");
        }
    }

    private List<Candidate> compatibleCandidates(TransactionFacts facts, String pluginId) {
        List<Candidate> candidates = new ArrayList<>();
        for (ConnectorPluginVersion entity : factsMapper.findSimpleCatalogVersions()) {
            if (pluginId != null && !pluginId.equals(entity.getPluginId())) continue;
            PluginManifest manifest = bindManifest(entity);
            requireBindablePlugin(entity.getPluginId(), List.of(manifest));
            if (manifest.compatibility().supportsVendor(facts.vendor().getVendorCode())
                    && manifest.compatibility().supportsDataType(facts.vendor().getDataTypeCode())) {
                candidates.add(new Candidate(entity, manifest, "ACTIVE".equals(entity.getStatus())));
            }
        }
        return candidates;
    }

    private BoundCompilation compile(TransactionFacts facts, ConnectorSpecDTO spec,
                                     ConnectorCompilationPurpose purpose) {
        String pluginId = spec == null || spec.getPlugin() == null ? null : spec.getPlugin().getPluginId();
        String version = spec == null || spec.getPlugin() == null ? null : spec.getPlugin().getPluginVersion();
        ConnectorPluginVersion entity = pluginId == null || version == null ? null
                : factsMapper.findPluginVersion(pluginId, version);
        PluginManifest manifest = null;
        try {
            if (entity == null) throw new ConnectorSpecNotFoundException("连接器插件版本不存在");
            manifest = bindManifest(entity);
            requireBindablePlugin(entity.getPluginId(), List.of(manifest));
            VerifiedPluginArtifact artifact = artifact(entity, manifest);
            ConnectorSpecCompilationInput input = new ConnectorSpecCompilationInput(
                    facts.vendor().getVendorConfigId(), facts.vendor().getVendorCode(),
                    facts.vendor().getDataTypeCode(), spec, artifact,
                    parseStatus(entity.getStatus()), facts.vendor().getSecurityVersion(),
                    facts.securitySteps(), facts.ownedSecretRefs()::contains, purpose);
            ConnectorSpecCompilationResult result = compiler.compile(input);
            metrics.success(pluginId, version, manifest.connectorKind().name(),
                    manifest.transportMode().name());
            return new BoundCompilation(result, manifest);
        } catch (RuntimeException exception) {
            metrics.failure(pluginId, version,
                    manifest == null ? null : manifest.connectorKind().name(),
                    manifest == null ? null : manifest.transportMode().name(), safeErrorCode(exception));
            throw exception;
        }
    }

    private VendorConnectorTestResultDTO validateTestResponse(
            Result<VendorConnectorTestRespDTO> result, ConnectorSpecCompilationResult compiled) {
        if (result == null || !Integer.valueOf(200).equals(result.getCode()) || result.getData() == null) {
            throw new IllegalStateException("CONNECTOR_TEST_RESPONSE_UNAVAILABLE");
        }
        VendorConnectorTestRespDTO response = result.getData();
        if (response.getSuccess() == null
                || !safeRemoteText(response.getErrorCategory(), 64)
                || !safeRemoteText(response.getErrorCode(), 128)
                || !safeRemoteText(response.getSafeMessage(), 512)
                || response.getNormalizedData() == null || response.getStageTimings() == null) {
            throw new IllegalStateException("CONNECTOR_TEST_RESPONSE_INVALID");
        }
        Map<String, Object> normalized = jsonObjectSnapshot(
                response.getNormalizedData(), "CONNECTOR_TEST_RESPONSE_INVALID");
        if (writeJson(normalized).getBytes(StandardCharsets.UTF_8).length > MAX_TEST_PAYLOAD_BYTES) {
            throw new IllegalStateException("CONNECTOR_TEST_RESPONSE_INVALID");
        }
        Map<String, ConnectorPipelineStepDTO> allowed = new LinkedHashMap<>();
        compiled.pipelineSteps().stream().filter(step -> !Boolean.FALSE.equals(step.enabled()))
                .forEach(step -> allowed.put(step.stageKey(), step));
        Set<String> seen = new HashSet<>();
        List<ConnectorStageTimingDTO> timings = new ArrayList<>();
        for (com.dataplatform.access.connector.api.dto.ConnectorStageTimingDTO timing
                : response.getStageTimings()) {
            if (timing == null || timing.getStageKey() == null || !seen.add(timing.getStageKey())) {
                throw new IllegalStateException("CONNECTOR_TEST_RESPONSE_INVALID");
            }
            ConnectorPipelineStepDTO step = allowed.get(timing.getStageKey());
            if (step == null || !Objects.equals(timing.getCapability(), step.capability())
                    || !Objects.equals(timing.getPluginId(), step.pluginId())
                    || !Objects.equals(timing.getPluginVersion(), step.pluginVersion())
                    || timing.getDurationMs() == null || timing.getDurationMs() < 0
                    || timing.getDurationMs() > MAX_STAGE_DURATION_MS) {
                throw new IllegalStateException("CONNECTOR_TEST_RESPONSE_INVALID");
            }
            timings.add(new ConnectorStageTimingDTO(timing.getStageKey(), timing.getCapability(),
                    timing.getPluginId(), timing.getPluginVersion(), timing.getDurationMs()));
        }
        return new VendorConnectorTestResultDTO(response.getSuccess(), response.getErrorCategory(),
                response.getErrorCode(), response.getSafeMessage(), normalized, List.copyOf(timings));
    }

    private ConnectorSpecLifecycleMapper.TestFactWrite testFact(
            VendorConnectorVersion draft, ConnectorSpecCompilationResult compiled,
            VendorConnectorTestResultDTO result, Long actorId) {
        ConnectorSpecLifecycleMapper.TestFactWrite fact = new ConnectorSpecLifecycleMapper.TestFactWrite();
        fact.setDraftId(draft.getId());
        fact.setVendorConfigId(draft.getVendorConfigId());
        fact.setDraftVersion(draft.getDraftVersion());
        fact.setSecurityVersion(draft.getSecurityVersion());
        fact.setConnectorSpec(compiled.canonicalSpec());
        fact.setPipelineSnapshot(writeJson(compiled.pipelineSteps()));
        fact.setSpecHash(compiled.specHash());
        fact.setCompilerVersion(compiled.compilerVersion());
        fact.setCompileHash(compiled.compileHash());
        fact.setSnapshotHash(compiled.snapshotHash());
        fact.setPluginBindings(writeJson(compiled.pipelineSteps().stream()
                .filter(step -> !Boolean.FALSE.equals(step.enabled()))
                .map(step -> step.pluginId() + ":" + step.pluginVersion()).distinct().sorted().toList()));
        fact.setTestSucceeded(Boolean.TRUE.equals(result.success()));
        fact.setSafeErrorCategory(result.errorCategory());
        fact.setSafeErrorCode(result.errorCode());
        var digest = new ObjectMapper().createObjectNode();
        digest.put("success", Boolean.TRUE.equals(result.success()));
        if (result.errorCategory() == null) digest.putNull("errorCategory");
        else digest.put("errorCategory", result.errorCategory());
        if (result.errorCode() == null) digest.putNull("errorCode");
        else digest.put("errorCode", result.errorCode());
        digest.set("stageTimings", new ObjectMapper().valueToTree(result.stageTimings()));
        fact.setResultDigest(ConnectorSnapshotIntegrity.sha256(new ObjectMapper(), digest));
        fact.setActorId(actorId);
        return fact;
    }

    private ConnectorTestPipelineStepDTO toAccessTestStep(ConnectorPipelineStepDTO step) {
        ConnectorTestPipelineStepDTO dto = new ConnectorTestPipelineStepDTO();
        dto.setStageKey(step.stageKey());
        dto.setCapability(step.capability());
        dto.setPluginId(step.pluginId());
        dto.setPluginVersion(step.pluginVersion());
        dto.setOrder(step.order());
        dto.setEnabled(step.enabled());
        dto.setConfig(jsonObjectSnapshot(step.config(), "CONNECTOR_PIPELINE_INVALID"));
        dto.setConfigHash(step.configHash());
        dto.setArtifactSha256(step.artifactSha256());
        dto.setManifestHash(step.manifestHash());
        dto.setSchemaHash(step.schemaHash());
        return dto;
    }

    private Map<String, Object> jsonObjectSnapshot(Map<String, ?> source, String errorCode) {
        try {
            Object value = deepJsonCopy(source == null ? Map.of() : source);
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(errorCode);
            @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) map;
            return java.util.Collections.unmodifiableMap(typed);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(errorCode);
        }
    }

    private Object deepJsonCopy(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number number) {
            if (number instanceof Double doubleValue && !Double.isFinite(doubleValue)
                    || number instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw new IllegalArgumentException("JSON_VALUE_INVALID");
            }
            return number;
        }
        if (value instanceof JsonNode node) {
            return deepJsonCopy(objectMapper.convertValue(node, Object.class));
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("JSON_VALUE_INVALID");
                copy.put(text, deepJsonCopy(nested));
            });
            return java.util.Collections.unmodifiableMap(copy);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            iterable.forEach(item -> copy.add(deepJsonCopy(item)));
            return java.util.Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray()) {
            List<Object> copy = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                copy.add(deepJsonCopy(Array.get(value, index)));
            }
            return java.util.Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException("JSON_VALUE_INVALID");
    }

    private boolean safeRemoteText(String value, int maxLength) {
        if (value == null) return true;
        if (value.length() > maxLength) return false;
        return value.codePoints().noneMatch(character -> character == '\r' || character == '\n'
                || Character.isISOControl(character));
    }

    private TransactionFacts loadFacts(Long configId) {
        if (configId == null || configId <= 0) throw new IllegalArgumentException("厂商配置ID无效");
        ConnectorSpecFactsMapper.VendorFacts vendor = factsMapper.findVendorFacts(configId);
        if (vendor == null || vendor.getVendorId() == null || vendor.getVendorCode() == null
                || vendor.getDataTypeCode() == null || vendor.getSecurityVersion() == null
                || vendor.getSecurityVersion() < 0) {
            throw new ConnectorSpecNotFoundException("厂商接口配置不存在或事实不完整");
        }
        List<SecurityStepConfig> security;
        if (vendor.getSecurityVersion() == 0) {
            security = List.of();
        } else {
            String snapshot = factsMapper.findSecuritySnapshot(configId, vendor.getSecurityVersion());
            if (snapshot == null) throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_MISSING");
            security = readSecurity(snapshot);
        }
        List<String> refs = factsMapper.findOwnedSecretRefs(vendor.getVendorId());
        if (refs == null) refs = List.of();
        Set<String> owned = new HashSet<>();
        for (String ref : refs) {
            if (ref == null || !ref.equals(ref.trim()) || !SECRET_REF.matcher(ref).matches()
                    || !owned.add(ref)) {
                throw new IllegalStateException("SECRET_REF_OWNERSHIP_FACTS_INVALID");
            }
        }
        return new TransactionFacts(vendor, List.copyOf(security), Set.copyOf(owned));
    }

    private PluginManifest bindManifest(ConnectorPluginVersion entity) {
        try {
            PluginManifestReader reader = new PluginManifestReader(new ObjectMapper());
            PluginManifest manifest = reader.read(entity.getManifestJson().getBytes(StandardCharsets.UTF_8));
            String compatibility = canonicalCompatibility(reader, entity.getManifestJson());
            if (!Objects.equals(entity.getPluginId(), manifest.pluginId())
                    || !Objects.equals(entity.getVersion(), manifest.version())
                    || !Objects.equals(entity.getSpiVersion(), manifest.spiVersion())
                    || !Objects.equals(entity.getEntryClass(), manifest.entryClass())
                    || !Objects.equals(entity.getMinHostVersion(), manifest.minHostVersion())
                    || !Objects.equals(entity.getManifestVersion(), manifest.manifestVersion())
                    || !Objects.equals(entity.getAuthoringModel(), manifest.authoringModel().name())
                    || !Objects.equals(entity.getConnectorKind(), manifest.connectorKind().name())
                    || !Objects.equals(entity.getTransportMode(), manifest.transportMode().name())
                    || !Objects.equals(entity.getOutputMode(), manifest.outputMode().name())
                    || !jsonEquals(entity.getCompatibilityManifest(), compatibility)
                    || !jsonEquals(entity.getConfigSchemaJson(), manifest.configSchema().toString())
                    || !readStrings(entity.getCapabilities()).equals(manifest.capabilities().stream()
                    .map(Enum::name).sorted().toList())
                    || !jsonEquals(entity.getPermissionManifest(), readTree(entity.getManifestJson())
                    .path("permissions").toString())
                    || !"2".equals(manifest.manifestVersion())
                    || manifest.authoringModel() != ConnectorAuthoringModel.SIMPLE_CONNECTOR
                    || !Set.of("IMPORTED", "VERIFIED", "STAGING", "STAGING_FAILED", "ACTIVE", "DISABLED")
                    .contains(entity.getStatus())) {
                throw new IllegalStateException("PLUGIN_SIGNED_PROJECTION_DRIFT");
            }
            return manifest;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("PLUGIN_SIGNED_PROJECTION_INVALID", exception);
        }
    }

    private VerifiedPluginArtifact artifact(ConnectorPluginVersion entity, PluginManifest manifest) {
        JsonNode root = readTree(entity.getManifestJson());
        String description = root.path("description").isTextual() ? root.path("description").asText() : null;
        return new VerifiedPluginArtifact(entity.getPluginId(), entity.getVersion(), entity.getSpiVersion(),
                manifest.displayName(), manifest.provider(), description, entity.getEntryClass(),
                entity.getArtifactUri(), entity.getArtifactSha256(), entity.getDetachedSignature(),
                entity.getSigningKeyId(), entity.getManifestJson(), entity.getConfigSchemaJson(),
                readStrings(entity.getCapabilities()), entity.getPermissionManifest(), entity.getMinHostVersion(),
                manifest.configSchema(), entity.getManifestVersion(), manifest.authoringModel(),
                manifest.connectorKind(), manifest.transportMode(), manifest.outputMode(),
                manifest.compatibility(), entity.getCompatibilityManifest());
    }

    private ConnectorSpecDraftViewDTO view(VendorConnectorVersion draft, TransactionFacts facts) {
        if (LEGACY.equals(draft.getAuthoringMode())) {
            return new ConnectorSpecDraftViewDTO(true, draft.getId(), draft.getVendorConfigId(),
                    draft.getDraftVersion(), LEGACY, draft.getSecurityVersion(), null,
                    null, null, null, null);
        }
        if (!SIMPLE.equals(draft.getAuthoringMode())) throw new IllegalStateException("DRAFT_AUTHORING_INVALID");
        List<ConnectorPipelineStepDTO> steps = readPipeline(draft.getPipelineSnapshot());
        ConnectorSpecDTO spec = readSpec(draft.getConnectorSpec());
        BoundCompilation compiled = compile(facts, spec, ConnectorCompilationPurpose.VALIDATE);
        if (!storedCompilationMatches(draft, compiled.result())
                || !jsonEquals(draft.getConnectorSpec(), compiled.result().canonicalSpec())) {
            throw new IllegalStateException("CONNECTOR_DRAFT_FACTS_DRIFTED");
        }
        String snapshotHash = compiled.result().snapshotHash();
        return new ConnectorSpecDraftViewDTO(true, draft.getId(), draft.getVendorConfigId(),
                draft.getDraftVersion(), SIMPLE, draft.getSecurityVersion(), spec, draft.getSpecHash(),
                draft.getCompilerVersion(), draft.getCompileHash(), snapshotHash);
    }

    private String assertStoredSimple(VendorConnectorVersion stored, List<ConnectorPipelineStepDTO> steps) {
        if (!SIMPLE.equals(stored.getAuthoringMode()) || stored.getConnectorSpec() == null
                || stored.getSpecHash() == null || stored.getCompileHash() == null
                || !ConnectorSpecCompiler.COMPILER_VERSION.equals(stored.getCompilerVersion())
                || steps.isEmpty()) throw new IllegalStateException("CONNECTOR_DRAFT_FACTS_INVALID");
        validatePipelineShape(steps);
        List<ConnectorStageDefinition> definitions = toDefinitions(steps);
        String snapshot = ConnectorSnapshotIntegrity.v2SnapshotHash(new ObjectMapper(), definitions);
        JsonNode specNode = readTree(stored.getConnectorSpec());
        String specHash = ConnectorSnapshotIntegrity.sha256(new ObjectMapper(), specNode);
        String compileHash = compileHash(specHash, snapshot, stored.getSecurityVersion());
        if (!Objects.equals(stored.getSpecHash(), specHash)
                || !Objects.equals(stored.getCompileHash(), compileHash)
                || "DRAFT".equals(stored.getStatus()) && (stored.getSnapshotHash() != null
                || stored.getHashAlgorithm() != null || stored.getIntegrityHash() != null)
                || !"DRAFT".equals(stored.getStatus()) && (!Objects.equals(stored.getSnapshotHash(), snapshot)
                || !"V2_EMBEDDED".equals(stored.getHashAlgorithm())
                || !Objects.equals(stored.getIntegrityHash(), snapshot))) {
            throw new IllegalStateException("CONNECTOR_DRAFT_INTEGRITY_DRIFT");
        }
        return snapshot;
    }

    private void validatePipelineShape(List<ConnectorPipelineStepDTO> steps) {
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        long transports = 0;
        for (ConnectorPipelineStepDTO step : steps) {
            if (step == null || step.stageKey() == null || step.stageKey().isBlank()
                    || step.capability() == null || step.pluginId() == null || step.pluginVersion() == null
                    || step.order() == null || step.enabled() == null || step.config() == null
                    || step.configHash() == null || step.artifactSha256() == null
                    || step.manifestHash() == null || step.schemaHash() == null
                    || !keys.add(step.stageKey()) || !orders.add(step.order())) {
                throw new IllegalStateException("CONNECTOR_PIPELINE_INVALID");
            }
            String configHash = ConnectorSnapshotIntegrity.sha256(
                    new ObjectMapper(), objectMapper.valueToTree(step.config()));
            if (!Objects.equals(configHash, step.configHash())) {
                throw new IllegalStateException("CONNECTOR_PIPELINE_INVALID");
            }
            if ("TRANSPORT".equals(step.capability()) && step.enabled()) transports++;
        }
        if (transports != 1) throw new IllegalStateException("CONNECTOR_PIPELINE_INVALID");
    }

    private void assertSimplePlanPluginsBound(List<ConnectorPipelineStepDTO> steps) {
        Map<String, PluginDigests> expected = new LinkedHashMap<>();
        for (ConnectorPipelineStepDTO step : steps) {
            String coordinate = step.pluginId() + ":" + step.pluginVersion();
            PluginDigests digests = expected.get(coordinate);
            if (digests == null && PlatformCoreConnectorMetadata.PLUGIN_ID.equals(step.pluginId())) {
                if (!PlatformCoreConnectorMetadata.VERSION.equals(step.pluginVersion())) {
                    throw new IllegalStateException("CONNECTOR_PLAN_PLUGIN_MISSING");
                }
                digests = new PluginDigests(PlatformCoreConnectorMetadata.artifactSha256(),
                        PlatformCoreConnectorMetadata.manifestSha256(),
                        PlatformCoreConnectorMetadata.schemaSha256());
                expected.put(coordinate, digests);
            } else if (digests == null) {
                ConnectorPluginVersion entity = factsMapper.findPluginVersion(
                        step.pluginId(), step.pluginVersion());
                if (entity == null) throw new IllegalStateException("CONNECTOR_PLAN_PLUGIN_MISSING");
                PluginManifest manifest = bindManifest(entity);
                digests = pluginDigests(entity, manifest);
                expected.put(coordinate, digests);
            }
            if (!Objects.equals(step.artifactSha256(), digests.artifactSha256())
                    || !Objects.equals(step.manifestHash(), digests.manifestHash())
                    || !Objects.equals(step.schemaHash(), digests.schemaHash())) {
                throw new IllegalStateException("CONNECTOR_PLAN_PLUGIN_DIGEST_DRIFT");
            }
        }
    }

    private PluginDigests pluginDigests(ConnectorPluginVersion entity, PluginManifest manifest) {
        PluginManifestReader reader = new PluginManifestReader(new ObjectMapper());
        JsonNode canonicalManifest = readTree(new String(reader.canonicalize(
                entity.getManifestJson().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        return new PluginDigests(entity.getArtifactSha256().toLowerCase(Locale.ROOT),
                ConnectorSnapshotIntegrity.sha256(new ObjectMapper(), canonicalManifest),
                ConnectorSnapshotIntegrity.sha256(new ObjectMapper(), manifest.configSchema()));
    }

    private boolean storedCompilationMatches(VendorConnectorVersion stored,
                                             ConnectorSpecCompilationResult compiled) {
        if (!Objects.equals(stored.getSecurityVersion(), Math.toIntExact(compiled.securityVersion()))
                || !Objects.equals(stored.getSpecHash(), compiled.specHash())
                || !Objects.equals(stored.getCompilerVersion(), compiled.compilerVersion())
                || !Objects.equals(stored.getCompileHash(), compiled.compileHash())) return false;
        List<ConnectorPipelineStepDTO> existing = readPipeline(stored.getPipelineSnapshot());
        return Objects.equals(existing, compiled.pipelineSteps())
                && Objects.equals(ConnectorSnapshotIntegrity.v2SnapshotHash(
                new ObjectMapper(), toDefinitions(existing)), compiled.snapshotHash());
    }

    private List<SecurityStepConfig> readSecurity(String snapshot) {
        try {
            JsonNode array = objectMapper.readTree(snapshot);
            if (!array.isArray()) throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID");
            List<SecurityStepConfig> result = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonNode node : array) {
                if (!node.isObject() || node.has("id") && node.has("stepKey")) {
                    throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID");
                }
                List<String> unknown = new ArrayList<>();
                node.fieldNames().forEachRemaining(name -> {
                    if (!SECURITY_STEP_FIELDS.contains(name)) unknown.add(name);
                });
                if (!unknown.isEmpty()) throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID");
                SecurityStepConfig step = new SecurityStepConfig();
                String id = text(node, node.has("stepKey") ? "stepKey" : "id");
                if (id == null || id.isBlank() || !ids.add(id))
                    throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID");
                step.setId(id);
                step.setDirection(SecurityDirection.valueOf(requiredText(node, "direction")));
                step.setStepType(SecurityStepType.valueOf(requiredText(node, "stepType")));
                step.setStepName(text(node, "stepName"));
                if (!node.path("sortNo").canConvertToInt())
                    throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID");
                step.setSortNo(node.path("sortNo").intValue());
                if (!node.path("enabled").isBoolean())
                    throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID");
                step.setEnabled(node.path("enabled").booleanValue());
                JsonNode config = node.path("config");
                if (!config.isObject()) throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID");
                step.setConfig(objectMapper.convertValue(config, new TypeReference<>() { }));
                result.add(step);
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID", exception);
        }
    }

    private ConnectorSpecFactsMapper.CatalogPluginFacts requirePluginFacts(
            String pluginId, List<PluginManifest> manifests) {
        return requireBindablePlugin(pluginId, manifests);
    }

    private ConnectorSpecFactsMapper.CatalogPluginFacts requireBindablePlugin(
            String pluginId, List<PluginManifest> manifests) {
        ConnectorSpecFactsMapper.CatalogPluginFacts facts = factsMapper.findCatalogPlugin(pluginId);
        if (facts == null || !"ACTIVE".equals(facts.getStatus())
                || facts.getDisplayName() == null || facts.getProvider() == null
                || manifests.stream().anyMatch(manifest ->
                !Objects.equals(facts.getDisplayName(), manifest.displayName())
                        || !Objects.equals(facts.getProvider(), manifest.provider())
                        || signedDescription(manifest) != null
                        && !Objects.equals(facts.getDescription(), signedDescription(manifest)))) {
            throw new IllegalStateException("PLUGIN_CATALOG_FACTS_INVALID");
        }
        return facts;
    }

    private String signedDescription(PluginManifest manifest) {
        ConnectorPluginVersion entity = factsMapper.findPluginVersion(manifest.pluginId(), manifest.version());
        if (entity == null) return null;
        JsonNode root = readTree(entity.getManifestJson());
        return root.path("description").isTextual() ? root.path("description").asText() : null;
    }

    private void validateSaveRequest(ConnectorSpecSaveRequestDTO request) {
        if (request == null || !request.unknownFieldNames().isEmpty()
                || request.getExpectedDraftVersion() == null
                || request.getExpectedDraftVersion() < 0 || request.getConnectorSpec() == null) {
            throw new IllegalArgumentException("CONNECTOR_SPEC_SAVE_REQUEST_INVALID");
        }
    }

    private ConnectorSpecValidationDTO invalidValidation(String code) {
        return new ConnectorSpecValidationDTO(false, code, null, null, null, null);
    }

    private List<ConnectorPipelineStepDTO> readPipeline(String json) {
        try {
            if (json == null) throw new IllegalStateException("CONNECTOR_PIPELINE_MISSING");
            List<ConnectorPipelineStepDTO> result = objectMapper.readValue(json, new TypeReference<>() { });
            if (result == null) throw new IllegalStateException("CONNECTOR_PIPELINE_INVALID");
            return List.copyOf(result);
        } catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("CONNECTOR_PIPELINE_INVALID", exception); }
    }

    private ConnectorSpecDTO readSpec(String json) {
        try { return objectMapper.readValue(json, ConnectorSpecDTO.class); }
        catch (Exception exception) { throw new IllegalStateException("CONNECTOR_SPEC_INVALID", exception); }
    }

    private List<ConnectorStageDefinition> toDefinitions(List<ConnectorPipelineStepDTO> steps) {
        try {
            return steps.stream().map(step -> new ConnectorStageDefinition(step.stageKey(),
                    StageCapability.valueOf(step.capability()), step.pluginId(), step.pluginVersion(),
                    step.order(), !Boolean.FALSE.equals(step.enabled()), objectMapper.valueToTree(step.config()),
                    step.configHash(), step.artifactSha256(), step.manifestHash(), step.schemaHash())).toList();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("CONNECTOR_PIPELINE_INVALID", exception);
        }
    }

    private String compileHash(String specHash, String snapshotHash, Integer securityVersion) {
        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode();
        node.put("specHash", specHash);
        node.put("snapshotHash", snapshotHash);
        node.put("compilerVersion", ConnectorSpecCompiler.COMPILER_VERSION);
        node.put("securityVersion", securityVersion == null ? -1 : securityVersion);
        return ConnectorSnapshotIntegrity.sha256(mapper, node);
    }

    private Map<String, Object> schemaMap(JsonNode schema) {
        return objectMapper.convertValue(schema, new TypeReference<>() { });
    }

    private ConnectorSpecCatalogDTO.Compatibility compatibility(PluginManifest manifest) {
        return new ConnectorSpecCatalogDTO.Compatibility(manifest.compatibility().vendorCodes().stream()
                .sorted().toList(), manifest.compatibility().dataTypeCodes().stream().sorted().toList());
    }

    private List<String> readStrings(String json) {
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() { });
            return values.stream().sorted().toList();
        } catch (Exception exception) { throw new IllegalStateException("PLUGIN_CAPABILITIES_INVALID", exception); }
    }

    private ConnectorPluginCatalogStatus parseStatus(String status) {
        try { return ConnectorPluginCatalogStatus.valueOf(status); }
        catch (Exception exception) { throw new IllegalStateException("PLUGIN_STATUS_INVALID", exception); }
    }

    private String canonicalCompatibility(PluginManifestReader reader, String manifestJson) throws Exception {
        JsonNode node = objectMapper.readTree(manifestJson).path("compatibility");
        return new String(reader.canonicalize(objectMapper.writeValueAsBytes(node)), StandardCharsets.UTF_8);
    }

    private String source(ConnectorPipelineStepDTO step) {
        if (step.stageKey().startsWith("platform.security.")) return "PLATFORM_SECURITY";
        if ("platform.transport".equals(step.stageKey())) return "PLATFORM_TRANSPORT";
        if ("platform.response-normalizer".equals(step.stageKey())) return "PLATFORM_NORMALIZER";
        return "CONNECTOR";
    }

    private Comparator<Candidate> candidateComparator() {
        return Comparator.comparingInt((Candidate item) -> item.active() ? 0 : 1)
                .thenComparing((Candidate left, Candidate right) ->
                        -compareSemver(left.entity().getVersion(), right.entity().getVersion()));
    }

    private int compareSemver(String left, String right) {
        String[] a = left.split("[-+]", 2)[0].split("\\.");
        String[] b = right.split("[-+]", 2)[0].split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? Integer.parseInt(a[i]) : 0;
            int bv = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return left.compareTo(right);
    }

    private int matchRank(PluginManifest manifest, TransactionFacts facts) {
        if (manifest.connectorKind() == ConnectorKind.GENERIC_HTTP) return 3;
        if (manifest.compatibility().vendorCodes().contains(facts.vendor().getVendorCode())) return 0;
        if (manifest.compatibility().dataTypeCodes().contains(facts.vendor().getDataTypeCode())) return 1;
        return 2;
    }
    private String prefix(String hash) { return hash == null ? null : hash.substring(0, Math.min(12, hash.length())); }
    private JsonNode readTree(String json) {
        try { return objectMapper.readTree(json); }
        catch (Exception exception) { throw new IllegalStateException("JSON_FACT_INVALID", exception); }
    }
    private boolean jsonEquals(String left, String right) {
        return left != null && right != null && Objects.equals(readTree(left), readTree(right));
    }
    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("CONNECTOR_JSON_WRITE_FAILED", exception); }
    }
    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) throw new IllegalStateException("SECURITY_VERSION_SNAPSHOT_INVALID");
        return value;
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
    private static String safeErrorCode(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "CONNECTOR_SPEC_INVALID";
        String code = message.split(":", 2)[0].toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]", "_");
        return code.isBlank() || code.length() > 64 ? "CONNECTOR_SPEC_INVALID" : code;
    }

    private record TransactionFacts(ConnectorSpecFactsMapper.VendorFacts vendor,
                                    List<SecurityStepConfig> securitySteps,
                                    Set<String> ownedSecretRefs) { }
    private record Candidate(ConnectorPluginVersion entity, PluginManifest manifest, boolean active) { }
    private record BoundCompilation(ConnectorSpecCompilationResult result, PluginManifest manifest) { }
    private record PluginDigests(String artifactSha256, String manifestHash, String schemaHash) { }
    private record PluginCoordinate(String pluginId, String pluginVersion) { }
    private record SchemaFact(String type, boolean required, String enumDigest, boolean secretRef) { }
}
