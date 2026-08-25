package com.dataplatform.common.plugin.transport;

import java.time.Duration;
import java.util.Set;

public record NetworkPolicy(
        Set<String> allowedProtocols,
        Set<String> allowedHosts,
        boolean allowPrivateNetworks,
        Duration maxConnectTimeout,
        Duration maxReadTimeout,
        Duration maxTotalTimeout,
        long maxResponseBytes) {

    public NetworkPolicy {
        allowedProtocols = allowedProtocols == null ? Set.of() : allowedProtocols.stream()
                .map(String::toLowerCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
        allowedHosts = allowedHosts == null ? Set.of() : allowedHosts.stream()
                .map(String::toLowerCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (allowedProtocols.isEmpty() || allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("Network protocol and host allowlists are required");
        }
        maxConnectTimeout = positive(maxConnectTimeout, "maxConnectTimeout");
        maxReadTimeout = positive(maxReadTimeout, "maxReadTimeout");
        maxTotalTimeout = positive(maxTotalTimeout, "maxTotalTimeout");
        if (maxResponseBytes <= 0 || maxResponseBytes > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("maxResponseBytes must be between 1 and 10 MiB");
        }
    }

    private static Duration positive(Duration duration, String field) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return duration;
    }
}
