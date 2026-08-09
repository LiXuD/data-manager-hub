package com.dataplatform.plugin.spi;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record ConnectorExecutionResult(
        TransportStatus transportStatus,
        BusinessStatus businessStatus,
        Map<String, Object> normalizedData,
        ErrorCategory errorCategory,
        String errorCode,
        String safeMessage,
        BillingSignal billingSignal,
        CacheSignal cacheSignal,
        RequestDeliveryState deliveryState,
        String pluginId,
        String pluginVersion,
        String pipelineVersion,
        String snapshotHash,
        String hashAlgorithm,
        String integrityHash,
        List<StageTiming> stageTimings) {

    public ConnectorExecutionResult(
            TransportStatus transportStatus, BusinessStatus businessStatus,
            Map<String, Object> normalizedData, ErrorCategory errorCategory,
            String errorCode, String safeMessage, BillingSignal billingSignal,
            CacheSignal cacheSignal, RequestDeliveryState deliveryState,
            String pluginId, String pluginVersion, String pipelineVersion,
            String snapshotHash, List<StageTiming> stageTimings) {
        this(transportStatus, businessStatus, normalizedData, errorCategory, errorCode, safeMessage,
                billingSignal, cacheSignal, deliveryState, pluginId, pluginVersion, pipelineVersion,
                snapshotHash, null, null, stageTimings);
    }

    public ConnectorExecutionResult {
        normalizedData = normalizedData == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(normalizedData));
        stageTimings = stageTimings == null ? List.of() : List.copyOf(stageTimings);
    }

    public boolean successful() {
        return errorCategory == null && transportStatus == TransportStatus.SUCCESS
                && businessStatus == BusinessStatus.SUCCESS;
    }
}
