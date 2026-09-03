package com.dataplatform.masterdata.connector.compiler;

import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
import com.dataplatform.common.plugin.runtime.ConnectorSnapshotIntegrity;
import com.dataplatform.common.plugin.runtime.ConnectorStageDefinition;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorConfigValidator;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorMetadata;
import com.dataplatform.common.plugin.schema.ConnectorJsonSchemaValidator;
import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityPipelineExecutor;
import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Pure deterministic compiler from the product-facing ConnectorSpec to a frozen runtime plan. */
public final class ConnectorSpecCompiler {

    public static final String COMPILER_VERSION = "1.0.0";
    public static final int MAX_CANONICAL_SPEC_BYTES = 128 * 1024;
    private static final int ORDER_INCREMENT = 100;
    private static final Set<String> RESERVED_KEYS = Set.of(
            "stagekey", "capability", "order", "enabled", "confighash", "artifactsha256",
            "manifesthash", "schemahash", "snapshot", "snapshothash", "pipeline",
            "pipelinesnapshot", "hashalgorithm", "integrityhash", "compilehash", "spechash",
            "compilerversion", "securityversion", "plan");
    private static final Set<String> MAPPING_SOURCE_TYPES = Set.of("field", "jsonPath");
    private static final Set<String> MAPPING_TRANSFORMS = Set.of("none", "toString", "toNumber");

    private final ObjectMapper mapper;
    private final ConnectorJsonSchemaValidator schemaValidator;

    public ConnectorSpecCompiler(ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "mapper");
        this.mapper = new ObjectMapper();
        this.schemaValidator = new ConnectorJsonSchemaValidator();
    }

    public ConnectorSpecCompilationResult compile(ConnectorSpecCompilationInput input) {
        Objects.requireNonNull(input, "input");
        if (input.vendorConfigId() == null || input.vendorConfigId() <= 0
                || blank(input.vendorCode()) || blank(input.dataTypeCode())
                || input.securityVersion() < 0 || input.securityVersion() > Integer.MAX_VALUE) {
            throw invalid("COMPILATION_INPUT_INVALID", "Connector compilation facts are invalid");
        }
        ConnectorSpecDTO spec = input.connectorSpec();
        validateSpecShape(spec);
        PluginManifest manifest = validateArtifact(input, spec);
        validateStatus(input);
        validateCompatibility(input, manifest);

        JsonNode config = mapper.valueToTree(spec.getConfig());
        rejectReservedFields(config, "$.config");
        if (GenericHttpConnectorMetadata.PLUGIN_ID.equals(manifest.pluginId())) {
            try {
                // Run the connector-specific structural checks first so malformed generic-http
                // configurations keep their domain error code. Secret ownership remains the
                // shared schema validator's responsibility below.
                GenericHttpConnectorConfigValidator.validate(config, ignored -> true);
            } catch (RuntimeException exception) {
                throw invalid("GENERIC_HTTP_CONFIG_INVALID",
                        "Generic HTTP configuration is invalid");
            }
        }
        List<String> schemaErrors = schemaValidator.validate(
                manifest.configSchema(), config, input.secretOwnedByVendor());
        if (!schemaErrors.isEmpty()) {
            throw invalid("CONNECTOR_CONFIG_INVALID", String.join("; ", schemaErrors));
        }
        schemaValidator.secretReferences(manifest.configSchema(), config).forEach(ref -> {
            if (!input.secretOwnedByVendor().test(ref)) {
                throw invalid("SECRET_REF_NOT_OWNED", "Connector secret reference is not vendor-owned");
            }
        });
        if (GenericHttpConnectorMetadata.PLUGIN_ID.equals(manifest.pluginId())) {
            try {
                GenericHttpConnectorConfigValidator.validate(config, input.secretOwnedByVendor());
            } catch (RuntimeException exception) {
                throw invalid("GENERIC_HTTP_CONFIG_INVALID",
                        "Generic HTTP configuration is invalid");
            }
        }

        validateResponseMapping(spec, manifest.outputMode());
        SecurityPlan security = validateSecurity(input);
        String canonicalSpec = canonicalSpec(spec);
        if (canonicalSpec.getBytes(StandardCharsets.UTF_8).length > MAX_CANONICAL_SPEC_BYTES) {
            throw invalid("CONNECTOR_SPEC_TOO_LARGE", "ConnectorSpec exceeds 128 KiB");
        }
        String specHash = hash(readTree(canonicalSpec));
        ArtifactHashes vendorHashes = artifactHashes(input.pluginArtifact(), manifest);
        List<ConnectorStageDefinition> stages = buildStages(
                manifest, vendorHashes, config, security, spec.getResponseMapping());
        validateFinalTopology(stages, manifest);
        String snapshotHash = ConnectorSnapshotIntegrity.v2SnapshotHash(mapper, stages);
        String compileHash = compileHash(specHash, snapshotHash, input.securityVersion());
        ConnectorPipelineDefinition pipeline = new ConnectorPipelineDefinition(
                compileHash, snapshotHash, ConnectorPipelineDefinition.V2_EMBEDDED,
                snapshotHash, stages);
        return new ConnectorSpecCompilationResult(canonicalSpec, specHash, snapshotHash,
                compileHash, COMPILER_VERSION, input.securityVersion(), pipeline, stages,
                stages.stream().map(this::toDto).toList());
    }

    private void validateSpecShape(ConnectorSpecDTO spec) {
        if (!spec.unknownFieldNames().isEmpty()) {
            throw invalid("CONNECTOR_SPEC_UNKNOWN_FIELD", "ConnectorSpec contains unknown fields");
        }
        if (!"1".equals(spec.getSpecVersion())) {
            throw invalid("CONNECTOR_SPEC_VERSION_UNSUPPORTED", "specVersion must be 1");
        }
        ConnectorSpecDTO.PluginRef plugin = spec.getPlugin();
        if (plugin == null || !plugin.unknownFieldNames().isEmpty()
                || blank(plugin.getPluginId()) || blank(plugin.getPluginVersion())) {
            throw invalid("CONNECTOR_PLUGIN_REF_INVALID", "Connector plugin coordinate is invalid");
        }
        if (spec.getConfig() == null || !mapper.valueToTree(spec.getConfig()).isObject()) {
            throw invalid("CONNECTOR_CONFIG_INVALID", "Connector config must be an object");
        }
        if (spec.getResponseMapping() != null && spec.getResponseMapping().stream()
                .anyMatch(item -> item == null || !item.unknownFieldNames().isEmpty())) {
            throw invalid("RESPONSE_MAPPING_INVALID", "Response mapping contains unknown fields");
        }
    }

    private PluginManifest validateArtifact(
            ConnectorSpecCompilationInput input, ConnectorSpecDTO spec) {
        VerifiedPluginArtifact artifact = input.pluginArtifact();
        try {
            PluginManifestReader reader = new PluginManifestReader(mapper);
            byte[] manifestBytes = required(artifact.manifestJson(), "manifestJson")
                    .getBytes(StandardCharsets.UTF_8);
            PluginManifest manifest = reader.read(manifestBytes);
            JsonNode schema = mapper.readTree(required(artifact.configSchemaJson(), "configSchemaJson"));
            if (!spec.getPlugin().getPluginId().equals(artifact.pluginId())
                    || !spec.getPlugin().getPluginVersion().equals(artifact.version())
                    || !artifact.pluginId().equals(manifest.pluginId())
                    || !artifact.version().equals(manifest.version())
                    || !Objects.equals(artifact.spiVersion(), manifest.spiVersion())
                    || !Objects.equals(artifact.displayName(), manifest.displayName())
                    || !Objects.equals(artifact.provider(), manifest.provider())
                    || !Objects.equals(artifact.entryClass(), manifest.entryClass())
                    || !Objects.equals(artifact.minHostVersion(), manifest.minHostVersion())
                    || !"2".equals(manifest.manifestVersion())
                    || manifest.authoringModel() != ConnectorAuthoringModel.SIMPLE_CONNECTOR
                    || !Objects.equals(artifact.manifestVersion(), manifest.manifestVersion())
                    || artifact.authoringModel() != manifest.authoringModel()
                    || artifact.connectorKind() != manifest.connectorKind()
                    || artifact.transportMode() != manifest.transportMode()
                    || artifact.outputMode() != manifest.outputMode()
                    || !artifact.compatibility().equals(manifest.compatibility())
                    || !Set.copyOf(artifact.capabilities()).equals(manifest.capabilities().stream()
                    .map(Enum::name).collect(java.util.stream.Collectors.toSet()))
                    || !schema.equals(manifest.configSchema())
                    || artifact.configSchema() == null
                    || !artifact.configSchema().equals(manifest.configSchema())
                    || artifact.capabilities().size() != Set.copyOf(artifact.capabilities()).size()
                    || !jsonEquals(artifact.permissionManifestJson(),
                    mapper.writeValueAsString(mapper.readTree(manifestBytes).path("permissions")))
                    || blank(artifact.artifactUri()) || blank(artifact.detachedSignature())
                    || blank(artifact.signingKeyId())
                    || !sha256(artifact.artifactSha256())) {
                throw invalid("PLUGIN_ARTIFACT_FACTS_INVALID",
                        "Verified plugin facts do not match the signed Manifest");
            }
            String expectedCompatibility = new String(reader.canonicalize(mapper.writeValueAsBytes(
                    mapper.readTree(manifestBytes).path("compatibility"))), StandardCharsets.UTF_8);
            if (!jsonEquals(expectedCompatibility, artifact.compatibilityJson())) {
                throw invalid("PLUGIN_ARTIFACT_FACTS_INVALID",
                        "Verified compatibility projection does not match the signed Manifest");
            }
            if (GenericHttpConnectorMetadata.PLUGIN_ID.equals(artifact.pluginId())) {
                validateGenericArtifact(artifact, reader);
            }
            return manifest;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("PLUGIN_ARTIFACT_FACTS_INVALID", "Verified plugin facts are invalid");
        }
    }

    private void validateGenericArtifact(
            VerifiedPluginArtifact artifact, PluginManifestReader reader) throws Exception {
        if (!GenericHttpConnectorMetadata.VERSION.equals(artifact.version())) {
            throw invalid("GENERIC_HTTP_BUILTIN_DRIFT", "Generic HTTP built-in coordinate is invalid");
        }
        String canonicalManifest = new String(reader.canonicalize(
                artifact.manifestJson().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        String canonicalSchema = new String(reader.canonicalize(
                artifact.configSchemaJson().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        String canonicalPermissions = new String(reader.canonicalize(
                artifact.permissionManifestJson().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        if (!GenericHttpConnectorMetadata.SPI_VERSION.equals(artifact.spiVersion())
                || !GenericHttpConnectorMetadata.DISPLAY_NAME.equals(artifact.displayName())
                || !GenericHttpConnectorMetadata.PROVIDER.equals(artifact.provider())
                || !GenericHttpConnectorMetadata.DESCRIPTION.equals(artifact.description())
                || !GenericHttpConnectorMetadata.ENTRY_CLASS.equals(artifact.entryClass())
                || !GenericHttpConnectorMetadata.ARTIFACT_URI.equals(artifact.artifactUri())
                || !GenericHttpConnectorMetadata.artifactSha256().equalsIgnoreCase(artifact.artifactSha256())
                || !GenericHttpConnectorMetadata.BUILTIN_SIGNATURE.equals(artifact.detachedSignature())
                || !GenericHttpConnectorMetadata.BUILTIN_SIGNING_KEY.equals(artifact.signingKeyId())
                || !GenericHttpConnectorMetadata.canonicalManifestJson().equals(canonicalManifest)
                || !GenericHttpConnectorMetadata.canonicalSchemaJson().equals(canonicalSchema)
                || !GenericHttpConnectorMetadata.canonicalPermissionsJson().equals(canonicalPermissions)
                || !GenericHttpConnectorMetadata.CAPABILITY_NAMES.equals(artifact.capabilities())
                || !GenericHttpConnectorMetadata.MIN_HOST_VERSION.equals(artifact.minHostVersion())
                || !"2".equals(artifact.manifestVersion())
                || artifact.authoringModel() != ConnectorAuthoringModel.SIMPLE_CONNECTOR
                || artifact.connectorKind() != com.dataplatform.plugin.spi.ConnectorKind.GENERIC_HTTP
                || artifact.transportMode() != ConnectorTransportMode.HOST_SINGLE_HTTP
                || artifact.outputMode() != ConnectorOutputMode.HOST_MAPPING
                || !GenericHttpConnectorMetadata.compatibility().equals(artifact.compatibility())
                || !GenericHttpConnectorMetadata.canonicalCompatibilityJson()
                .equals(artifact.compatibilityJson())) {
            throw invalid("GENERIC_HTTP_BUILTIN_DRIFT",
                    "Generic HTTP catalogue facts do not match host code");
        }
    }

    private void validateStatus(ConnectorSpecCompilationInput input) {
        boolean allowed = input.purpose() == ConnectorCompilationPurpose.PUBLISH
                ? input.pluginStatus() == ConnectorPluginCatalogStatus.ACTIVE
                : input.pluginStatus() == ConnectorPluginCatalogStatus.STAGING
                || input.pluginStatus() == ConnectorPluginCatalogStatus.ACTIVE;
        if (!allowed) {
            throw invalid("PLUGIN_STATUS_INVALID", "Plugin status is not valid for compilation purpose");
        }
    }

    private void validateCompatibility(ConnectorSpecCompilationInput input, PluginManifest manifest) {
        if (blank(input.vendorCode()) || blank(input.dataTypeCode())
                || !manifest.compatibility().supportsVendor(input.vendorCode())
                || !manifest.compatibility().supportsDataType(input.dataTypeCode())) {
            throw invalid("PLUGIN_COMPATIBILITY_MISMATCH",
                    "Plugin is not compatible with the vendor and data type");
        }
    }

    private void validateResponseMapping(ConnectorSpecDTO spec, ConnectorOutputMode outputMode) {
        List<ConnectorSpecDTO.ResponseMapping> mappings = spec.getResponseMapping();
        if (outputMode == ConnectorOutputMode.PLUGIN_NORMALIZED) {
            if (mappings != null) {
                throw invalid("RESPONSE_MAPPING_FORBIDDEN",
                        "PLUGIN_NORMALIZED connectors cannot define host mapping");
            }
            return;
        }
        if (mappings == null) return;
        if (mappings.isEmpty()) {
            throw invalid("RESPONSE_MAPPING_INVALID", "Response mapping cannot be empty");
        }
        Set<String> targets = new HashSet<>();
        for (ConnectorSpecDTO.ResponseMapping mapping : mappings) {
            String sourceType = defaultText(mapping.getSourceType(), "field");
            String transform = defaultText(mapping.getTransformType(), "none");
            if (blank(mapping.getTargetField()) || blank(mapping.getSourcePath())
                    || mapping.getSourceType() != null && mapping.getSourceType().isBlank()
                    || mapping.getTransformType() != null && mapping.getTransformType().isBlank()
                    || !mapping.getTargetField().equals(mapping.getTargetField().trim())
                    || !mapping.getSourcePath().equals(mapping.getSourcePath().trim())
                    || !targets.add(mapping.getTargetField())
                    || !MAPPING_SOURCE_TYPES.contains(sourceType)
                    || !MAPPING_TRANSFORMS.contains(transform)) {
                throw invalid("RESPONSE_MAPPING_INVALID", "Response mapping is invalid");
            }
        }
    }

    private SecurityPlan validateSecurity(ConnectorSpecCompilationInput input) {
        List<SecurityStepConfig> steps = input.securitySteps();
        if (input.securityVersion() == 0 && !steps.isEmpty()
                || input.securityVersion() > 0 && !input.securitySnapshotPresent()) {
            throw invalid("SECURITY_VERSION_INVALID", "Security version and snapshot do not match");
        }
        List<SecurityStepConfig> enabled = steps.stream()
                .filter(step -> !Boolean.FALSE.equals(step.getEnabled()))
                .sorted(Comparator.comparing(SecurityStepConfig::getSortNo,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SecurityStepConfig::getId,
                                Comparator.nullsLast(String::compareTo)))
                .toList();
        if (enabled.stream().anyMatch(step -> step.getDirection() == null)) {
            throw invalid("SECURITY_PIPELINE_INVALID", "Enabled security steps require a direction");
        }
        List<SecurityStepConfig> request = enabled.stream()
                .filter(step -> step.getDirection() == SecurityDirection.REQUEST).toList();
        List<SecurityStepConfig> response = enabled.stream()
                .filter(step -> step.getDirection() == SecurityDirection.RESPONSE).toList();
        if (request.size() + response.size() != enabled.size()) {
            throw invalid("SECURITY_PIPELINE_INVALID", "Enabled security steps require a valid direction");
        }
        try {
            SecurityPipelineExecutor executor = new SecurityPipelineExecutor();
            executor.validate(SecurityDirection.REQUEST, request);
            executor.validate(SecurityDirection.RESPONSE, response);
        } catch (RuntimeException exception) {
            throw invalid("SECURITY_PIPELINE_INVALID", "Security snapshot is invalid");
        }
        Set<String> requestRefs = securitySecretRefs(request, input.secretOwnedByVendor());
        Set<String> responseRefs = securitySecretRefs(response, input.secretOwnedByVendor());
        return new SecurityPlan(request, response, requestRefs, responseRefs);
    }

    private Set<String> securitySecretRefs(
            List<SecurityStepConfig> steps, Predicate<String> ownership) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (SecurityStepConfig step : steps) collectNamedSecretRefs(
                mapper.valueToTree(step.getConfig()), result);
        for (String ref : result) {
            if (!ownership.test(ref)) {
                throw invalid("SECRET_REF_NOT_OWNED", "Security secret reference is not vendor-owned");
            }
        }
        return result.stream().sorted().collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void collectNamedSecretRefs(JsonNode node, Set<String> result) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("secretRef".equals(entry.getKey())) {
                    if (!entry.getValue().isTextual() || blank(entry.getValue().asText())) {
                        throw invalid("SECURITY_PIPELINE_INVALID", "Security secretRef is invalid");
                    }
                    result.add(entry.getValue().asText());
                }
                collectNamedSecretRefs(entry.getValue(), result);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectNamedSecretRefs(child, result));
        }
    }

    private List<ConnectorStageDefinition> buildStages(
            PluginManifest manifest,
            ArtifactHashes vendorHashes,
            JsonNode vendorConfig,
            SecurityPlan security,
            List<ConnectorSpecDTO.ResponseMapping> mappings) {
        List<ConnectorStageDefinition> stages = new ArrayList<>();
        int order = ORDER_INCREMENT;
        order = addVendor(stages, manifest, vendorHashes, "connector.request-builder",
                StageCapability.REQUEST_BUILDER, order, vendorConfig);
        if (manifest.capabilities().contains(StageCapability.REQUEST_PROCESSOR)) {
            order = addVendor(stages, manifest, vendorHashes, "connector.request-processor",
                    StageCapability.REQUEST_PROCESSOR, order, vendorConfig);
        }
        if (!security.request().isEmpty()) {
            order = addPlatform(stages, "platform.security.request.000",
                    StageCapability.REQUEST_PROCESSOR, order,
                    securityConfig(SecurityDirection.REQUEST, security.request(), security.requestRefs()));
        }
        if (manifest.transportMode() == ConnectorTransportMode.HOST_SINGLE_HTTP) {
            order = addPlatform(stages, "platform.transport", StageCapability.TRANSPORT,
                    order, mapper.createObjectNode());
        } else {
            order = addVendor(stages, manifest, vendorHashes, "connector.transport",
                    StageCapability.TRANSPORT, order, vendorConfig);
        }
        if (!security.response().isEmpty()) {
            order = addPlatform(stages, "platform.security.response.000",
                    StageCapability.RESPONSE_PROCESSOR, order,
                    securityConfig(SecurityDirection.RESPONSE, security.response(), security.responseRefs()));
        }
        if (manifest.capabilities().contains(StageCapability.RESPONSE_PROCESSOR)) {
            order = addVendor(stages, manifest, vendorHashes, "connector.response-processor",
                    StageCapability.RESPONSE_PROCESSOR, order, vendorConfig);
        }
        order = addVendor(stages, manifest, vendorHashes, "connector.response-parser",
                StageCapability.RESPONSE_PARSER, order, vendorConfig);
        if (manifest.outputMode() == ConnectorOutputMode.HOST_MAPPING) {
            ObjectNode mappingConfig = mapper.createObjectNode();
            if (mappings == null) mappingConfig.putNull("responseMapping");
            else mappingConfig.set("responseMapping", mappingArray(mappings));
            addPlatform(stages, "platform.response-normalizer",
                    StageCapability.RESPONSE_NORMALIZER, order, mappingConfig);
        } else {
            addVendor(stages, manifest, vendorHashes, "connector.response-normalizer",
                    StageCapability.RESPONSE_NORMALIZER, order, vendorConfig);
        }
        return List.copyOf(stages);
    }

    private int addVendor(List<ConnectorStageDefinition> stages, PluginManifest manifest,
                          ArtifactHashes hashes, String key, StageCapability capability,
                          int order, JsonNode config) {
        add(stages, key, capability, manifest.pluginId(), manifest.version(), order, config,
                hashes.artifactSha256(), hashes.manifestHash(), hashes.schemaHash());
        return order + ORDER_INCREMENT;
    }

    private int addPlatform(List<ConnectorStageDefinition> stages, String key,
                            StageCapability capability, int order, JsonNode config) {
        add(stages, key, capability, PlatformCoreConnectorMetadata.PLUGIN_ID,
                PlatformCoreConnectorMetadata.VERSION, order, config,
                PlatformCoreConnectorMetadata.artifactSha256(),
                PlatformCoreConnectorMetadata.manifestSha256(),
                PlatformCoreConnectorMetadata.schemaSha256());
        return order + ORDER_INCREMENT;
    }

    private void add(List<ConnectorStageDefinition> stages, String key, StageCapability capability,
                     String pluginId, String version, int order, JsonNode config,
                     String artifactHash, String manifestHash, String schemaHash) {
        stages.add(new ConnectorStageDefinition(key, capability, pluginId, version, order, true,
                config, hash(config), artifactHash, manifestHash, schemaHash));
    }

    private ObjectNode securityConfig(SecurityDirection direction, List<SecurityStepConfig> steps,
                                      Set<String> refs) {
        ObjectNode config = mapper.createObjectNode();
        config.put("direction", direction.name());
        ArrayNode stepArray = config.putArray("securitySteps");
        for (SecurityStepConfig step : steps) {
            ObjectNode node = stepArray.addObject();
            if (step.getId() == null) node.putNull("id"); else node.put("id", step.getId());
            if (step.getDirection() == null) node.putNull("direction");
            else node.put("direction", step.getDirection().name());
            if (step.getStepType() == null) node.putNull("stepType");
            else node.put("stepType", step.getStepType().name());
            if (step.getStepName() == null) node.putNull("stepName");
            else node.put("stepName", step.getStepName());
            if (step.getSortNo() == null) node.putNull("sortNo"); else node.put("sortNo", step.getSortNo());
            if (step.getEnabled() == null) node.putNull("enabled"); else node.put("enabled", step.getEnabled());
            node.set("config", mapper.valueToTree(step.getConfig()));
        }
        ArrayNode array = config.putArray("secretRefs");
        refs.forEach(array::add);
        return config;
    }

    private void validateFinalTopology(List<ConnectorStageDefinition> stages, PluginManifest manifest) {
        if (stages.stream().filter(step -> step.capability() == StageCapability.TRANSPORT).count() != 1
                || stages.stream().map(ConnectorStageDefinition::stageKey).distinct().count() != stages.size()
                || stages.stream().map(ConnectorStageDefinition::order).distinct().count() != stages.size()
                || !stages.getFirst().stageKey().equals("connector.request-builder")
                || !stages.stream().anyMatch(step -> step.stageKey().equals("connector.response-parser"))
                || manifest.transportMode() == ConnectorTransportMode.HOST_SINGLE_HTTP
                != stages.stream().anyMatch(step -> step.stageKey().equals("platform.transport"))) {
            throw invalid("COMPILED_TOPOLOGY_INVALID", "Compiled connector topology is invalid");
        }
    }

    private String canonicalSpec(ConnectorSpecDTO spec) {
        ObjectNode node = mapper.createObjectNode();
        node.put("specVersion", spec.getSpecVersion());
        ObjectNode plugin = node.putObject("plugin");
        plugin.put("pluginId", spec.getPlugin().getPluginId());
        plugin.put("pluginVersion", spec.getPlugin().getPluginVersion());
        node.set("config", mapper.valueToTree(spec.getConfig()));
        if (spec.getResponseMapping() == null) node.putNull("responseMapping");
        else node.set("responseMapping", mappingArray(spec.getResponseMapping()));
        return canonical(node);
    }

    private String compileHash(String specHash, String snapshotHash, long securityVersion) {
        ObjectNode node = mapper.createObjectNode();
        node.put("specHash", specHash);
        node.put("snapshotHash", snapshotHash);
        node.put("compilerVersion", COMPILER_VERSION);
        node.put("securityVersion", securityVersion);
        return hash(node);
    }

    private ConnectorPipelineStepDTO toDto(ConnectorStageDefinition stage) {
        return new ConnectorPipelineStepDTO(stage.stageKey(), stage.capability().name(),
                stage.pluginId(), stage.pluginVersion(), stage.order(), stage.enabled(),
                mapper.convertValue(stage.config(), new com.fasterxml.jackson.core.type.TypeReference<>() { }),
                stage.configHash(), stage.artifactSha256(), stage.manifestHash(), stage.schemaHash());
    }

    private void rejectReservedFields(JsonNode node, String path) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String normalized = entry.getKey().toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9_]", "");
                if (normalized.startsWith("__platform")
                        || RESERVED_KEYS.contains(normalized.replace("_", ""))) {
                    throw invalid("RESERVED_FIELD_FORBIDDEN", "Reserved connector field at " + path);
                }
                rejectReservedFields(entry.getValue(), path + "." + entry.getKey());
            });
        } else if (node.isArray()) {
            node.forEach(child -> rejectReservedFields(child, path));
        }
    }

    private String canonical(JsonNode node) {
        try {
            return mapper.writeValueAsString(sort(node));
        } catch (Exception exception) {
            throw invalid("CANONICALIZATION_FAILED", "Connector data cannot be canonicalized");
        }
    }

    private String hash(JsonNode node) {
        return ConnectorSnapshotIntegrity.sha256(mapper, node);
    }

    private JsonNode readTree(String json) {
        try { return mapper.readTree(json); }
        catch (Exception exception) { throw invalid("CANONICALIZATION_FAILED", "Connector data is invalid"); }
    }

    private boolean jsonEquals(String left, String right) {
        try { return Objects.equals(mapper.readTree(left), mapper.readTree(right)); }
        catch (Exception exception) { return false; }
    }

    private ArtifactHashes artifactHashes(VerifiedPluginArtifact artifact, PluginManifest manifest) {
        try {
            PluginManifestReader reader = new PluginManifestReader(mapper);
            JsonNode canonicalManifest = mapper.readTree(reader.canonicalize(
                    artifact.manifestJson().getBytes(StandardCharsets.UTF_8)));
            return new ArtifactHashes(artifact.artifactSha256().toLowerCase(Locale.ROOT),
                    hash(canonicalManifest), hash(manifest.configSchema()));
        } catch (Exception exception) {
            throw invalid("PLUGIN_ARTIFACT_FACTS_INVALID", "Plugin hashes cannot be derived");
        }
    }

    private ArrayNode mappingArray(List<ConnectorSpecDTO.ResponseMapping> mappings) {
        ArrayNode array = mapper.createArrayNode();
        for (ConnectorSpecDTO.ResponseMapping mapping : mappings) {
            ObjectNode item = array.addObject();
            item.put("targetField", mapping.getTargetField());
            item.put("sourcePath", mapping.getSourcePath());
            item.put("sourceType", defaultText(mapping.getSourceType(), "field"));
            if (mapping.getDefaultValue() == null) item.putNull("defaultValue");
            else item.set("defaultValue", mapper.valueToTree(mapping.getDefaultValue()));
            item.put("transformType", defaultText(mapping.getTransformType(), "none"));
        }
        return array;
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.stream().sorted().forEach(field -> result.set(field, sort(value.get(field))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean sha256(String value) { return value != null && value.matches("(?i)[0-9a-f]{64}"); }
    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    private static String required(String value, String field) {
        if (blank(value)) throw invalid("PLUGIN_ARTIFACT_FACTS_INVALID", field + " is required");
        return value;
    }
    private static IllegalArgumentException invalid(String code, String message) {
        return new IllegalArgumentException(code + ": " + message);
    }

    private record SecurityPlan(
            List<SecurityStepConfig> request,
            List<SecurityStepConfig> response,
            Set<String> requestRefs,
            Set<String> responseRefs) { }

    private record ArtifactHashes(
            String artifactSha256,
            String manifestHash,
            String schemaHash) { }
}
