package com.dataplatform.masterdata.connector.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.artifact.PluginCompatibility;
import com.dataplatform.common.plugin.runtime.ConnectorPluginMetadata;
import com.dataplatform.common.plugin.runtime.ConnectorPluginRegistry;
import com.dataplatform.common.plugin.runtime.DefaultPluginValidationContext;
import com.dataplatform.common.plugin.runtime.DefaultManagedTaskExecutor;
import com.dataplatform.common.plugin.runtime.DefaultPluginContext;
import com.dataplatform.common.plugin.runtime.JacksonObjectCodec;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.common.plugin.runtime.NoOpPluginMetricRecorder;
import com.dataplatform.common.plugin.runtime.PipelineCompiler;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorMetadata;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorPlugin;
import com.dataplatform.common.plugin.runtime.PluginHandle;
import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.dataplatform.common.security.pipeline.SecurityStepType;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
import com.dataplatform.plugin.spi.VendorParseResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConnectorSpecCompilerTest {

    private static final String PLUGIN_ID = "fixture-vendor";
    private static final String PLUGIN_VERSION = "2.0.0";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hostSingleMappingLocksTopologyOrdersConfigsAndDigests() {
        ConnectorSpecCompilationResult result = compiler().compile(input(
                artifact(ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING,
                        Set.of(StageCapability.REQUEST_BUILDER, StageCapability.REQUEST_PROCESSOR,
                                StageCapability.RESPONSE_PROCESSOR, StageCapability.RESPONSE_PARSER)),
                ConnectorPluginCatalogStatus.STAGING, ConnectorCompilationPurpose.DRAFT,
                spec(mapping()), 7, List.of(requestSecurity(), responseSecurity()), owned()));

        assertEquals(List.of("connector.request-builder", "connector.request-processor",
                        "platform.security.request.000", "platform.transport",
                        "platform.security.response.000", "connector.response-processor",
                        "connector.response-parser", "platform.response-normalizer"),
                result.stageDefinitions().stream().map(step -> step.stageKey()).toList());
        assertEquals(List.of(100, 200, 300, 400, 500, 600, 700, 800),
                result.stageDefinitions().stream().map(step -> step.order()).toList());
        assertEquals(1, result.stageDefinitions().stream()
                .filter(step -> step.capability() == StageCapability.TRANSPORT).count());
        var vendorStages = result.stageDefinitions().stream()
                .filter(step -> PLUGIN_ID.equals(step.pluginId())).toList();
        assertEquals(1, vendorStages.stream().map(step -> step.configHash()).distinct().count());
        assertEquals(1, vendorStages.stream().map(step -> step.config()).distinct().count());
        assertTrue(result.specHash().matches("[0-9a-f]{64}"));
        assertTrue(result.snapshotHash().matches("[0-9a-f]{64}"));
        assertTrue(result.compileHash().matches("[0-9a-f]{64}"));
        assertEquals(result.snapshotHash(), result.pipeline().integrityHash());
        assertEquals("V2_EMBEDDED", result.pipeline().hashAlgorithm());
        assertEquals(ConnectorSpecCompiler.COMPILER_VERSION, result.compilerVersion());
        assertEquals("alpha", result.stageDefinitions().get(2).config()
                .path("secretRefs").get(0).asText());
    }

    @Test
    void managedPluginNormalizedUsesVendorTransportAndNormalizerWithoutPlaceholders() {
        ConnectorSpecCompilationResult result = compiler().compile(input(
                artifact(ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP,
                        ConnectorOutputMode.PLUGIN_NORMALIZED,
                        Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                                StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER)),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.PUBLISH,
                spec(null), 1, List.of(), owned()));

        assertEquals(List.of("connector.request-builder", "connector.transport",
                        "connector.response-parser", "connector.response-normalizer"),
                result.stageDefinitions().stream().map(step -> step.stageKey()).toList());
        assertTrue(result.stageDefinitions().stream().allMatch(step -> step.enabled()));
    }

    @Test
    void compilerRejectsStatusCompatibilityModesUnknownReservedSchemaAndVersions() throws Exception {
        for (ConnectorPluginCatalogStatus status : List.of(
                ConnectorPluginCatalogStatus.IMPORTED, ConnectorPluginCatalogStatus.VERIFIED,
                ConnectorPluginCatalogStatus.STAGING_FAILED, ConnectorPluginCatalogStatus.DISABLED)) {
            assertInvalid("PLUGIN_STATUS_INVALID", input(artifact(), status,
                    ConnectorCompilationPurpose.DRAFT, spec(mapping()), 0, List.of(), owned()));
        }
        assertInvalid("PLUGIN_STATUS_INVALID", input(artifact(), ConnectorPluginCatalogStatus.STAGING,
                ConnectorCompilationPurpose.PUBLISH, spec(mapping()), 0, List.of(), owned()));
        assertInvalid("PLUGIN_COMPATIBILITY_MISMATCH", inputFor("OTHER", "TYPE", artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 0, List.of(), owned()));
        assertInvalid("RESPONSE_MAPPING_FORBIDDEN", input(
                artifact(ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP,
                        ConnectorOutputMode.PLUGIN_NORMALIZED,
                        Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                                StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER)),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 0, List.of(), owned()));

        ConnectorSpecDTO unknown = mapper.readValue("""
                {"specVersion":"1","plugin":{"pluginId":"fixture-vendor",
                 "pluginVersion":"2.0.0","latest":true},"config":{"endpoint":"ok"},
                 "responseMapping":null,"stageKey":"bad"}
                """, ConnectorSpecDTO.class);
        assertInvalid("CONNECTOR_SPEC_UNKNOWN_FIELD", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                unknown, 0, List.of(), owned()));

        ConnectorSpecDTO reserved = spec(mapping());
        reserved.setConfig(Map.of("endpoint", "ok", "nested", Map.of("__PlatformHash", "bad")));
        assertInvalid("RESERVED_FIELD_FORBIDDEN", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                reserved, 0, List.of(), owned()));
        ConnectorSpecDTO schemaInvalid = spec(mapping());
        schemaInvalid.setConfig(Map.of("unknown", "bad"));
        assertInvalid("CONNECTOR_CONFIG_INVALID", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                schemaInvalid, 0, List.of(), owned()));
        ConnectorSpecDTO duplicateMapping = spec(List.of(
                new ConnectorSpecDTO.ResponseMapping("same", "a", "field", null, "none"),
                new ConnectorSpecDTO.ResponseMapping("same", "b", "jsonPath", null, "toString")));
        assertInvalid("RESPONSE_MAPPING_INVALID", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                duplicateMapping, 0, List.of(), owned()));
        ConnectorSpecDTO blankMappingMode = spec(List.of(
                new ConnectorSpecDTO.ResponseMapping("name", "a", " ", null, null)));
        assertInvalid("RESPONSE_MAPPING_INVALID", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                blankMappingMode, 0, List.of(), owned()));
        assertInvalid("COMPILATION_INPUT_INVALID", inputFor("VENDOR", "TYPE", artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), Integer.MAX_VALUE + 1L, List.of(), owned()));
        assertInvalid("COMPILATION_INPUT_INVALID", new ConnectorSpecCompilationInput(
                0L, "VENDOR", "TYPE", spec(mapping()), artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, 0, List.of(), owned(),
                ConnectorCompilationPurpose.TEST));
        assertInvalid("COMPILATION_INPUT_INVALID", inputFor(" ", "TYPE", artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 0, List.of(), owned()));
    }

    @Test
    void securitySnapshotPresenceOwnershipAndSortingAreStrict() {
        assertTrue(compiler().compile(input(artifact(), ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, spec(mapping()), 0, null, owned()))
                .stageDefinitions().stream().noneMatch(step -> step.stageKey().startsWith("platform.security")));
        assertInvalid("SECURITY_VERSION_INVALID", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 1, null, owned()));
        assertTrue(compiler().compile(input(artifact(), ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, spec(mapping()), 1, List.of(), owned()))
                .stageDefinitions().stream().noneMatch(step -> step.stageKey().startsWith("platform.security")));
        assertInvalid("SECURITY_VERSION_INVALID", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 0, List.of(requestSecurity()), owned()));
        assertInvalid("SECRET_REF_NOT_OWNED", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 1, List.of(requestSecurity()), ref -> false));
        SecurityStepConfig missingDirection = requestSecurity();
        missingDirection.setDirection(null);
        assertInvalid("SECURITY_PIPELINE_INVALID", input(artifact(),
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 1, List.of(missingDirection), owned()));

        SecurityStepConfig later = requestSecurity();
        later.setId("later");
        later.setSortNo(20);
        SecurityStepConfig earlier = requestSecurity();
        earlier.setId("earlier");
        earlier.setSortNo(10);
        String first = compiler().compile(input(artifact(), ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, spec(mapping()), 2,
                List.of(later, earlier), owned())).snapshotHash();
        String second = compiler().compile(input(artifact(), ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, spec(mapping()), 2,
                List.of(earlier, later), owned())).snapshotHash();
        assertEquals(first, second);
    }

    @Test
    void snapshotsNestedInputsAndIsDeterministicAcrossMapsAndConfiguredMappers() {
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        List<Object> nested = new ArrayList<>(List.of("one"));
        config.put("options", nested);
        config.put("endpoint", "ok");
        ConnectorSpecDTO mutableSpec = new ConnectorSpecDTO("1",
                new ConnectorSpecDTO.PluginRef(PLUGIN_ID, PLUGIN_VERSION), config, mapping());
        SecurityStepConfig security = requestSecurity();
        Map<String, Object> securityConfig = new LinkedHashMap<>(security.getConfig());
        security.setConfig(securityConfig);
        ConnectorSpecCompilationInput frozen = input(artifact(), ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, mutableSpec, 1, List.of(security), owned());
        nested.add("mutated");
        config.put("later", true);
        securityConfig.put("secretRef", "beta");

        ConnectorSpecCompilationResult frozenResult = compiler().compile(frozen);
        assertFalse(frozenResult.canonicalSpec().contains("mutated"));
        assertFalse(frozenResult.canonicalSpec().contains("later"));
        assertEquals("alpha", frozenResult.stageDefinitions().stream()
                .filter(step -> step.stageKey().equals("platform.security.request.000"))
                .findFirst().orElseThrow().config().path("secretRefs").get(0).asText());

        LinkedHashMap<String, Object> reverse = new LinkedHashMap<>();
        reverse.put("endpoint", "ok");
        reverse.put("options", List.of("one"));
        ConnectorSpecDTO same = new ConnectorSpecDTO("1",
                new ConnectorSpecDTO.PluginRef(PLUGIN_ID, PLUGIN_VERSION), reverse, mapping());
        ConnectorSpecCompilationInput sameInput = input(artifact(), ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, same, 1, List.of(requestSecurity()), owned());
        ObjectMapper configured = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT);
        ConnectorSpecCompilationResult configuredResult = new ConnectorSpecCompiler(configured).compile(sameInput);
        assertEquals(frozenResult.canonicalSpec(), configuredResult.canonicalSpec());
        assertEquals(frozenResult.specHash(), configuredResult.specHash());
        assertEquals(frozenResult.snapshotHash(), configuredResult.snapshotHash());
        assertEquals(frozenResult.compileHash(), configuredResult.compileHash());
    }

    @Test
    void rejectsOversizeAndArtifactProjectionTamper() {
        ConnectorSpecDTO oversize = spec(mapping());
        oversize.setConfig(Map.of("endpoint", "x".repeat(140_000)));
        assertInvalid("CONNECTOR_SPEC_TOO_LARGE", input(artifactWithSchema("""
                {"type":"object","required":["endpoint"],"properties":{
                  "endpoint":{"type":"string"}},"additionalProperties":false}
                """), ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                oversize, 0, List.of(), owned()));

        VerifiedPluginArtifact good = artifact();
        VerifiedPluginArtifact tampered = new VerifiedPluginArtifact(good.pluginId(), good.version(),
                "1.0", good.displayName(), good.provider(), good.description(), good.entryClass(),
                good.artifactUri(), good.artifactSha256(), good.detachedSignature(), good.signingKeyId(),
                good.manifestJson(), good.configSchemaJson(), good.capabilities(),
                good.permissionManifestJson(), good.minHostVersion(), good.configSchema(),
                good.manifestVersion(), good.authoringModel(), good.connectorKind(),
                good.transportMode(), good.outputMode(), good.compatibility(), good.compatibilityJson());
        assertInvalid("PLUGIN_ARTIFACT_FACTS_INVALID", input(tampered,
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 0, List.of(), owned()));

        VerifiedPluginArtifact permissionsTampered = new VerifiedPluginArtifact(
                good.pluginId(), good.version(), good.spiVersion(), good.displayName(), good.provider(),
                good.description(), good.entryClass(), good.artifactUri(), good.artifactSha256(),
                good.detachedSignature(), good.signingKeyId(), good.manifestJson(),
                good.configSchemaJson(), good.capabilities(),
                "{\"networkProtocols\":[],\"networkHosts\":[]}", good.minHostVersion(),
                good.configSchema(), good.manifestVersion(), good.authoringModel(),
                good.connectorKind(), good.transportMode(), good.outputMode(),
                good.compatibility(), good.compatibilityJson());
        assertInvalid("PLUGIN_ARTIFACT_FACTS_INVALID", input(permissionsTampered,
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 0, List.of(), owned()));
    }

    @Test
    void compiledResultIsAcceptedByProductionPipelineCompiler() throws Exception {
        VerifiedPluginArtifact artifact = artifact();
        ConnectorSpecCompilationResult result = compiler().compile(input(artifact,
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 0, List.of(), owned()));
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        var context = pluginContext();
        RuntimeVendorPlugin vendor = new RuntimeVendorPlugin();
        vendor.initialize(context);
        PlatformCoreConnectorPlugin platform = new PlatformCoreConnectorPlugin();
        platform.initialize(context);
        registry.register(PluginHandle.builtIn(vendor, context));
        registry.register(PluginHandle.builtIn(platform, context));
        try {
            ConnectorPluginMetadata vendorMetadata = metadata(artifact);
            PipelineCompiler runtime = new PipelineCompiler(registry,
                    new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", owned()), mapper,
                    (pluginId, version) -> PlatformCoreConnectorMetadata.PLUGIN_ID.equals(pluginId)
                            ? PlatformCoreConnectorMetadata.metadata() : vendorMetadata);
            try (var compiled = runtime.compile(result.pipeline())) {
                assertFalse(compiled.destroyed());
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void genericHttpBindsStaticBuiltinFactsAndRunsConditionalValidation() {
        VerifiedPluginArtifact artifact = genericArtifact();
        ConnectorSpecDTO valid = genericSpec(Map.of(
                "endpoint", "https://vendor.example/api",
                "method", "GET",
                "auth", Map.of("type", "BEARER", "tokenRef", "alpha")));

        ConnectorSpecCompilationResult result = compiler().compile(inputFor(
                "ANY_VENDOR", "ANY_TYPE", artifact, ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, valid, 0, List.of(), owned()));

        assertEquals(List.of("connector.request-builder", "connector.request-processor",
                        "platform.transport", "connector.response-parser", "platform.response-normalizer"),
                result.stageDefinitions().stream().map(step -> step.stageKey()).toList());
        assertTrue(result.stageDefinitions().stream()
                .filter(step -> GenericHttpConnectorMetadata.PLUGIN_ID.equals(step.pluginId()))
                .allMatch(step -> GenericHttpConnectorMetadata.artifactSha256()
                        .equals(step.artifactSha256())));

        ConnectorSpecDTO missingSecret = genericSpec(Map.of(
                "endpoint", "https://vendor.example/api", "method", "GET",
                "auth", Map.of("type", "BEARER")));
        assertInvalid("GENERIC_HTTP_CONFIG_INVALID", inputFor(
                "ANY_VENDOR", "ANY_TYPE", artifact, ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, missingSecret, 0, List.of(), owned()));

        ConnectorSpecDTO unowned = genericSpec(Map.of(
                "endpoint", "https://vendor.example/api", "method", "GET",
                "auth", Map.of("type", "BEARER", "tokenRef", "other")));
        assertInvalid("CONNECTOR_CONFIG_INVALID", inputFor(
                "ANY_VENDOR", "ANY_TYPE", artifact, ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, unowned, 0, List.of(), owned()));

        ConnectorSpecDTO rawQuery = genericSpec(Map.of(
                "endpoint", "https://vendor.example/api?token=plaintext", "method", "GET",
                "auth", Map.of("type", "NONE")));
        assertInvalid("GENERIC_HTTP_CONFIG_INVALID", inputFor(
                "ANY_VENDOR", "ANY_TYPE", artifact, ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, rawQuery, 0, List.of(), owned()));

        ConnectorSpecDTO plaintextHeader = genericSpec(Map.of(
                "endpoint", "https://vendor.example/api", "method", "GET",
                "headers", List.of(Map.of("name", "X-Api-Key", "value", "plaintext")),
                "auth", Map.of("type", "NONE")));
        assertInvalid("GENERIC_HTTP_CONFIG_INVALID", inputFor(
                "ANY_VENDOR", "ANY_TYPE", artifact, ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, plaintextHeader, 0, List.of(), owned()));
    }

    @Test
    void genericHttpRejectsAnyStaticBuiltinDrift() {
        VerifiedPluginArtifact good = genericArtifact();
        VerifiedPluginArtifact drifted = new VerifiedPluginArtifact(
                good.pluginId(), good.version(), good.spiVersion(), good.displayName(), good.provider(),
                good.description(), good.entryClass(), "builtin://generic-http/drifted",
                good.artifactSha256(), good.detachedSignature(), good.signingKeyId(),
                good.manifestJson(), good.configSchemaJson(), good.capabilities(),
                good.permissionManifestJson(), good.minHostVersion(), good.configSchema(),
                good.manifestVersion(), good.authoringModel(), good.connectorKind(),
                good.transportMode(), good.outputMode(), good.compatibility(), good.compatibilityJson());

        assertInvalid("GENERIC_HTTP_BUILTIN_DRIFT", inputFor(
                "ANY_VENDOR", "ANY_TYPE", drifted, ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, genericSpec(Map.of(
                        "endpoint", "https://vendor.example/api", "method", "GET",
                        "auth", Map.of("type", "NONE"))), 0, List.of(), owned()));
    }

    @Test
    void genericHttpConfigCannotExceedTheRuntimeStageLimit() throws Exception {
        VerifiedPluginArtifact artifact = genericArtifact();
        Map<String, Object> belowLimit = genericConfigWithHeaders(16, 4_000);
        Map<String, Object> aboveLimit = genericConfigWithHeaders(17, 4_000);
        assertTrue(mapper.writeValueAsBytes(mapper.valueToTree(belowLimit)).length
                <= com.dataplatform.common.plugin.runtime.PipelineCompiler.MAX_STAGE_CONFIG_BYTES);
        assertTrue(mapper.writeValueAsBytes(mapper.valueToTree(aboveLimit)).length
                > com.dataplatform.common.plugin.runtime.PipelineCompiler.MAX_STAGE_CONFIG_BYTES);

        compiler().compile(inputFor("ANY_VENDOR", "ANY_TYPE", artifact,
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                genericSpec(belowLimit), 0, List.of(), owned()));
        assertInvalid("GENERIC_HTTP_CONFIG_INVALID", inputFor(
                "ANY_VENDOR", "ANY_TYPE", artifact, ConnectorPluginCatalogStatus.ACTIVE,
                ConnectorCompilationPurpose.TEST, genericSpec(aboveLimit), 0, List.of(), owned()));
    }

    private ConnectorSpecCompiler compiler() { return new ConnectorSpecCompiler(mapper); }

    private ConnectorSpecDTO genericSpec(Map<String, Object> config) {
        return new ConnectorSpecDTO("1", new ConnectorSpecDTO.PluginRef(
                GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION),
                config, null);
    }

    private Map<String, Object> genericConfigWithHeaders(int count, int valueLength) {
        List<Map<String, String>> headers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            headers.add(Map.of("name", "X-Field-" + index, "value", "v".repeat(valueLength)));
        }
        return Map.of(
                "endpoint", "https://vendor.example/api",
                "method", "GET",
                "headers", headers,
                "auth", Map.of("type", "NONE"));
    }

    private VerifiedPluginArtifact genericArtifact() {
        try {
            return new VerifiedPluginArtifact(
                    GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                    GenericHttpConnectorMetadata.SPI_VERSION, GenericHttpConnectorMetadata.DISPLAY_NAME,
                    GenericHttpConnectorMetadata.PROVIDER, GenericHttpConnectorMetadata.DESCRIPTION,
                    GenericHttpConnectorMetadata.ENTRY_CLASS, GenericHttpConnectorMetadata.ARTIFACT_URI,
                    GenericHttpConnectorMetadata.artifactSha256(),
                    GenericHttpConnectorMetadata.BUILTIN_SIGNATURE,
                    GenericHttpConnectorMetadata.BUILTIN_SIGNING_KEY,
                    GenericHttpConnectorMetadata.canonicalManifestJson(),
                    GenericHttpConnectorMetadata.canonicalSchemaJson(),
                    GenericHttpConnectorMetadata.CAPABILITY_NAMES,
                    GenericHttpConnectorMetadata.canonicalPermissionsJson(),
                    GenericHttpConnectorMetadata.MIN_HOST_VERSION,
                    GenericHttpConnectorMetadata.configSchema(), "2",
                    com.dataplatform.plugin.spi.ConnectorAuthoringModel.SIMPLE_CONNECTOR,
                    ConnectorKind.GENERIC_HTTP, ConnectorTransportMode.HOST_SINGLE_HTTP,
                    ConnectorOutputMode.HOST_MAPPING, GenericHttpConnectorMetadata.compatibility(),
                    GenericHttpConnectorMetadata.canonicalCompatibilityJson());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ConnectorSpecDTO spec(List<ConnectorSpecDTO.ResponseMapping> mappings) {
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("endpoint", "ok");
        config.put("options", List.of("one"));
        return new ConnectorSpecDTO("1", new ConnectorSpecDTO.PluginRef(PLUGIN_ID, PLUGIN_VERSION),
                config, mappings);
    }

    private List<ConnectorSpecDTO.ResponseMapping> mapping() {
        return List.of(new ConnectorSpecDTO.ResponseMapping(
                "companyName", "company.name", null, null, null));
    }

    private ConnectorSpecCompilationInput input(
            VerifiedPluginArtifact artifact, ConnectorPluginCatalogStatus status,
            ConnectorCompilationPurpose purpose, ConnectorSpecDTO spec, long securityVersion,
            List<SecurityStepConfig> security, java.util.function.Predicate<String> ownership) {
        return inputFor("VENDOR", "TYPE", artifact, status, purpose, spec,
                securityVersion, security, ownership);
    }

    private ConnectorSpecCompilationInput inputFor(
            String vendor, String dataType, VerifiedPluginArtifact artifact,
            ConnectorPluginCatalogStatus status, ConnectorCompilationPurpose purpose,
            ConnectorSpecDTO spec, long securityVersion, List<SecurityStepConfig> security,
            java.util.function.Predicate<String> ownership) {
        return new ConnectorSpecCompilationInput(7L, vendor, dataType, spec, artifact,
                status, securityVersion, security, ownership, purpose);
    }

    private java.util.function.Predicate<String> owned() {
        return ref -> Set.of("alpha", "beta").contains(ref);
    }

    private SecurityStepConfig requestSecurity() {
        SecurityStepConfig step = new SecurityStepConfig();
        step.setId("sign");
        step.setDirection(SecurityDirection.REQUEST);
        step.setStepType(SecurityStepType.HMAC);
        step.setSortNo(10);
        step.setEnabled(true);
        step.setConfig(Map.of("inputFrom", "BODY", "algorithm", "HMAC_SHA256",
                "secretRef", "alpha"));
        return step;
    }

    private SecurityStepConfig responseSecurity() {
        SecurityStepConfig step = new SecurityStepConfig();
        step.setId("decode");
        step.setDirection(SecurityDirection.RESPONSE);
        step.setStepType(SecurityStepType.DECODE);
        step.setSortNo(20);
        step.setEnabled(true);
        step.setConfig(Map.of("inputFrom", "BODY", "encoding", "BASE64"));
        return step;
    }

    private VerifiedPluginArtifact artifact() {
        return artifact(ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING,
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER));
    }

    private VerifiedPluginArtifact artifactWithSchema(String schema) {
        return artifact(ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING,
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER), schema);
    }

    private VerifiedPluginArtifact artifact(
            ConnectorTransportMode transport, ConnectorOutputMode output,
            Set<StageCapability> capabilities) {
        return artifact(transport, output, capabilities, """
                {"type":"object","required":["endpoint"],"properties":{
                  "endpoint":{"type":"string","maxLength":200000},
                  "options":{"type":"array","items":{"type":"string"}}},
                 "additionalProperties":false}
                """);
    }

    private VerifiedPluginArtifact artifact(
            ConnectorTransportMode transport, ConnectorOutputMode output,
            Set<StageCapability> capabilities, String schema) {
        try {
            List<String> orderedCapabilities = capabilities.stream().map(Enum::name).sorted().toList();
            String capabilityJson = mapper.writeValueAsString(orderedCapabilities);
            String manifest = """
                    {"manifestVersion":"2","pluginId":"fixture-vendor","version":"2.0.0",
                     "spiVersion":"1.1","displayName":"Fixture Vendor","provider":"test",
                     "entryClass":"example.FixtureVendorPlugin","authoringModel":"SIMPLE_CONNECTOR",
                     "connectorKind":"DEDICATED_VENDOR","transportMode":"%s","outputMode":"%s",
                     "capabilities":%s,"compatibility":{"vendorCodes":["VENDOR"],"dataTypeCodes":["TYPE"]},
                     "minHostVersion":"2.1.0","configSchema":%s,
                     "permissions":{"networkProtocols":["https"],"networkHosts":["vendor.example"]}}
                    """.formatted(transport.name(), output.name(), capabilityJson, schema);
            JsonNode schemaNode = mapper.readTree(schema);
            return new VerifiedPluginArtifact(PLUGIN_ID, PLUGIN_VERSION, "1.1", "Fixture Vendor",
                    "test", null, "example.FixtureVendorPlugin", "https://repo.example/vendor.jar",
                    "a".repeat(64), "signature", "key-1", manifest, schema,
                    orderedCapabilities, "{\"networkProtocols\":[\"https\"],"
                    + "\"networkHosts\":[\"vendor.example\"]}", "2.1.0", schemaNode,
                    "2", com.dataplatform.plugin.spi.ConnectorAuthoringModel.SIMPLE_CONNECTOR,
                    ConnectorKind.DEDICATED_VENDOR, transport, output,
                    new PluginCompatibility(Set.of("VENDOR"), Set.of("TYPE")),
                    "{\"vendorCodes\":[\"VENDOR\"],\"dataTypeCodes\":[\"TYPE\"]}");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ConnectorPluginMetadata metadata(VerifiedPluginArtifact artifact) {
        ConnectorSpecCompilationResult result = compiler().compile(input(artifact,
                ConnectorPluginCatalogStatus.ACTIVE, ConnectorCompilationPurpose.TEST,
                spec(mapping()), 0, List.of(), owned()));
        var vendorStep = result.stageDefinitions().stream()
                .filter(step -> PLUGIN_ID.equals(step.pluginId())).findFirst().orElseThrow();
        return new ConnectorPluginMetadata(PLUGIN_ID, PLUGIN_VERSION, vendorStep.artifactSha256(),
                vendorStep.manifestHash(), vendorStep.schemaHash(), artifact.configSchema(),
                "2", artifact.authoringModel(), artifact.connectorKind(), artifact.transportMode(),
                artifact.outputMode(), artifact.compatibility());
    }

    private void assertInvalid(String code, ConnectorSpecCompilationInput input) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> compiler().compile(input));
        assertTrue(error.getMessage().startsWith(code + ":"), error::getMessage);
    }

    private PluginContext pluginContext() {
        PluginLogger logger = new PluginLogger() {
            @Override public void debug(String event, Map<String, ?> safeFields) { }
            @Override public void info(String event, Map<String, ?> safeFields) { }
            @Override public void warn(String event, Map<String, ?> safeFields) { }
            @Override public void error(String event, Map<String, ?> safeFields) { }
        };
        return new DefaultPluginContext((request, execution) -> {
            throw new AssertionError("transport must not execute during compilation");
        }, ref -> new SecretValue("test-secret".toCharArray()), Clock.systemUTC(), logger,
                new NoOpPluginMetricRecorder(), new JacksonObjectCodec(mapper),
                new DefaultManagedTaskExecutor(Runnable::run));
    }

    private static final class RuntimeVendorPlugin extends AbstractVendorConnectorPlugin {
        private RuntimeVendorPlugin() {
            super(ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING);
        }
        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(PLUGIN_ID, PLUGIN_VERSION, "1.1", "Fixture Vendor", "test",
                    Set.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER));
        }
        @Override protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation) {
            return null;
        }
        @Override protected VendorParseResult parseResponse(
                VendorConnectorInvocation invocation, ConnectorRawResponse response) {
            return VendorParseResult.success(Map.of());
        }
    }
}
