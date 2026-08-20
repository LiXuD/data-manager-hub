package com.dataplatform.plugin.spi;

/** Read-only host facts governing the current connector attempt's idempotency. */
public interface IdempotencyContext {

    IdempotencyPolicy policy();

    String idempotencyKey();

    boolean retryPermitted();
}
