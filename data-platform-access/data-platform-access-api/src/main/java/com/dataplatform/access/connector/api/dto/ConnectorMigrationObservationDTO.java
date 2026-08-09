package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;

public record ConnectorMigrationObservationDTO(
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        double errorRate,
        long p95DurationMs,
        long cacheHitCalls,
        long realtimeCalls) implements Serializable {
}
