package com.dataplatform.masterdata.connector.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ConnectorSpecMetricsTest {

    @Test
    void recordsOnlyAllowedLowCardinalityTagsAndCollapsesArbitraryMessages() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ConnectorSpecMetrics metrics = new ConnectorSpecMetrics(registry);
        metrics.failure("vendor-plugin", "1.2.3", "DEDICATED_VENDOR", "HOST_SINGLE_HTTP",
                "secret id 918273 arbitrary customer message");

        var counter = registry.find("connector_spec_compile_failures_total").counter();
        assertEquals(1.0, counter.count());
        assertEquals("UNKNOWN", counter.getId().getTag("errorCategory"));
        assertEquals(5, counter.getId().getTags().size());
        assertEquals(null, counter.getId().getTag("vendorConfigId"));
        assertEquals(null, counter.getId().getTag("message"));

        metrics.conversionFailure("secret tenant-specific reason 918273");
        var conversion = registry.find("connector_spec_conversion_failures_total").counter();
        assertEquals(1.0, conversion.count());
        assertEquals("UNKNOWN", conversion.getId().getTag("conversionResult"));
        assertEquals(1, conversion.getId().getTags().size());
    }
}
