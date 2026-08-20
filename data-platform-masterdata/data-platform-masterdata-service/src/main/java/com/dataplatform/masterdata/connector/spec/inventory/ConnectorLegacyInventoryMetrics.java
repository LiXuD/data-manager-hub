package com.dataplatform.masterdata.connector.spec.inventory;

import com.dataplatform.masterdata.connector.service.LegacyHttpConversionClassification;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Best-effort inventory metrics with fixed, low-cardinality labels only. */
@Component
public final class ConnectorLegacyInventoryMetrics {
    private static final String UNKNOWN = "UNKNOWN";
    private static final Set<String> RESULTS = Set.of(
            "INVALID_REQUEST", "FACT_INVALID", "PIPELINE_INVALID", "QUERY_FAILED", UNKNOWN);

    private final MeterRegistry registry;

    public ConnectorLegacyInventoryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void classified(LegacyHttpConversionClassification classification) {
        String value = classification == null ? UNKNOWN : classification.name();
        increment("connector_legacy_inventory_classifications_total", "classification", value);
    }

    public void failed(String result) {
        increment("connector_legacy_inventory_failures_total", "result", safeResult(result));
    }

    private void increment(String name, String tagName, String tagValue) {
        try {
            registry.counter(name, tagName, tagValue).increment();
        } catch (RuntimeException ignored) {
            // Observability is non-authoritative.
        }
    }

    private static String safeResult(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return RESULTS.contains(normalized) ? normalized : UNKNOWN;
    }
}
