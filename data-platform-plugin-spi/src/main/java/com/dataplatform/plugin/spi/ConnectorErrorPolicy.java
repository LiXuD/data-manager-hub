package com.dataplatform.plugin.spi;

import java.util.EnumMap;
import java.util.Map;

/**
 * Exhaustive host governance policy for connector failures.
 * Plugins report facts; the platform owns retry, fallback, circuit, billing,
 * cache and external error semantics.
 */
public enum ConnectorErrorPolicy {
    CONFIGURATION_ERROR(ErrorCategory.CONFIGURATION_ERROR, false, false, false, false, false,
            RequestDeliveryState.NOT_SENT, "CONFIGURATION_ERROR"),
    PLUGIN_NOT_READY(ErrorCategory.PLUGIN_NOT_READY, false, false, false, false, false,
            RequestDeliveryState.NOT_SENT, "PLUGIN_NOT_READY"),
    PLUGIN_VERSION_MISMATCH(ErrorCategory.PLUGIN_VERSION_MISMATCH, false, false, false, false, false,
            RequestDeliveryState.NOT_SENT, "PLUGIN_VERSION_MISMATCH"),
    REQUEST_BUILD_ERROR(ErrorCategory.REQUEST_BUILD_ERROR, false, false, false, false, false,
            RequestDeliveryState.NOT_SENT, "REQUEST_BUILD_ERROR"),
    AUTH_SECURITY_ERROR(ErrorCategory.AUTH_SECURITY_ERROR, false, false, false, false, false,
            RequestDeliveryState.NOT_SENT, "AUTH_SECURITY_ERROR"),
    TRANSPORT_TIMEOUT(ErrorCategory.TRANSPORT_TIMEOUT, true, true, true, true, false,
            RequestDeliveryState.MAYBE_SENT, "TRANSPORT_TIMEOUT"),
    TRANSPORT_CONNECTION_ERROR(ErrorCategory.TRANSPORT_CONNECTION_ERROR, true, true, true, true, false,
            RequestDeliveryState.MAYBE_SENT, "TRANSPORT_CONNECTION_ERROR"),
    TRANSPORT_HTTP_ERROR(ErrorCategory.TRANSPORT_HTTP_ERROR, false, false, true, true, false,
            RequestDeliveryState.SENT, "TRANSPORT_HTTP_ERROR"),
    RESPONSE_SECURITY_ERROR(ErrorCategory.RESPONSE_SECURITY_ERROR, false, false, true, true, false,
            RequestDeliveryState.SENT, "RESPONSE_SECURITY_ERROR"),
    RESPONSE_PARSE_ERROR(ErrorCategory.RESPONSE_PARSE_ERROR, false, false, true, true, false,
            RequestDeliveryState.SENT, "RESPONSE_PARSE_ERROR"),
    BUSINESS_REJECTED(ErrorCategory.BUSINESS_REJECTED, false, false, true, true, false,
            RequestDeliveryState.SENT, "BUSINESS_REJECTED"),
    CONTRACT_VIOLATION(ErrorCategory.CONTRACT_VIOLATION, false, false, false, false, false,
            RequestDeliveryState.SENT, "CONTRACT_VIOLATION"),
    PLUGIN_INTERNAL_ERROR(ErrorCategory.PLUGIN_INTERNAL_ERROR, false, false, false, false, false,
            RequestDeliveryState.MAYBE_SENT, "PLUGIN_INTERNAL_ERROR");

    private static final Map<ErrorCategory, ConnectorErrorPolicy> BY_CATEGORY = buildIndex();

    private final ErrorCategory category;
    private final boolean retryAllowed;
    private final boolean fallbackAllowed;
    private final boolean circuitFailure;
    private final boolean billingAllowed;
    private final boolean cacheAllowed;
    private final RequestDeliveryState defaultDeliveryState;
    private final String externalCode;

    ConnectorErrorPolicy(ErrorCategory category, boolean retryAllowed,
                         boolean fallbackAllowed, boolean circuitFailure,
                         boolean billingAllowed, boolean cacheAllowed,
                         RequestDeliveryState defaultDeliveryState,
                         String externalCode) {
        this.category = category;
        this.retryAllowed = retryAllowed;
        this.fallbackAllowed = fallbackAllowed;
        this.circuitFailure = circuitFailure;
        this.billingAllowed = billingAllowed;
        this.cacheAllowed = cacheAllowed;
        this.defaultDeliveryState = defaultDeliveryState;
        this.externalCode = externalCode;
    }

    public static ConnectorErrorPolicy forCategory(ErrorCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("error category is required");
        }
        ConnectorErrorPolicy policy = BY_CATEGORY.get(category);
        if (policy == null) {
            throw new IllegalStateException("missing connector error policy: " + category);
        }
        return policy;
    }

    public ErrorCategory category() { return category; }
    public boolean retryAllowed() { return retryAllowed; }
    public boolean fallbackAllowed() { return fallbackAllowed; }
    public boolean circuitFailure() { return circuitFailure; }
    public RequestDeliveryState defaultDeliveryState() { return defaultDeliveryState; }
    public boolean billingAllowed() { return billingAllowed; }
    public boolean cacheAllowed() { return cacheAllowed; }
    public String externalCode() { return externalCode; }

    public RequestDeliveryState deliveryState(RequestDeliveryState reportedState) {
        return reportedState != null ? reportedState : defaultDeliveryState;
    }

    public BillingSignal billingSignal(BillingSignal reportedSignal) {
        return billingSignal(reportedSignal, defaultDeliveryState);
    }

    public BillingSignal billingSignal(BillingSignal reportedSignal,
                                       RequestDeliveryState deliveryState) {
        if (deliveryState == RequestDeliveryState.NOT_SENT) {
            return BillingSignal.INELIGIBLE;
        }
        return billingAllowed && reportedSignal == BillingSignal.ELIGIBLE
                ? BillingSignal.ELIGIBLE : BillingSignal.INELIGIBLE;
    }

    public CacheSignal cacheSignal(CacheSignal reportedSignal) {
        return cacheAllowed && reportedSignal != null ? reportedSignal : CacheSignal.NOT_CACHEABLE;
    }

    public boolean canFallback(RequestDeliveryState deliveryState) {
        return fallbackAllowed && deliveryState == RequestDeliveryState.NOT_SENT;
    }

    private static Map<ErrorCategory, ConnectorErrorPolicy> buildIndex() {
        EnumMap<ErrorCategory, ConnectorErrorPolicy> result = new EnumMap<>(ErrorCategory.class);
        for (ConnectorErrorPolicy policy : values()) {
            if (result.put(policy.category, policy) != null) {
                throw new IllegalStateException("duplicate connector error policy: " + policy.category);
            }
        }
        if (result.size() != ErrorCategory.values().length) {
            throw new IllegalStateException("connector error policy table is not exhaustive");
        }
        return Map.copyOf(result);
    }
}
