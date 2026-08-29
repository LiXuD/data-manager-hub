package com.dataplatform.masterdata.connector.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationReqDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.feign.ConnectorMigrationObservationInternalFeignClient;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationDTO;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationReqDTO;
import com.dataplatform.billing.api.feign.ConnectorBillingObservationInternalFeignClient;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecRollbackRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDraftViewDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationActionRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationObserveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationStartRequestDTO;
import com.dataplatform.masterdata.connector.entity.VendorConnectorMigration;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorMigrationMapper;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.VendorConnectorMigrationService;
import com.dataplatform.masterdata.connector.spec.ConnectorSpecService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Coordinates one vendor migration without exposing connector payloads or raw pipeline writes. */
@Service
public class VendorConnectorMigrationServiceImpl implements VendorConnectorMigrationService {
    private static final String LEGACY = "ADVANCED_LEGACY";
    private static final String SIMPLE = "SIMPLE_CONNECTOR";
    private static final String PLUGIN_RUNTIME = "PLUGIN";
    private static final int DEFAULT_MINIMUM_OBSERVATION_MINUTES = 60;
    private static final long DEFAULT_MINIMUM_CALLS = 100;
    private static final double DEFAULT_MAXIMUM_ERROR_RATE = 0.05D;
    private static final long DEFAULT_MAXIMUM_P95_DURATION_MS = 5000L;
    private static final double DEFAULT_MINIMUM_BILLING_COVERAGE_RATE = 1D;
    private static final int REQUIRED_ACCESS_INSTANCES = 2;

    private final VendorConnectorMigrationMapper migrationMapper;
    private final ConnectorMigrationObservationInternalFeignClient accessObservationClient;
    private final ConnectorBillingObservationInternalFeignClient billingObservationClient;
    private final ConnectorPluginActivationInternalFeignClient activationClient;
    private final ConnectorSpecService connectorSpecService;

    public VendorConnectorMigrationServiceImpl(
            VendorConnectorMigrationMapper migrationMapper,
            ConnectorMigrationObservationInternalFeignClient accessObservationClient,
            ConnectorBillingObservationInternalFeignClient billingObservationClient,
            ConnectorPluginActivationInternalFeignClient activationClient,
            ConnectorSpecService connectorSpecService) {
        this.migrationMapper = migrationMapper;
        this.accessObservationClient = accessObservationClient;
        this.billingObservationClient = billingObservationClient;
        this.activationClient = activationClient;
        this.connectorSpecService = connectorSpecService;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<VendorConnectorMigrationDTO> list(String state) {
        return migrationMapper.selectList(new LambdaQueryWrapper<VendorConnectorMigration>()
                        .eq(StringUtils.hasText(state), VendorConnectorMigration::getState,
                                StringUtils.hasText(state) ? state.toUpperCase(Locale.ROOT) : null)
                        .orderByAsc(VendorConnectorMigration::getVendorConfigId))
                .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public VendorConnectorMigrationDTO prepare(Long vendorConfigId, Long actorId) {
        validateIds(vendorConfigId, actorId);
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = lockRuntimeFacts(vendorConfigId);
        requireLegacySource(facts);
        VendorConnectorMigration current = lockMigration(vendorConfigId);
        if (current != null && "STABLE".equals(current.getState())) {
            return toDto(current);
        }
        if (current != null && !reusable(current.getState())) {
            verifySourceUnchanged(current, facts);
            return toDto(current);
        }

        if (current == null) {
            VendorConnectorMigration created = new VendorConnectorMigration();
            populatePrepared(created, facts, actorId);
            if (migrationMapper.insert(created) != 1) {
                throw new ConnectorConflictException("CONNECTOR_MIGRATION_PREPARE_CONFLICT");
            }
        } else {
            verifySourceUnchanged(current, facts);
            int nextRecordVersion = nextRecordVersion(current.getRecordVersion());
            int updated = migrationMapper.update(null, new LambdaUpdateWrapper<VendorConnectorMigration>()
                    .eq(VendorConnectorMigration::getId, current.getId())
                    .eq(VendorConnectorMigration::getRecordVersion, current.getRecordVersion())
                    .set(VendorConnectorMigration::getState, "PREPARED")
                    .set(VendorConnectorMigration::getRecordVersion, nextRecordVersion)
                    .set(VendorConnectorMigration::getSourceConfigHash, facts.getActiveSnapshotHash())
                    .set(VendorConnectorMigration::getDraftId, null)
                    .set(VendorConnectorMigration::getDraftVersion, null)
                    .set(VendorConnectorMigration::getDraftSnapshotHash, null)
                    .set(VendorConnectorMigration::getPublishedConnectorVersionId, null)
                    .set(VendorConnectorMigration::getPublishedVersionNo, null)
                    .set(VendorConnectorMigration::getPreviousRuntimeMode, "LEGACY")
                    .set(VendorConnectorMigration::getPreviousActiveConnectorVersionId,
                            facts.getActiveConnectorVersionId())
                    .set(VendorConnectorMigration::getPreviousConnectorVersion, facts.getActiveVersionNo())
                    .set(VendorConnectorMigration::getObservationStartedAt, null)
                    .set(VendorConnectorMigration::getObservationEligibleAt, null)
                    .set(VendorConnectorMigration::getObservedCalls, 0L)
                    .set(VendorConnectorMigration::getObservedSuccesses, 0L)
                    .set(VendorConnectorMigration::getObservedFailures, 0L)
                    .set(VendorConnectorMigration::getObservedErrorRate, 0D)
                    .set(VendorConnectorMigration::getObservedP95DurationMs, 0L)
                    .set(VendorConnectorMigration::getObservedCacheHits, 0L)
                    .set(VendorConnectorMigration::getObservedRealtimeCalls, 0L)
                    .set(VendorConnectorMigration::getObservedBillingEvents, 0L)
                    .set(VendorConnectorMigration::getObservedPostedBillingEvents, 0L)
                    .set(VendorConnectorMigration::getObservedBillingCoverageRate, 0D)
                    .set(VendorConnectorMigration::getObservedBillingAmount, BigDecimal.ZERO)
                    .set(VendorConnectorMigration::getObservationGatePassed, false)
                    .set(VendorConnectorMigration::getSafeErrorCode, null)
                    .set(VendorConnectorMigration::getSafeErrorDigest, null)
                    .set(VendorConnectorMigration::getCompletedAt, null)
                    .set(VendorConnectorMigration::getRolledBackAt, null)
                    .set(VendorConnectorMigration::getUpdatedBy, actorId)
                    .set(VendorConnectorMigration::getUpdatedAt, LocalDateTime.now()));
            if (updated != 1) {
                throw new ConnectorConflictException("CONNECTOR_MIGRATION_RECORD_CONFLICT");
            }
        }
        return toDto(requireMigration(vendorConfigId));
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public VendorConnectorMigrationDTO startObservation(
            Long vendorConfigId, VendorConnectorMigrationStartRequestDTO request, Long actorId) {
        validateIds(vendorConfigId, actorId);
        validateStartRequest(request);
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = lockRuntimeFacts(vendorConfigId);
        VendorConnectorMigration migration = requireMigration(vendorConfigId);
        verifyExpectedVersion(migration, request.getExpectedRecordVersion());
        if ("STABLE".equals(migration.getState())) return toDto(migration);
        if ("OBSERVING".equals(migration.getState()) || "READY".equals(migration.getState())) {
            verifyObservationTarget(migration, facts);
            return toDto(migration);
        }
        if (!List.of("PREPARED", "VALIDATED", "TEST_PASSED").contains(migration.getState())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_STATE_NOT_STARTABLE");
        }
        requireSimpleTarget(facts);
        ConnectorSpecDraftViewDTO targetDraft = requireTargetDraft(vendorConfigId, facts);
        requireActivationReady(facts);
        LocalDateTime startedAt = LocalDateTime.now();
        int minimumMinutes = defaultValue(request.getMinimumObservationMinutes(),
                DEFAULT_MINIMUM_OBSERVATION_MINUTES);
        int nextRecordVersion = nextRecordVersion(migration.getRecordVersion());
        int updated = migrationMapper.update(null, new LambdaUpdateWrapper<VendorConnectorMigration>()
                .eq(VendorConnectorMigration::getId, migration.getId())
                .eq(VendorConnectorMigration::getRecordVersion, migration.getRecordVersion())
                .in(VendorConnectorMigration::getState, "PREPARED", "VALIDATED", "TEST_PASSED")
                .set(VendorConnectorMigration::getState, "OBSERVING")
                .set(VendorConnectorMigration::getRecordVersion, nextRecordVersion)
                .set(VendorConnectorMigration::getDraftId, targetDraft.id())
                .set(VendorConnectorMigration::getDraftVersion, targetDraft.draftVersion())
                .set(VendorConnectorMigration::getDraftSnapshotHash, targetDraft.compiledSnapshotHash())
                .set(VendorConnectorMigration::getPublishedConnectorVersionId,
                        facts.getActiveConnectorVersionId())
                .set(VendorConnectorMigration::getPublishedVersionNo, facts.getActiveVersionNo())
                .set(VendorConnectorMigration::getMinimumObservationMinutes, minimumMinutes)
                .set(VendorConnectorMigration::getMinimumCalls,
                        defaultValue(request.getMinimumCalls(), DEFAULT_MINIMUM_CALLS))
                .set(VendorConnectorMigration::getMaximumErrorRate,
                        defaultValue(request.getMaximumErrorRate(), DEFAULT_MAXIMUM_ERROR_RATE))
                .set(VendorConnectorMigration::getMaximumP95DurationMs,
                        defaultValue(request.getMaximumP95DurationMs(), DEFAULT_MAXIMUM_P95_DURATION_MS))
                .set(VendorConnectorMigration::getMinimumBillingCoverageRate,
                        defaultValue(request.getMinimumBillingCoverageRate(),
                                DEFAULT_MINIMUM_BILLING_COVERAGE_RATE))
                .set(VendorConnectorMigration::getObservationStartedAt, startedAt)
                .set(VendorConnectorMigration::getObservationEligibleAt,
                        startedAt.plusMinutes(minimumMinutes))
                .set(VendorConnectorMigration::getObservedCalls, 0L)
                .set(VendorConnectorMigration::getObservedSuccesses, 0L)
                .set(VendorConnectorMigration::getObservedFailures, 0L)
                .set(VendorConnectorMigration::getObservedErrorRate, 0D)
                .set(VendorConnectorMigration::getObservedP95DurationMs, 0L)
                .set(VendorConnectorMigration::getObservedCacheHits, 0L)
                .set(VendorConnectorMigration::getObservedRealtimeCalls, 0L)
                .set(VendorConnectorMigration::getObservedBillingEvents, 0L)
                .set(VendorConnectorMigration::getObservedPostedBillingEvents, 0L)
                .set(VendorConnectorMigration::getObservedBillingCoverageRate, 0D)
                .set(VendorConnectorMigration::getObservedBillingAmount, BigDecimal.ZERO)
                .set(VendorConnectorMigration::getObservationGatePassed, false)
                .set(VendorConnectorMigration::getSafeErrorCode, null)
                .set(VendorConnectorMigration::getSafeErrorDigest, null)
                .set(VendorConnectorMigration::getCompletedAt, null)
                .set(VendorConnectorMigration::getRolledBackAt, null)
                .set(VendorConnectorMigration::getUpdatedBy, actorId)
                .set(VendorConnectorMigration::getUpdatedAt, startedAt));
        if (updated != 1) throw new ConnectorConflictException("CONNECTOR_MIGRATION_RECORD_CONFLICT");
        return toDto(requireMigration(vendorConfigId));
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public VendorConnectorMigrationDTO observe(
            Long vendorConfigId, VendorConnectorMigrationObserveRequestDTO request, Long actorId) {
        validateIds(vendorConfigId, actorId);
        validateObserveRequest(request);
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = lockRuntimeFacts(vendorConfigId);
        VendorConnectorMigration migration = requireMigration(vendorConfigId);
        verifyExpectedVersion(migration, request.getExpectedRecordVersion());
        if ("READY".equals(migration.getState()) || "STABLE".equals(migration.getState())) {
            return toDto(migration);
        }
        if (!"OBSERVING".equals(migration.getState())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_STATE_NOT_OBSERVABLE");
        }
        verifyObservationTarget(migration, facts);
        requireActivationReady(facts);
        LocalDateTime endedAt = request.getEndedAt() == null ? LocalDateTime.now() : request.getEndedAt();
        if (endedAt.isBefore(migration.getObservationStartedAt())) {
            throw new IllegalArgumentException("endedAt cannot be before observationStartedAt");
        }
        if (endedAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("endedAt cannot be in the future");
        }

        ConnectorMigrationObservationDTO access;
        ConnectorBillingObservationDTO billing;
        try {
            access = accessObservation(facts, migration, endedAt);
            billing = billingObservation(facts, migration, endedAt);
            validateObservationFacts(access, billing);
        } catch (ObservationDataException exception) {
            return toDto(markFailed(migration, exception.code(), actorId));
        }

        double billingCoverage = access.totalCalls() == 0
                ? 0D : (double) billing.postedEvents() / access.totalCalls();
        if (billingCoverage < 0D || billingCoverage > 1D) {
            return toDto(markFailed(migration, "BILLING_COVERAGE_INVALID", actorId));
        }
        boolean observationMature = !endedAt.isBefore(migration.getObservationEligibleAt())
                && access.totalCalls() >= migration.getMinimumCalls();
        String failureCode = firstFailure(access, billingCoverage, migration);
        boolean gatePassed = observationMature && failureCode == null;
        String nextState = gatePassed ? "READY" : observationMature && failureCode != null ? "FAILED" : "OBSERVING";
        int nextRecordVersion = nextRecordVersion(migration.getRecordVersion());
        int updated = migrationMapper.update(null, new LambdaUpdateWrapper<VendorConnectorMigration>()
                .eq(VendorConnectorMigration::getId, migration.getId())
                .eq(VendorConnectorMigration::getRecordVersion, migration.getRecordVersion())
                .eq(VendorConnectorMigration::getState, "OBSERVING")
                .set(VendorConnectorMigration::getState, nextState)
                .set(VendorConnectorMigration::getRecordVersion, nextRecordVersion)
                .set(VendorConnectorMigration::getObservedCalls, access.totalCalls())
                .set(VendorConnectorMigration::getObservedSuccesses, access.successfulCalls())
                .set(VendorConnectorMigration::getObservedFailures, access.failedCalls())
                .set(VendorConnectorMigration::getObservedErrorRate, access.errorRate())
                .set(VendorConnectorMigration::getObservedP95DurationMs, access.p95DurationMs())
                .set(VendorConnectorMigration::getObservedCacheHits, access.cacheHitCalls())
                .set(VendorConnectorMigration::getObservedRealtimeCalls, access.realtimeCalls())
                .set(VendorConnectorMigration::getObservedBillingEvents, billing.totalEvents())
                .set(VendorConnectorMigration::getObservedPostedBillingEvents, billing.postedEvents())
                .set(VendorConnectorMigration::getObservedBillingCoverageRate, billingCoverage)
                .set(VendorConnectorMigration::getObservedBillingAmount, billing.finalAmount())
                .set(VendorConnectorMigration::getObservationGatePassed, gatePassed)
                .set(VendorConnectorMigration::getSafeErrorCode, failureCode)
                .set(VendorConnectorMigration::getSafeErrorDigest,
                        failureCode == null ? null : digest(failureCode))
                .set(VendorConnectorMigration::getUpdatedBy, actorId)
                .set(VendorConnectorMigration::getUpdatedAt, endedAt));
        if (updated != 1) throw new ConnectorConflictException("CONNECTOR_MIGRATION_RECORD_CONFLICT");
        return toDto(requireMigration(vendorConfigId));
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public VendorConnectorMigrationDTO complete(
            Long vendorConfigId, VendorConnectorMigrationActionRequestDTO request, Long actorId) {
        validateIds(vendorConfigId, actorId);
        validateActionRequest(request);
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = lockRuntimeFacts(vendorConfigId);
        VendorConnectorMigration migration = requireMigration(vendorConfigId);
        verifyExpectedVersion(migration, request.getExpectedRecordVersion());
        if ("STABLE".equals(migration.getState())) return toDto(migration);
        if (!"READY".equals(migration.getState()) || !Boolean.TRUE.equals(migration.getObservationGatePassed())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_OBSERVATION_GATE_NOT_PASSED");
        }
        verifyObservationTarget(migration, facts);
        LocalDateTime completedAt = LocalDateTime.now();
        int updated = migrationMapper.update(null, new LambdaUpdateWrapper<VendorConnectorMigration>()
                .eq(VendorConnectorMigration::getId, migration.getId())
                .eq(VendorConnectorMigration::getRecordVersion, migration.getRecordVersion())
                .eq(VendorConnectorMigration::getState, "READY")
                .set(VendorConnectorMigration::getState, "STABLE")
                .set(VendorConnectorMigration::getRecordVersion, nextRecordVersion(migration.getRecordVersion()))
                .set(VendorConnectorMigration::getCompletedAt, completedAt)
                .set(VendorConnectorMigration::getUpdatedBy, actorId)
                .set(VendorConnectorMigration::getUpdatedAt, completedAt));
        if (updated != 1) throw new ConnectorConflictException("CONNECTOR_MIGRATION_RECORD_CONFLICT");
        return toDto(requireMigration(vendorConfigId));
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public VendorConnectorMigrationDTO rollback(
            Long vendorConfigId, VendorConnectorMigrationActionRequestDTO request, Long actorId) {
        validateIds(vendorConfigId, actorId);
        validateActionRequest(request);
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = lockRuntimeFacts(vendorConfigId);
        VendorConnectorMigration migration = requireMigration(vendorConfigId);
        verifyExpectedVersion(migration, request.getExpectedRecordVersion());
        if ("ROLLED_BACK".equals(migration.getState())) return toDto(migration);
        if (migration.getPreviousConnectorVersion() == null || migration.getPreviousConnectorVersion() <= 0
                || migration.getPublishedConnectorVersionId() == null
                || !migration.getPublishedConnectorVersionId().equals(facts.getActiveConnectorVersionId())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_ROLLBACK_TARGET_INVALID");
        }
        if (!SIMPLE.equals(facts.getActiveAuthoringMode()) || !PLUGIN_RUNTIME.equals(facts.getRuntimeMode())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_ROLLBACK_RUNTIME_INVALID");
        }
        if (facts.getConnectorVersion() == null || facts.getConnectorVersion() < 0) {
            throw new ConnectorConflictException("CONNECTOR_CONTROL_FACTS_INVALID");
        }
        connectorSpecService.rollback(vendorConfigId, migration.getPreviousConnectorVersion(),
                new ConnectorSpecRollbackRequestDTO(facts.getConnectorVersion()), actorId);
        LocalDateTime rolledBackAt = LocalDateTime.now();
        int updated = migrationMapper.update(null, new LambdaUpdateWrapper<VendorConnectorMigration>()
                .eq(VendorConnectorMigration::getId, migration.getId())
                .eq(VendorConnectorMigration::getRecordVersion, migration.getRecordVersion())
                .set(VendorConnectorMigration::getState, "ROLLED_BACK")
                .set(VendorConnectorMigration::getRecordVersion, nextRecordVersion(migration.getRecordVersion()))
                .set(VendorConnectorMigration::getObservationGatePassed, false)
                .set(VendorConnectorMigration::getRolledBackAt, rolledBackAt)
                .set(VendorConnectorMigration::getUpdatedBy, actorId)
                .set(VendorConnectorMigration::getUpdatedAt, rolledBackAt));
        if (updated != 1) throw new ConnectorConflictException("CONNECTOR_MIGRATION_RECORD_CONFLICT");
        return toDto(requireMigration(vendorConfigId));
    }

    private ConnectorMigrationObservationDTO accessObservation(
            VendorConnectorMigrationMapper.MigrationRuntimeFacts facts,
            VendorConnectorMigration migration, LocalDateTime endedAt) {
        try {
            Result<ConnectorMigrationObservationDTO> response = accessObservationClient.observation(
                    new ConnectorMigrationObservationReqDTO(facts.getVendorId(), facts.getInterfaceId(),
                            migration.getPublishedVersionNo(),
                            requireHash(facts.getActiveSnapshotHash()), migration.getObservationStartedAt(), endedAt));
            if (response == null || !Integer.valueOf(200).equals(response.getCode()) || response.getData() == null) {
                throw new ObservationDataException("ACCESS_OBSERVATION_UNAVAILABLE");
            }
            return response.getData();
        } catch (ObservationDataException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ObservationDataException("ACCESS_OBSERVATION_UNAVAILABLE");
        }
    }

    private ConnectorBillingObservationDTO billingObservation(
            VendorConnectorMigrationMapper.MigrationRuntimeFacts facts,
            VendorConnectorMigration migration, LocalDateTime endedAt) {
        try {
            Result<ConnectorBillingObservationDTO> response = billingObservationClient.observation(
                    new ConnectorBillingObservationReqDTO(facts.getVendorId(), facts.getInterfaceId(),
                            migration.getPublishedVersionNo(), requireHash(facts.getActiveSnapshotHash()),
                            migration.getObservationStartedAt(), endedAt));
            if (response == null || !Integer.valueOf(200).equals(response.getCode()) || response.getData() == null) {
                throw new ObservationDataException("BILLING_OBSERVATION_UNAVAILABLE");
            }
            return response.getData();
        } catch (ObservationDataException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ObservationDataException("BILLING_OBSERVATION_UNAVAILABLE");
        }
    }

    private void validateObservationFacts(ConnectorMigrationObservationDTO access,
                                          ConnectorBillingObservationDTO billing) {
        if (access.totalCalls() < 0 || access.successfulCalls() < 0 || access.failedCalls() < 0
                || access.successfulCalls() + access.failedCalls() != access.totalCalls()
                || access.errorRate() < 0D || access.errorRate() > 1D || access.p95DurationMs() < 0
                || access.cacheHitCalls() < 0 || access.realtimeCalls() < 0
                || billing.totalEvents() < 0 || billing.postedEvents() < 0
                || billing.postedEvents() > billing.totalEvents() || billing.pendingReviewEvents() < 0
                || billing.reversedEvents() < 0 || billing.billableEvents() < 0
                || billing.finalAmount() == null || billing.finalAmount().signum() < 0) {
            throw new ObservationDataException("OBSERVATION_FACTS_INVALID");
        }
    }

    private String firstFailure(ConnectorMigrationObservationDTO access, double billingCoverage,
                               VendorConnectorMigration migration) {
        if (access.errorRate() > migration.getMaximumErrorRate()) return "ERROR_RATE_ABOVE_THRESHOLD";
        if (access.p95DurationMs() > migration.getMaximumP95DurationMs()) return "P95_ABOVE_THRESHOLD";
        if (billingCoverage < migration.getMinimumBillingCoverageRate()) {
            return "BILLING_COVERAGE_BELOW_THRESHOLD";
        }
        return null;
    }

    private VendorConnectorMigration markFailed(VendorConnectorMigration migration,
                                                String code, Long actorId) {
        int updated = migrationMapper.update(null, new LambdaUpdateWrapper<VendorConnectorMigration>()
                .eq(VendorConnectorMigration::getId, migration.getId())
                .eq(VendorConnectorMigration::getRecordVersion, migration.getRecordVersion())
                .eq(VendorConnectorMigration::getState, "OBSERVING")
                .set(VendorConnectorMigration::getState, "FAILED")
                .set(VendorConnectorMigration::getRecordVersion, nextRecordVersion(migration.getRecordVersion()))
                .set(VendorConnectorMigration::getObservationGatePassed, false)
                .set(VendorConnectorMigration::getSafeErrorCode, code)
                .set(VendorConnectorMigration::getSafeErrorDigest, digest(code))
                .set(VendorConnectorMigration::getUpdatedBy, actorId)
                .set(VendorConnectorMigration::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) throw new ConnectorConflictException("CONNECTOR_MIGRATION_RECORD_CONFLICT");
        return requireMigration(migration.getVendorConfigId());
    }

    private void requireActivationReady(VendorConnectorMigrationMapper.MigrationRuntimeFacts facts) {
        if (!StringUtils.hasText(facts.getPluginId()) || !StringUtils.hasText(facts.getPluginVersion())) {
            throw new ConnectorConflictException("CONNECTOR_PLUGIN_BINDING_MISSING");
        }
        ConnectorPluginActivationSummaryDTO summary;
        try {
            Result<ConnectorPluginActivationSummaryDTO> response = activationClient.activation(
                    facts.getPluginId(), facts.getPluginVersion());
            summary = response == null || !Integer.valueOf(200).equals(response.getCode())
                    ? null : response.getData();
        } catch (RuntimeException exception) {
            throw new ConnectorConflictException("CONNECTOR_PLUGIN_ACTIVATION_UNAVAILABLE");
        }
        if (summary == null || !Boolean.TRUE.equals(summary.getReady())) {
            throw new ConnectorConflictException("CONNECTOR_PLUGIN_NOT_READY");
        }
        List<ConnectorPluginActivationDTO> instances = summary.getInstances();
        if (instances == null || instances.size() < REQUIRED_ACCESS_INSTANCES) {
            throw new ConnectorConflictException("CONNECTOR_ACCESS_INSTANCES_INSUFFICIENT");
        }
        if (instances.stream().anyMatch(instance -> instance == null
                || !"READY".equals(instance.getState()))) {
            throw new ConnectorConflictException("CONNECTOR_ACCESS_INSTANCE_NOT_READY");
        }
    }

    private void populatePrepared(VendorConnectorMigration target,
                                  VendorConnectorMigrationMapper.MigrationRuntimeFacts facts, Long actorId) {
        target.setVendorConfigId(facts.getVendorConfigId());
        target.setVendorId(facts.getVendorId());
        target.setInterfaceId(facts.getInterfaceId());
        target.setState("PREPARED");
        target.setRecordVersion(0);
        target.setSourceConfigHash(requireHash(facts.getActiveSnapshotHash()));
        target.setPreviousRuntimeMode("LEGACY");
        target.setPreviousActiveConnectorVersionId(facts.getActiveConnectorVersionId());
        target.setPreviousConnectorVersion(facts.getActiveVersionNo());
        target.setMinimumObservationMinutes(DEFAULT_MINIMUM_OBSERVATION_MINUTES);
        target.setMinimumCalls(DEFAULT_MINIMUM_CALLS);
        target.setMaximumErrorRate(DEFAULT_MAXIMUM_ERROR_RATE);
        target.setMaximumP95DurationMs(DEFAULT_MAXIMUM_P95_DURATION_MS);
        target.setMinimumBillingCoverageRate(DEFAULT_MINIMUM_BILLING_COVERAGE_RATE);
        target.setObservedCalls(0L);
        target.setObservedSuccesses(0L);
        target.setObservedFailures(0L);
        target.setObservedErrorRate(0D);
        target.setObservedP95DurationMs(0L);
        target.setObservedCacheHits(0L);
        target.setObservedRealtimeCalls(0L);
        target.setObservedBillingEvents(0L);
        target.setObservedPostedBillingEvents(0L);
        target.setObservedBillingCoverageRate(0D);
        target.setObservedBillingAmount(BigDecimal.ZERO);
        target.setObservationGatePassed(false);
        target.setCreatedBy(actorId);
        target.setUpdatedBy(actorId);
    }

    private void requireLegacySource(VendorConnectorMigrationMapper.MigrationRuntimeFacts facts) {
        if (facts == null || facts.getVendorConfigId() == null || facts.getVendorId() == null
                || facts.getInterfaceId() == null || facts.getActiveConnectorVersionId() == null
                || facts.getActiveVersionNo() == null || !LEGACY.equals(facts.getActiveAuthoringMode())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_SOURCE_NOT_LEGACY");
        }
        if (!PLUGIN_RUNTIME.equals(facts.getRuntimeMode())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_RUNTIME_NOT_PLUGIN");
        }
        requireHash(facts.getActiveSnapshotHash());
    }

    private void requireSimpleTarget(VendorConnectorMigrationMapper.MigrationRuntimeFacts facts) {
        if (facts == null || !PLUGIN_RUNTIME.equals(facts.getRuntimeMode())
                || !SIMPLE.equals(facts.getActiveAuthoringMode())
                || facts.getActiveConnectorVersionId() == null || facts.getActiveVersionNo() == null) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_TARGET_NOT_SIMPLE");
        }
        requireHash(facts.getActiveSnapshotHash());
    }

    private void verifyObservationTarget(VendorConnectorMigration migration,
                                         VendorConnectorMigrationMapper.MigrationRuntimeFacts facts) {
        requireSimpleTarget(facts);
        if (migration.getDraftId() == null || migration.getDraftId() <= 0
                || migration.getDraftVersion() == null || migration.getDraftVersion() <= 0
                || !StringUtils.hasText(migration.getDraftSnapshotHash())
                || !migration.getDraftSnapshotHash().equalsIgnoreCase(facts.getActiveSnapshotHash())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_TARGET_BINDING_MISSING");
        }
        if (!migration.getPublishedConnectorVersionId().equals(facts.getActiveConnectorVersionId())
                || !migration.getPublishedVersionNo().equals(facts.getActiveVersionNo())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_TARGET_CHANGED");
        }
    }

    private ConnectorSpecDraftViewDTO requireTargetDraft(
            Long vendorConfigId, VendorConnectorMigrationMapper.MigrationRuntimeFacts facts) {
        ConnectorSpecDraftViewDTO draft;
        try {
            draft = connectorSpecService.draft(vendorConfigId);
        } catch (RuntimeException exception) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_TARGET_DRAFT_UNAVAILABLE");
        }
        if (draft == null || !draft.present() || !vendorConfigId.equals(draft.vendorConfigId())
                || draft.id() == null || draft.id() <= 0 || draft.draftVersion() == null
                || draft.draftVersion() <= 0 || !SIMPLE.equals(draft.authoringMode())
                || !StringUtils.hasText(draft.compiledSnapshotHash())
                || !draft.compiledSnapshotHash().matches("(?i)[0-9a-f]{64}")
                || !draft.compiledSnapshotHash().equalsIgnoreCase(facts.getActiveSnapshotHash())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_TARGET_DRAFT_INVALID");
        }
        return draft;
    }

    private void verifySourceUnchanged(VendorConnectorMigration migration,
                                       VendorConnectorMigrationMapper.MigrationRuntimeFacts facts) {
        if (!StringUtils.hasText(migration.getSourceConfigHash())
                || !migration.getSourceConfigHash().equals(facts.getActiveSnapshotHash())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_SOURCE_CHANGED");
        }
    }

    private VendorConnectorMigrationMapper.MigrationRuntimeFacts lockRuntimeFacts(Long vendorConfigId) {
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts =
                migrationMapper.lockRuntimeFacts(vendorConfigId);
        if (facts == null || !vendorConfigId.equals(facts.getVendorConfigId())) {
            throw new IllegalArgumentException("厂商接口配置不存在");
        }
        return facts;
    }

    private VendorConnectorMigration lockMigration(Long vendorConfigId) {
        return migrationMapper.selectOne(new LambdaQueryWrapper<VendorConnectorMigration>()
                .eq(VendorConnectorMigration::getVendorConfigId, vendorConfigId)
                .last("FOR UPDATE"));
    }

    private VendorConnectorMigration requireMigration(Long vendorConfigId) {
        VendorConnectorMigration migration = lockMigration(vendorConfigId);
        if (migration == null) throw new ConnectorConflictException("CONNECTOR_MIGRATION_NOT_PREPARED");
        return migration;
    }

    private void verifyExpectedVersion(VendorConnectorMigration migration, Integer expected) {
        if (expected == null || !expected.equals(migration.getRecordVersion())) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_RECORD_CONFLICT");
        }
    }

    private void validateIds(Long vendorConfigId, Long actorId) {
        if (vendorConfigId == null || vendorConfigId <= 0) {
            throw new IllegalArgumentException("vendorConfigId is required");
        }
        if (actorId == null || actorId <= 0) throw new IllegalArgumentException("ACTOR_ID_INVALID");
    }

    private void validateStartRequest(VendorConnectorMigrationStartRequestDTO request) {
        if (request == null || !request.unknownFieldNames().isEmpty()
                || request.getExpectedRecordVersion() == null
                || request.getExpectedRecordVersion() < 0) {
            throw new IllegalArgumentException("CONNECTOR_MIGRATION_START_REQUEST_INVALID");
        }
        int minutes = defaultValue(request.getMinimumObservationMinutes(), DEFAULT_MINIMUM_OBSERVATION_MINUTES);
        long calls = defaultValue(request.getMinimumCalls(), DEFAULT_MINIMUM_CALLS);
        double errorRate = defaultValue(request.getMaximumErrorRate(), DEFAULT_MAXIMUM_ERROR_RATE);
        long p95 = defaultValue(request.getMaximumP95DurationMs(), DEFAULT_MAXIMUM_P95_DURATION_MS);
        double billing = defaultValue(request.getMinimumBillingCoverageRate(),
                DEFAULT_MINIMUM_BILLING_COVERAGE_RATE);
        if (minutes < 0 || minutes > 10080 || calls < 1 || calls > 1_000_000
                || errorRate < 0D || errorRate > 1D || p95 < 1 || p95 > 600_000
                || billing < 0D || billing > 1D) {
            throw new IllegalArgumentException("CONNECTOR_MIGRATION_THRESHOLDS_INVALID");
        }
    }

    private void validateObserveRequest(VendorConnectorMigrationObserveRequestDTO request) {
        if (request == null || !request.unknownFieldNames().isEmpty()
                || request.getExpectedRecordVersion() == null || request.getExpectedRecordVersion() < 0) {
            throw new IllegalArgumentException("CONNECTOR_MIGRATION_OBSERVE_REQUEST_INVALID");
        }
    }

    private void validateActionRequest(VendorConnectorMigrationActionRequestDTO request) {
        if (request == null || !request.unknownFieldNames().isEmpty()
                || request.getExpectedRecordVersion() == null || request.getExpectedRecordVersion() < 0) {
            throw new IllegalArgumentException("CONNECTOR_MIGRATION_ACTION_REQUEST_INVALID");
        }
    }

    private boolean reusable(String state) {
        return "FAILED".equals(state) || "BLOCKED".equals(state) || "ROLLED_BACK".equals(state);
    }

    private int nextRecordVersion(Integer value) {
        if (value == null || value < 0 || value == Integer.MAX_VALUE) {
            throw new ConnectorConflictException("CONNECTOR_MIGRATION_RECORD_VERSION_INVALID");
        }
        return value + 1;
    }

    private String requireHash(String hash) {
        if (!StringUtils.hasText(hash) || !hash.matches("[0-9a-fA-F]{64}")) {
            throw new ConnectorConflictException("CONNECTOR_SNAPSHOT_HASH_INVALID");
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    private String digest(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SAFE_ERROR_DIGEST_UNAVAILABLE", exception);
        }
    }

    private <T> T defaultValue(T value, T fallback) { return value == null ? fallback : value; }

    private VendorConnectorMigrationDTO toDto(VendorConnectorMigration value) {
        return new VendorConnectorMigrationDTO(value.getId(), value.getVendorConfigId(), value.getVendorId(),
                value.getInterfaceId(), value.getState(), value.getRecordVersion(), value.getSourceConfigHash(),
                value.getDraftId(), value.getDraftVersion(), value.getDraftSnapshotHash(),
                value.getPublishedConnectorVersionId(), value.getPublishedVersionNo(), value.getPreviousRuntimeMode(),
                value.getPreviousActiveConnectorVersionId(), value.getPreviousConnectorVersion(),
                value.getMinimumObservationMinutes(), value.getMinimumCalls(), value.getMaximumErrorRate(),
                value.getMaximumP95DurationMs(), value.getMinimumBillingCoverageRate(),
                value.getObservationStartedAt(), value.getObservationEligibleAt(), value.getObservedCalls(),
                value.getObservedSuccesses(), value.getObservedFailures(), value.getObservedErrorRate(),
                value.getObservedP95DurationMs(), value.getObservedCacheHits(), value.getObservedRealtimeCalls(),
                value.getObservedBillingEvents(), value.getObservedPostedBillingEvents(),
                value.getObservedBillingCoverageRate(), value.getObservedBillingAmount(),
                value.getObservationGatePassed(), value.getSafeErrorCode(), value.getSafeErrorDigest(),
                value.getCompletedAt(), value.getRolledBackAt(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private static final class ObservationDataException extends RuntimeException {
        private final String code;

        private ObservationDataException(String code) { this.code = code; }

        private String code() { return code; }
    }
}
