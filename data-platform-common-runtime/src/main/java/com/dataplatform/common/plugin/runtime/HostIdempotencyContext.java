package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.IdempotencyContext;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import java.util.Objects;

/** Immutable host-derived idempotency facts for one connector attempt. */
public record HostIdempotencyContext(
        IdempotencyPolicy policy,
        String idempotencyKey,
        boolean retryPermitted) implements IdempotencyContext {

    public HostIdempotencyContext {
        policy = Objects.requireNonNull(policy, "policy");
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? null : idempotencyKey;
        if (policy == IdempotencyPolicy.IDEMPOTENT_WITH_KEY && idempotencyKey == null) {
            throw new IllegalArgumentException("idempotencyKey is required by policy");
        }
        if (policy == IdempotencyPolicy.NON_IDEMPOTENT && retryPermitted) {
            throw new IllegalArgumentException("non-idempotent requests cannot be retryable");
        }
    }

    public static HostIdempotencyContext nonRetryable() {
        return new HostIdempotencyContext(IdempotencyPolicy.NON_IDEMPOTENT, null, false);
    }

    public static HostIdempotencyContext fromRequest(ConnectorRequest request) {
        if (request == null) {
            return nonRetryable();
        }
        boolean safeMethod = "GET".equals(request.method()) || "HEAD".equals(request.method())
                || "OPTIONS".equals(request.method());
        if (safeMethod) {
            return new HostIdempotencyContext(IdempotencyPolicy.IDEMPOTENT,
                    request.idempotencyKey(), true);
        }
        if (request.idempotencyPolicy() == IdempotencyPolicy.NON_IDEMPOTENT) {
            return nonRetryable();
        }
        boolean keyed = request.idempotencyKey() != null && !request.idempotencyKey().isBlank();
        return new HostIdempotencyContext(request.idempotencyPolicy(), request.idempotencyKey(), keyed);
    }
}
