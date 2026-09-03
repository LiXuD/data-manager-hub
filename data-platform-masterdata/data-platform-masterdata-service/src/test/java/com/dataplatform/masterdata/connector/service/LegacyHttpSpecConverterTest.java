package com.dataplatform.masterdata.connector.service;

import static com.dataplatform.masterdata.connector.fixture.ConnectorProductModelFixtures.asynchronousPollingPipeline;
import static com.dataplatform.masterdata.connector.fixture.ConnectorProductModelFixtures.singleHttpLegacyPipeline;
import static com.dataplatform.masterdata.connector.fixture.ConnectorProductModelFixtures.tokenThenBusinessPipeline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyHttpSpecConverterTest {
    private final LegacyHttpSpecConverter converter = new LegacyHttpSpecConverter();
    private final LegacyHttpConversionPolicy platformPolicy = new LegacyHttpConversionPolicy(10_000);

    @Test
    void convertsSingleHttpFixtureToExactDeterministicGenericSpec() {
        List<ConnectorPipelineStepDTO> pipeline = singleHttpLegacyPipeline();

        LegacyHttpConversionResult conversion = converter.convert(pipeline, platformPolicy);
        LegacyHttpConversionPreflightResult result = conversion.preflight();

        assertTrue(result.convertible());
        assertEquals(LegacyHttpConversionClassification.LOSSLESS_CONVERTIBLE,
                result.classification());
        assertTrue(result.reasons().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> result.reasons().add(new LegacyHttpConversionReason(
                        LegacyHttpConversionReasonCode.EMPTY_PIPELINE, null, null, "immutable")));
        ConnectorSpecDTO spec = conversion.connectorSpec();
        assertEquals("1", spec.getSpecVersion());
        assertEquals("generic-http", spec.getPlugin().getPluginId());
        assertEquals("2.0.0", spec.getPlugin().getPluginVersion());
        assertEquals("https://fixture.example.test/vendor/single-http", spec.getConfig().get("endpoint"));
        assertEquals("POST", spec.getConfig().get("method"));
        assertEquals("application/json; charset=utf-8", spec.getConfig().get("contentType"));
        assertEquals(List.of(Map.of("name", "Accept", "value", "application/json")),
                spec.getConfig().get("headers"));
        assertEquals(List.of(Map.of("sourceField", "companyName", "targetField", "companyName",
                        "required", false, "transformType", "none")),
                spec.getConfig().get("requestMapping"));
        assertEquals(Map.of("type", "BEARER", "tokenRef", "vendor.fixture.token"),
                spec.getConfig().get("auth"));
        assertEquals(1, spec.getResponseMapping().size());
        assertEquals("data", spec.getResponseMapping().getFirst().getTargetField());
        assertEquals("company", spec.getResponseMapping().getFirst().getSourcePath());
        assertEquals(spec.getConfig(), converter.toConnectorSpec(pipeline, platformPolicy).getConfig());
    }

    @Test
    void rejectsFormerKeyedFixtureSemanticsAndUnsafeRequestShapes() {
        for (Map<String, Object> override : List.<Map<String, Object>>of(
                Map.of("apiUrl", "http://fixture.example.test/api"),
                Map.of("apiUrl", "https://fixture.example.test/api?token=x"),
                Map.of("apiUrl", "https://fixture.example.test/api#fragment"),
                Map.of("apiUrl", "https://fixture.example.test/%00"),
                Map.of("method", "HEAD"),
                Map.of("contentType", "application/x-www-form-urlencoded"),
                Map.of("idempotencyPolicy", "IDEMPOTENT_WITH_KEY", "idempotencyKey", "key"),
                Map.of("idempotencyPolicy", "IDEMPOTENT"),
                Map.of("requestMapping", Map.of("a.b", "a.b")),
                Map.of("requestMapping", Map.of("source", "target")),
                Map.of("headers", Map.of("X-Api-Key", "plaintext")),
                Map.of("headers", Map.of("X-Dupe", "a", "x-dupe", "b")))) {
            List<ConnectorPipelineStepDTO> candidate = replaceConfig(0, override);
            LegacyHttpConversionPreflightResult result = converter.preflight(candidate, platformPolicy);
            assertFalse(result.convertible(), () -> "unexpectedly convertible: " + override.keySet());
            assertThrows(IllegalArgumentException.class,
                    () -> converter.toConnectorSpec(candidate, platformPolicy));
        }
    }

    @Test
    void rejectsNonEquivalentAuthSecurityAndResponseMapping() {
        List<Map<String, Object>> processorConfigs = List.of(
                Map.of("authType", "BASIC", "authConfig", Map.of(
                        "username", "plaintext", "password", Map.of("secretRef", "password")),
                        "secretRefs", Map.of(), "securitySteps", List.of()),
                Map.of("authType", "API_KEY", "authConfig", Map.of(
                        "apiKeyName", "key", "apiKeyValue", Map.of("secretRef", "api.key"),
                        "apiKeyLocation", "query"), "secretRefs", Map.of(), "securitySteps", List.of()),
                Map.of("authType", "NONE", "authConfig", Map.of(),
                        "secretRefs", Map.of("token", "secret.ref"), "securitySteps", List.of()),
                Map.of("authType", "NONE", "authConfig", Map.of(),
                        "secretRefs", Map.of(), "securitySteps", List.of(Map.of("type", "HMAC"))),
                Map.of("authType", "NONE", "authConfig", Map.of(),
                        "secretRefs", Map.of(), "securitySteps", List.of(),
                        "legacySecretAlias", "token"));
        for (Map<String, Object> config : processorConfigs) {
            assertFalse(converter.preflight(replaceConfig(1, config), platformPolicy).convertible());
        }
        assertFalse(converter.preflight(replaceConfig(5,
                Map.of("responseMapping", List.of(Map.of("source", "target")))), platformPolicy)
                .convertible());
        assertFalse(converter.preflight(replaceConfig(5,
                Map.of("responseMapping", Map.of())), platformPolicy).convertible());
        List<ConnectorPipelineStepDTO> withoutNormalizer = new ArrayList<>(singleHttpLegacyPipeline());
        withoutNormalizer.removeLast();
        assertTrue(hasReason(converter.preflight(withoutNormalizer, platformPolicy),
                LegacyHttpConversionReasonCode.RESPONSE_NORMALIZER_MISSING));
    }

    @Test
    void routesTokenAndPollingFixturesToDedicatedPlugins() {
        LegacyHttpConversionPreflightResult token = converter.preflight(tokenThenBusinessPipeline());
        LegacyHttpConversionPreflightResult polling = converter.preflight(asynchronousPollingPipeline());

        assertEquals(LegacyHttpConversionClassification.REQUIRES_DEDICATED_PLUGIN,
                token.classification());
        assertEquals(LegacyHttpConversionClassification.REQUIRES_DEDICATED_PLUGIN,
                polling.classification());
        assertTrue(token.reasons().stream().allMatch(
                reason -> reason.code() == LegacyHttpConversionReasonCode.NON_LEGACY_PLUGIN));
        assertTrue(polling.reasons().stream().allMatch(
                reason -> reason.code() == LegacyHttpConversionReasonCode.NON_LEGACY_PLUGIN));
    }

    @Test
    void failsClosedOnUnknownFieldsWithoutMutatingInput() {
        List<ConnectorPipelineStepDTO> original = singleHttpLegacyPipeline();
        Map<String, Object> config = new LinkedHashMap<>(original.getFirst().config());
        config.put("vendorSpecificScript", "must-not-be-interpreted");
        Map<String, Object> beforeConfig = Map.copyOf(config);
        List<ConnectorPipelineStepDTO> candidate = new ArrayList<>(original);
        candidate.set(0, copy(original.getFirst(), config));

        LegacyHttpConversionPreflightResult result = converter.preflight(candidate, platformPolicy);

        assertFalse(result.convertible());
        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY,
                result.classification());
        assertTrue(result.reasons().stream().anyMatch(
                reason -> reason.code() == LegacyHttpConversionReasonCode.UNKNOWN_CONFIG_FIELD));
        assertEquals(beforeConfig, candidate.getFirst().config());
        assertTrue(result.reasons().stream().noneMatch(
                reason -> reason.detail().contains("must-not-be-interpreted")));
    }

    @Test
    void rejectsAmbiguousTopologyAndUnsupportedLegacyVersionDeterministically() {
        List<ConnectorPipelineStepDTO> candidate = new ArrayList<>(singleHttpLegacyPipeline());
        ConnectorPipelineStepDTO parser = candidate.get(4);
        candidate.set(4, new ConnectorPipelineStepDTO(parser.stageKey(), parser.capability(),
                parser.pluginId(), "1.1.0", parser.order(), parser.enabled(), parser.config(),
                parser.configHash()));
        candidate.add(new ConnectorPipelineStepDTO("second-transport", "TRANSPORT",
                "legacy-http", "1.0.0", 250, true, Map.of(), null));

        LegacyHttpConversionPreflightResult first = converter.preflight(candidate);
        LegacyHttpConversionPreflightResult second = converter.preflight(candidate);

        assertEquals(first, second);
        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY,
                first.classification());
        assertTrue(hasReason(first, LegacyHttpConversionReasonCode.LEGACY_VERSION_UNSUPPORTED));
        assertTrue(hasReason(first, LegacyHttpConversionReasonCode.TRANSPORT_COUNT_INVALID));
    }

    @Test
    void rejectsOutOfOrderCapabilitiesAndMissingEndpoint() {
        List<ConnectorPipelineStepDTO> outOfOrder = new ArrayList<>(singleHttpLegacyPipeline());
        ConnectorPipelineStepDTO processor = outOfOrder.get(1);
        outOfOrder.set(1, new ConnectorPipelineStepDTO(processor.stageKey(), processor.capability(),
                processor.pluginId(), processor.pluginVersion(), 450, processor.enabled(),
                processor.config(), processor.configHash()));

        LegacyHttpConversionPreflightResult orderResult = converter.preflight(outOfOrder);

        List<ConnectorPipelineStepDTO> missingEndpoint = new ArrayList<>(singleHttpLegacyPipeline());
        ConnectorPipelineStepDTO builder = missingEndpoint.getFirst();
        missingEndpoint.set(0, copy(builder, Map.of("method", "POST")));
        LegacyHttpConversionPreflightResult endpointResult =
                converter.preflight(missingEndpoint, platformPolicy);

        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY,
                orderResult.classification());
        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY,
                endpointResult.classification());
        assertTrue(hasReason(orderResult, LegacyHttpConversionReasonCode.CAPABILITY_ORDER_INVALID));
        assertTrue(hasReason(endpointResult, LegacyHttpConversionReasonCode.REQUEST_ENDPOINT_MISSING));
    }

    @Test
    void requiresMatchingPlatformPolicyBeforeDeclaringLosslessConversion() {
        LegacyHttpConversionPreflightResult missingPolicy =
                converter.preflight(singleHttpLegacyPipeline());
        LegacyHttpConversionPreflightResult mismatchedPolicy = converter.preflight(
                singleHttpLegacyPipeline(), new LegacyHttpConversionPolicy(30_000));

        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY,
                missingPolicy.classification());
        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY,
                mismatchedPolicy.classification());
        assertTrue(hasReason(missingPolicy, LegacyHttpConversionReasonCode.PLATFORM_POLICY_REQUIRED));
        assertTrue(hasReason(mismatchedPolicy, LegacyHttpConversionReasonCode.PLATFORM_TIMEOUT_MISMATCH));
    }

    @Test
    void rejectsFractionalTimeoutThatWouldBeTruncatedByNumericConversion() {
        LegacyHttpConversionPreflightResult result = converter.preflight(
                replaceConfig(0, Map.of("connectTimeoutMs", 10_000.5D)), platformPolicy);

        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY, result.classification());
        assertTrue(hasReason(result, LegacyHttpConversionReasonCode.PLATFORM_TIMEOUT_MISMATCH));
    }

    @Test
    void rejectsFractionalResponseLimitThatWouldBeTruncatedByNumericConversion() {
        LegacyHttpConversionPreflightResult result = converter.preflight(
                replaceConfig(0, Map.of("maxResponseBytes", 10 * 1024 * 1024 + 0.5D)), platformPolicy);

        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY, result.classification());
        assertTrue(hasReason(result, LegacyHttpConversionReasonCode.RESPONSE_LIMIT_UNSUPPORTED));
    }

    @Test
    void classifiesPersistedPipelineParsingFailuresAsLegacyOnly() {
        LegacyHttpConversionPreflightResult empty =
                converter.assessMigrationEligibility("", 10_000);
        LegacyHttpConversionPreflightResult malformed =
                converter.assessMigrationEligibility("{not-json", 10_000);

        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY, empty.classification());
        assertEquals(LegacyHttpConversionReasonCode.PIPELINE_SNAPSHOT_INVALID,
                empty.reasons().getFirst().code());
        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY, malformed.classification());
        assertEquals(LegacyHttpConversionReasonCode.PIPELINE_SNAPSHOT_INVALID,
                malformed.reasons().getFirst().code());
    }

    @Test
    void rejectsPlaintextAuthenticationWithoutEchoingTheSecret() {
        List<ConnectorPipelineStepDTO> candidate = new ArrayList<>(singleHttpLegacyPipeline());
        ConnectorPipelineStepDTO processor = candidate.get(1);
        Map<String, Object> plaintext = Map.of(
                "authType", "BEARER",
                "authConfig", Map.of("token", "fixture-plaintext-secret"),
                "secretRefs", Map.of(),
                "securitySteps", List.of());
        candidate.set(1, copy(processor, plaintext));

        LegacyHttpConversionPreflightResult result = converter.preflight(candidate, platformPolicy);

        assertEquals(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY,
                result.classification());
        assertTrue(hasReason(result, LegacyHttpConversionReasonCode.SECRET_REFERENCE_REQUIRED));
        assertTrue(result.reasons().stream().noneMatch(
                reason -> reason.detail().contains("fixture-plaintext-secret")));
    }

    @Test
    void convertedFixturePreservesOfflineRequestAndResponseFactsWithoutDualSending() {
        List<ConnectorPipelineStepDTO> pipeline = singleHttpLegacyPipeline();
        ConnectorSpecDTO spec = converter.toConnectorSpec(pipeline, platformPolicy);
        Map<String, Object> standardInput = Map.of("companyName", "Acme Ltd");

        Map<String, Object> legacyRequest = applyLegacyFlatMapping(
                standardInput, pipeline.getFirst().config().get("requestMapping"));
        Map<String, Object> genericRequest = applyGenericMapping(
                standardInput, spec.getConfig().get("requestMapping"));
        assertEquals(legacyRequest, genericRequest);
        assertEquals(pipeline.getFirst().config().get("apiUrl"), spec.getConfig().get("endpoint"));
        assertEquals(pipeline.getFirst().config().get("method"), spec.getConfig().get("method"));
        assertEquals(Map.of("Accept", "application/json"), flattenedHeaders(spec.getConfig()));
        assertEquals("vendor.fixture.token",
                ((Map<?, ?>) spec.getConfig().get("auth")).get("tokenRef"));

        Map<String, Object> parsedResponse = Map.of(
                "company", Map.of("name", "Acme Ltd", "status", "ACTIVE"));
        Map<String, Object> legacyResponse = applyLegacyFlatMapping(
                parsedResponse, pipeline.getLast().config().get("responseMapping"));
        Map<String, Object> genericResponse = applyGenericResponseMapping(
                parsedResponse, spec.getResponseMapping());
        assertEquals(legacyResponse, genericResponse);
    }

    private boolean hasReason(LegacyHttpConversionPreflightResult result,
                              LegacyHttpConversionReasonCode code) {
        return result.reasons().stream().anyMatch(reason -> reason.code() == code);
    }

    private ConnectorPipelineStepDTO copy(ConnectorPipelineStepDTO source,
                                          Map<String, Object> config) {
        return new ConnectorPipelineStepDTO(source.stageKey(), source.capability(), source.pluginId(),
                source.pluginVersion(), source.order(), source.enabled(), config, source.configHash(),
                source.artifactSha256(), source.manifestHash(), source.schemaHash());
    }

    private List<ConnectorPipelineStepDTO> replaceConfig(int index, Map<String, Object> overrides) {
        List<ConnectorPipelineStepDTO> candidate = new ArrayList<>(singleHttpLegacyPipeline());
        Map<String, Object> config = new LinkedHashMap<>(candidate.get(index).config());
        config.putAll(overrides);
        candidate.set(index, copy(candidate.get(index), config));
        return candidate;
    }

    private Map<String, Object> applyLegacyFlatMapping(
            Map<String, Object> source, Object mappingValue) {
        Map<?, ?> mapping = (Map<?, ?>) mappingValue;
        Map<String, Object> result = new LinkedHashMap<>();
        mapping.forEach((from, to) -> result.put((String) to, source.get(from)));
        return result;
    }

    private Map<String, Object> applyGenericMapping(
            Map<String, Object> source, Object mappingValue) {
        List<?> mappings = (List<?>) mappingValue;
        Map<String, Object> result = new LinkedHashMap<>(source);
        for (Object value : mappings) {
            Map<?, ?> mapping = (Map<?, ?>) value;
            result.put((String) mapping.get("targetField"),
                    source.get(mapping.get("sourceField")));
        }
        return result;
    }

    private Map<String, Object> flattenedHeaders(Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : (List<?>) config.get("headers")) {
            Map<?, ?> header = (Map<?, ?>) value;
            result.put((String) header.get("name"), header.get("value"));
        }
        return result;
    }

    private Map<String, Object> applyGenericResponseMapping(
            Map<String, Object> source, List<ConnectorSpecDTO.ResponseMapping> mappings) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ConnectorSpecDTO.ResponseMapping mapping : mappings) {
            result.put(mapping.getTargetField(), source.get(mapping.getSourcePath()));
        }
        return result;
    }
}
