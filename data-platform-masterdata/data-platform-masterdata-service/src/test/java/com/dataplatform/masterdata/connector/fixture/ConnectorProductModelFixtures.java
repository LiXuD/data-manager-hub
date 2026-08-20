package com.dataplatform.masterdata.connector.fixture;

import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import java.util.List;
import java.util.Map;

/** Reusable pipeline shapes for product-model conversion and later runtime contract tests. */
public final class ConnectorProductModelFixtures {
    private ConnectorProductModelFixtures() {
    }

    public static List<ConnectorPipelineStepDTO> singleHttpLegacyPipeline() {
        return List.of(
                legacy("request-builder", "REQUEST_BUILDER", 0, Map.ofEntries(
                        Map.entry("apiUrl", "https://fixture.example.test/vendor/single-http"),
                        Map.entry("method", "POST"),
                        Map.entry("headers", Map.of("Accept", "application/json")),
                        Map.entry("contentType", "application/json; charset=utf-8"),
                        Map.entry("requestMapping", Map.of("companyName", "companyName")),
                        Map.entry("connectTimeoutMs", 10_000),
                        Map.entry("readTimeoutMs", 10_000),
                        Map.entry("totalTimeoutMs", 10_000),
                        Map.entry("idempotencyPolicy", "NON_IDEMPOTENT"),
                        Map.entry("maxResponseBytes", 10 * 1024 * 1024))),
                legacy("request-security", "REQUEST_PROCESSOR", 100, Map.of(
                        "authType", "BEARER",
                        "authConfig", Map.of("token", Map.of("secretRef", "vendor.fixture.token")),
                        "secretRefs", Map.of(),
                        "securitySteps", List.of())),
                legacy("transport", "TRANSPORT", 200, Map.of()),
                legacy("response-security", "RESPONSE_PROCESSOR", 300, Map.of(
                        "secretRefs", Map.of(), "securitySteps", List.of())),
                legacy("response-parser", "RESPONSE_PARSER", 400, Map.of()),
                legacy("response-normalizer", "RESPONSE_NORMALIZER", 500,
                        Map.of("responseMapping", Map.of("company", "data"))));
    }

    public static List<ConnectorPipelineStepDTO> tokenThenBusinessPipeline() {
        return dedicated("fixture-token-business", Map.of(
                "flow", "TOKEN_THEN_BUSINESS", "maxManagedCalls", 2));
    }

    public static List<ConnectorPipelineStepDTO> asynchronousPollingPipeline() {
        return dedicated("fixture-async-polling", Map.of(
                "flow", "SUBMIT_THEN_POLL", "maxManagedCalls", 5, "terminalPoll", 2));
    }

    private static List<ConnectorPipelineStepDTO> dedicated(String pluginId,
                                                            Map<String, Object> transportConfig) {
        return List.of(
                step("request-builder", "REQUEST_BUILDER", pluginId, 0, Map.of()),
                step("managed-transport", "TRANSPORT", pluginId, 100, transportConfig),
                step("response-parser", "RESPONSE_PARSER", pluginId, 200, Map.of()));
    }

    private static ConnectorPipelineStepDTO legacy(String key, String capability, int order,
                                                   Map<String, Object> config) {
        return step(key, capability, "legacy-http", order, config);
    }

    private static ConnectorPipelineStepDTO step(String key, String capability, String pluginId,
                                                 int order, Map<String, Object> config) {
        return new ConnectorPipelineStepDTO(key, capability, pluginId, "1.0.0", order,
                true, config, null);
    }
}
