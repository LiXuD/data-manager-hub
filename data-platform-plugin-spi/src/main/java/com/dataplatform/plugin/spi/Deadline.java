package com.dataplatform.plugin.spi;

import java.time.Duration;
import java.time.Instant;

/** Host-owned total execution deadline exposed to a connector invocation. */
public interface Deadline {

    Instant expiresAt();

    Duration remaining();

    boolean isExpired();
}
