package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VendorConnectorMigrationDTO(
        Long id,
        Long vendorConfigId,
        Long vendorId,
        Long interfaceId,
        String state,
        Integer recordVersion,
        String sourceConfigHash,
        Long draftId,
        Integer draftVersion,
        String draftSnapshotHash,
        Long publishedConnectorVersionId,
        Integer publishedVersionNo,
        String previousRuntimeMode,
        Long previousActiveConnectorVersionId,
        Integer previousConnectorVersion,
        Integer minimumObservationMinutes,
        Long minimumCalls,
        Double maximumErrorRate,
        Long maximumP95DurationMs,
        Double minimumBillingCoverageRate,
        LocalDateTime observationStartedAt,
        LocalDateTime observationEligibleAt,
        Long observedCalls,
        Long observedSuccesses,
        Long observedFailures,
        Double observedErrorRate,
        Long observedP95DurationMs,
        Long observedCacheHits,
        Long observedRealtimeCalls,
        Long observedBillingEvents,
        Long observedPostedBillingEvents,
        Double observedBillingCoverageRate,
        BigDecimal observedBillingAmount,
        Boolean observationGatePassed,
        String safeErrorCode,
        String safeErrorDigest,
        LocalDateTime completedAt,
        LocalDateTime rolledBackAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {
}
