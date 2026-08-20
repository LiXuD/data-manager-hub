package com.dataplatform.masterdata.connector.spec.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dataplatform.masterdata.connector.service.LegacyHttpConversionClassification;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ConnectorLegacyInventoryMetricsTest {

    @Test
    void recordsOnlyFixedClassificationAndResultLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ConnectorLegacyInventoryMetrics metrics = new ConnectorLegacyInventoryMetrics(registry);

        metrics.classified(LegacyHttpConversionClassification.LOSSLESS_CONVERTIBLE);
        metrics.failed("tenant 918273 secret-specific failure");

        var classification = registry.find(
                "connector_legacy_inventory_classifications_total").counter();
        assertEquals(1.0, classification.count());
        assertEquals("LOSSLESS_CONVERTIBLE", classification.getId().getTag("classification"));
        assertEquals(1, classification.getId().getTags().size());
        assertNull(classification.getId().getTag("vendorConfigId"));

        var failure = registry.find("connector_legacy_inventory_failures_total").counter();
        assertEquals(1.0, failure.count());
        assertEquals("UNKNOWN", failure.getId().getTag("result"));
        assertEquals(1, failure.getId().getTags().size());
        assertNull(failure.getId().getTag("vendorCode"));
    }
}
