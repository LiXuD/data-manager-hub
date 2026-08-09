package com.dataplatform.common.circuitbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

class CircuitBreakerManagerTest {

    @Test
    void typedFailureResultsContributeToCircuitFailureRate() {
        CircuitBreakerManager manager = new CircuitBreakerManager();

        for (int call = 0; call < 10; call++) {
            boolean result = manager.executeWithProtection("typed-vendor", () -> false, failed -> !failed);
            assertFalse(result);
        }

        assertEquals(CircuitBreaker.State.OPEN, manager.getCircuitBreaker("typed-vendor").getState());
        assertEquals(10, manager.getCircuitBreaker("typed-vendor").getMetrics().getNumberOfFailedCalls());
    }
}
