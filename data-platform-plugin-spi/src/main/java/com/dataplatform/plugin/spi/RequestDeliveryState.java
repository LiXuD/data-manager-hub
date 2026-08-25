package com.dataplatform.plugin.spi;

/**
 * Describes whether an external request may have crossed the process boundary.
 * Only {@link #NOT_SENT} is safe for a legacy-path retry or fallback.
 */
public enum RequestDeliveryState {
    NOT_SENT,
    MAYBE_SENT,
    SENT
}
