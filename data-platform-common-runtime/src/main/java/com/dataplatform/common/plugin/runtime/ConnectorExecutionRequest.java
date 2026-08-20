package com.dataplatform.common.plugin.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import com.dataplatform.plugin.spi.IdempotencyContext;

public record ConnectorExecutionRequest(Map<String, Object> standardParameters,
                                        String vendorCode,
                                        Instant deadline,
                                        BooleanSupplier cancellationRequested,
                                        String requestId,
                                        long vendorConfigId,
                                        int attemptNo,
                                        IdempotencyContext idempotencyContext) {

    /** Compatibility constructor for existing v1 callers. */
    public ConnectorExecutionRequest(Map<String, Object> standardParameters,
                                     String vendorCode,
                                     Instant deadline,
                                     BooleanSupplier cancellationRequested) {
        this(standardParameters, vendorCode, deadline, cancellationRequested,
                "legacy-" + UUID.randomUUID(), 0L, 1,
                HostIdempotencyContext.nonRetryable());
    }

    public ConnectorExecutionRequest {
        standardParameters = standardParameters == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(standardParameters));
        if (vendorCode == null || vendorCode.isBlank()) {
            throw new IllegalArgumentException("vendorCode is required");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("deadline is required");
        }
        cancellationRequested = cancellationRequested == null ? () -> false : cancellationRequested;
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (vendorConfigId < 0) {
            throw new IllegalArgumentException("vendorConfigId cannot be negative");
        }
        if (attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        idempotencyContext = Objects.requireNonNull(idempotencyContext, "idempotencyContext");
    }

    public ConnectorExecutionRequest withAttemptNo(int nextAttemptNo) {
        return new ConnectorExecutionRequest(standardParameters, vendorCode, deadline,
                cancellationRequested, requestId, vendorConfigId, nextAttemptNo, idempotencyContext);
    }
}
