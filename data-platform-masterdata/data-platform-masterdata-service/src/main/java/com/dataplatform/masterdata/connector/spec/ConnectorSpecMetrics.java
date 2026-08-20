package com.dataplatform.masterdata.connector.spec;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Best-effort, low-cardinality compiler metrics. Observability never changes the business result. */
@Component
public final class ConnectorSpecMetrics {
    private static final String UNKNOWN = "UNKNOWN";
    private static final java.util.Set<String> ERROR_CATEGORIES = java.util.Set.of(
            "COMPILATION_INPUT_INVALID", "CONNECTOR_SPEC_UNKNOWN_FIELD",
            "CONNECTOR_SPEC_VERSION_UNSUPPORTED", "CONNECTOR_PLUGIN_REF_INVALID",
            "CONNECTOR_CONFIG_INVALID", "PLUGIN_ARTIFACT_FACTS_INVALID", "PLUGIN_STATUS_INVALID",
            "PLUGIN_COMPATIBILITY_MISMATCH", "SECRET_REF_NOT_OWNED", "RESPONSE_MAPPING_INVALID",
            "RESPONSE_MAPPING_FORBIDDEN", "SECURITY_PIPELINE_INVALID", "CONNECTOR_SPEC_TOO_LARGE",
            "RESERVED_FIELD_FORBIDDEN", "COMPILED_TOPOLOGY_INVALID", "CANONICALIZATION_FAILED");
    private static final java.util.Set<String> CONVERSION_RESULTS = java.util.Set.of(
            "SUCCESS", "NOT_CONVERTIBLE", "CONFLICT", "INVALID", UNKNOWN);
    private final MeterRegistry registry;

    public ConnectorSpecMetrics(MeterRegistry registry) { this.registry = registry; }

    public void success(String pluginId, String version, String kind, String transport) {
        increment("connector_spec_compile_total", pluginId, version, kind, transport, "NONE");
    }

    public void failure(String pluginId, String version, String kind, String transport,
                        String errorCategory) {
        increment("connector_spec_compile_total", pluginId, version, kind, transport,
                safeCategory(errorCategory));
        increment("connector_spec_compile_failures_total", pluginId, version, kind, transport,
                safeCategory(errorCategory));
    }

    public void conversionSuccess() {
        conversionIncrement("connector_spec_conversion_total", "SUCCESS");
    }

    public void conversionFailure(String result) {
        String safeResult = safeConversionResult(result);
        conversionIncrement("connector_spec_conversion_total", safeResult);
        conversionIncrement("connector_spec_conversion_failures_total", safeResult);
    }

    private void increment(String name, String pluginId, String version, String kind,
                           String transport, String errorCategory) {
        try {
            registry.counter(name, List.of(
                    io.micrometer.core.instrument.Tag.of("pluginId", safe(pluginId)),
                    io.micrometer.core.instrument.Tag.of("pluginVersion", safe(version)),
                    io.micrometer.core.instrument.Tag.of("connectorKind", safe(kind)),
                    io.micrometer.core.instrument.Tag.of("transportMode", safe(transport)),
                    io.micrometer.core.instrument.Tag.of("errorCategory", errorCategory))).increment();
        } catch (RuntimeException ignored) {
            // Metrics are explicitly non-authoritative.
        }
    }

    private void conversionIncrement(String name, String result) {
        try {
            registry.counter(name, "conversionResult", result).increment();
        } catch (RuntimeException ignored) {
            // Metrics are explicitly non-authoritative.
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() || value.length() > 128 ? UNKNOWN : value;
    }

    private static String safeCategory(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        String result = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return result.length() > 64 || !ERROR_CATEGORIES.contains(result) ? UNKNOWN : result;
    }

    private static String safeConversionResult(String value) {
        if (value == null) return UNKNOWN;
        String result = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return CONVERSION_RESULTS.contains(result) ? result : UNKNOWN;
    }
}
