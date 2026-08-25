package com.dataplatform.plugin.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ConnectorErrorPolicyTest {

    @Test
    void policyTableIsExhaustiveAndCategoryStable() {
        Set<ErrorCategory> covered = Arrays.stream(ConnectorErrorPolicy.values())
                .map(ConnectorErrorPolicy::category)
                .collect(Collectors.toSet());

        assertEquals(Set.of(ErrorCategory.values()), covered);
        for (ErrorCategory category : ErrorCategory.values()) {
            ConnectorErrorPolicy policy = ConnectorErrorPolicy.forCategory(category);
            assertEquals(category, policy.category());
            assertEquals(category.name(), policy.externalCode());
            assertFalse(policy.cacheAllowed());
        }
    }

    @Test
    void retryFallbackAndCircuitPoliciesAreExplicit() {
        Set<ErrorCategory> retryable = Arrays.stream(ConnectorErrorPolicy.values())
                .filter(ConnectorErrorPolicy::retryAllowed)
                .map(ConnectorErrorPolicy::category).collect(Collectors.toSet());
        Set<ErrorCategory> circuitFailures = Arrays.stream(ConnectorErrorPolicy.values())
                .filter(ConnectorErrorPolicy::circuitFailure)
                .map(ConnectorErrorPolicy::category).collect(Collectors.toSet());

        assertEquals(Set.of(ErrorCategory.TRANSPORT_TIMEOUT,
                ErrorCategory.TRANSPORT_CONNECTION_ERROR), retryable);
        assertEquals(Set.of(ErrorCategory.TRANSPORT_TIMEOUT,
                ErrorCategory.TRANSPORT_CONNECTION_ERROR,
                ErrorCategory.TRANSPORT_HTTP_ERROR,
                ErrorCategory.RESPONSE_SECURITY_ERROR,
                ErrorCategory.RESPONSE_PARSE_ERROR,
                ErrorCategory.BUSINESS_REJECTED), circuitFailures);
        assertTrue(ConnectorErrorPolicy.TRANSPORT_TIMEOUT.canFallback(RequestDeliveryState.NOT_SENT));
        assertFalse(ConnectorErrorPolicy.TRANSPORT_TIMEOUT.canFallback(RequestDeliveryState.MAYBE_SENT));
        assertFalse(ConnectorErrorPolicy.RESPONSE_PARSE_ERROR.canFallback(RequestDeliveryState.NOT_SENT));
        assertEquals(RequestDeliveryState.MAYBE_SENT,
                ConnectorErrorPolicy.TRANSPORT_TIMEOUT.deliveryState(null));
        assertEquals(RequestDeliveryState.NOT_SENT,
                ConnectorErrorPolicy.TRANSPORT_TIMEOUT.deliveryState(RequestDeliveryState.NOT_SENT));
        assertEquals(BillingSignal.ELIGIBLE,
                ConnectorErrorPolicy.BUSINESS_REJECTED.billingSignal(BillingSignal.ELIGIBLE));
        assertEquals(BillingSignal.INELIGIBLE,
                ConnectorErrorPolicy.BUSINESS_REJECTED.billingSignal(BillingSignal.UNKNOWN));
        assertEquals(BillingSignal.INELIGIBLE,
                ConnectorErrorPolicy.CONTRACT_VIOLATION.billingSignal(BillingSignal.ELIGIBLE));
        assertEquals(BillingSignal.INELIGIBLE,
                ConnectorErrorPolicy.TRANSPORT_TIMEOUT.billingSignal(
                        BillingSignal.ELIGIBLE, RequestDeliveryState.NOT_SENT));
        assertEquals(BillingSignal.ELIGIBLE,
                ConnectorErrorPolicy.TRANSPORT_TIMEOUT.billingSignal(
                        BillingSignal.ELIGIBLE, RequestDeliveryState.MAYBE_SENT));
        assertEquals(BillingSignal.ELIGIBLE,
                ConnectorErrorPolicy.TRANSPORT_CONNECTION_ERROR.billingSignal(
                        BillingSignal.ELIGIBLE, RequestDeliveryState.SENT));
        assertEquals(CacheSignal.NOT_CACHEABLE,
                ConnectorErrorPolicy.BUSINESS_REJECTED.cacheSignal(CacheSignal.CACHEABLE));
    }
}
