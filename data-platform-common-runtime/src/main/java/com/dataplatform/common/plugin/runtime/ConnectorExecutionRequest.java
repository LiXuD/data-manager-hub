package com.dataplatform.common.plugin.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.function.BooleanSupplier;

public record ConnectorExecutionRequest(Map<String, Object> standardParameters,
                                        String vendorCode,
                                        Instant deadline,
                                        BooleanSupplier cancellationRequested) {
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
    }
}
