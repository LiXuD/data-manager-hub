package com.dataplatform.plugin.spi;

public enum IdempotencyPolicy {
    IDEMPOTENT,
    IDEMPOTENT_WITH_KEY,
    NON_IDEMPOTENT
}
