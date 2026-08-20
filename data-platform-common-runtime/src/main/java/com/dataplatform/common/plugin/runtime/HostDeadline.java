package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.Deadline;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Host-clock-backed total deadline shared by every stage in one attempt. */
public final class HostDeadline implements Deadline {

    private final Clock clock;
    private final Instant expiresAt;

    public HostDeadline(Clock clock, Instant expiresAt) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override public Instant expiresAt() { return expiresAt; }

    @Override
    public Duration remaining() {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    @Override
    public boolean isExpired() {
        return !clock.instant().isBefore(expiresAt);
    }
}
