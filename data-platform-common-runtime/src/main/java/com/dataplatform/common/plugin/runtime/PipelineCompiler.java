package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageLifecycle;
import com.dataplatform.common.plugin.schema.ConnectorJsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PipelineCompiler {

    public static final int MAX_STAGES = 50;
    public static final int MAX_STAGE_CONFIG_BYTES = 64 * 1024;
    private final ConnectorPluginRegistry registry;
    private final PluginValidationContext validationContext;
    private final ObjectMapper mapper;
    private final ConnectorPluginMetadataResolver metadataResolver;
    private final ConnectorJsonSchemaValidator schemaValidator = new ConnectorJsonSchemaValidator();

    public PipelineCompiler(ConnectorPluginRegistry registry,
                            PluginValidationContext validationContext,
                            ObjectMapper mapper) {
        this(registry, validationContext, mapper, null);
    }

    public PipelineCompiler(ConnectorPluginRegistry registry,
                            PluginValidationContext validationContext,
                            ObjectMapper mapper,
                            ConnectorPluginMetadataResolver metadataResolver) {
        this.registry = registry;
        this.validationContext = validationContext;
        this.mapper = mapper;
        this.metadataResolver = metadataResolver;
    }

    public CompiledConnectorPipeline compile(ConnectorPipelineDefinition definition) throws ConnectorException {
        List<ConnectorStageDefinition> snapshotStages = definition.stages().stream()
                .sorted(Comparator.comparingInt(ConnectorStageDefinition::order))
                .toList();
        List<ConnectorStageDefinition> stages = snapshotStages.stream()
                .filter(ConnectorStageDefinition::enabled)
                .toList();
        validateTopology(stages);
        List<CompiledPipelineStep> compiled = new ArrayList<>();
        Map<String, ConnectorPluginMetadata> resolvedMetadata = new LinkedHashMap<>();
        Map<String, Set<String>> stageSecretReferences = new LinkedHashMap<>();
        try {
            for (ConnectorStageDefinition stage : snapshotStages) {
                byte[] configBytes = mapper.writeValueAsBytes(stage.config());
                if (configBytes.length > MAX_STAGE_CONFIG_BYTES) {
                    throw new IllegalArgumentException("Stage config exceeds 64 KiB: " + stage.stageKey());
                }
                String actualHash = sha256(stage.config());
                if (!MessageDigest.isEqual(actualHash.getBytes(StandardCharsets.US_ASCII),
                        stage.configHash().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                    throw new IllegalArgumentException("Stage configHash mismatch: " + stage.stageKey());
                }
                if (metadataResolver != null) {
                    ConnectorPluginMetadata metadata = metadataResolver.resolve(
                            stage.pluginId(), stage.pluginVersion());
                    validateHostMetadata(stage, metadata, definition.hashAlgorithm());
                    List<String> schemaErrors = schemaValidator.validate(metadata.configSchema(), stage.config());
                    if (!schemaErrors.isEmpty()) {
                        throw new IllegalArgumentException("Stage config violates signed Schema: "
                                + stage.stageKey() + ": " + String.join("; ", schemaErrors));
                    }
                    stageSecretReferences.put(stage.stageKey(),
                            schemaValidator.secretReferences(metadata.configSchema(), stage.config()));
                    resolvedMetadata.put(stage.pluginId() + ":" + stage.pluginVersion(), metadata);
                }
            }
            for (ConnectorStageDefinition stage : stages) {
                String actualHash = sha256(stage.config());
                PluginHandle.Lease lease = registry.acquire(stage.pluginId(), stage.pluginVersion());
                try {
                    ConnectorStageFactory factory = lease.handle().factory(stage.capability());
                    CompiledStageConfig config = new CompiledStageConfig(stage.stageKey(), stage.pluginId(),
                            stage.pluginVersion(), stage.capability(), stage.config(), actualHash);
                    lease.handle().withContextClassLoader(() -> {
                        factory.validate(stage.config(), validationContext);
                        return null;
                    });
                    StageLifecycle lifecycle = java.util.Objects.requireNonNull(
                            factory.lifecycle(), "Factory lifecycle is required");
                    ConnectorStage executable = null;
                    if (lifecycle == StageLifecycle.SHARED) {
                        executable = lease.handle().withContextClassLoader(() -> factory.create(config));
                        if (executable == null || executable.capability() != stage.capability()) {
                            throw new IllegalArgumentException("Factory returned an invalid stage: " + stage.stageKey());
                        }
                    }
                    compiled.add(new CompiledPipelineStep(
                            stage, factory, config, lifecycle, executable, lease,
                            stageSecretReferences.get(stage.stageKey())));
                } catch (Exception exception) {
                    lease.close();
                    if (exception instanceof ConnectorException connectorException) {
                        throw connectorException;
                    }
                    throw new IllegalArgumentException("Stage compilation failed: " + stage.stageKey(), exception);
                }
            }
            validateSnapshotIntegrity(definition, snapshotStages, resolvedMetadata.values());
            return new CompiledConnectorPipeline(definition, compiled);
        } catch (RuntimeException | ConnectorException exception) {
            compiled.forEach(CompiledPipelineStep::closePipelineResources);
            throw exception;
        } catch (Exception exception) {
            compiled.forEach(CompiledPipelineStep::closePipelineResources);
            throw new IllegalArgumentException("Pipeline compilation failed", exception);
        }
    }

    private void validateHostMetadata(ConnectorStageDefinition stage, ConnectorPluginMetadata metadata,
                                      String hashAlgorithm) {
        if (metadata == null || !stage.pluginId().equals(metadata.pluginId())
                || !stage.pluginVersion().equals(metadata.version()) || metadata.configSchema() == null) {
            throw new IllegalArgumentException("Fixed plugin metadata is unavailable: " + stage.stageKey());
        }
        if (ConnectorPipelineDefinition.V2_EMBEDDED.equals(hashAlgorithm)) {
            requireDigest(stage.artifactSha256(), metadata.artifactSha256(), "artifact", stage.stageKey());
            requireDigest(stage.manifestHash(), metadata.manifestHash(), "manifest", stage.stageKey());
            requireDigest(stage.schemaHash(), metadata.schemaHash(), "schema", stage.stageKey());
        }
    }

    private void requireDigest(String snapshot, String actual, String label, String stageKey) {
        if (snapshot == null || actual == null || !MessageDigest.isEqual(
                snapshot.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Stage " + label + " hash drift: " + stageKey);
        }
    }

    private void validateSnapshotIntegrity(ConnectorPipelineDefinition definition,
                                           List<ConnectorStageDefinition> stages,
                                           Iterable<ConnectorPluginMetadata> metadata) {
        if (metadataResolver == null || ConnectorPipelineDefinition.UNVERIFIED.equals(definition.hashAlgorithm())) {
            return;
        }
        String expectedSnapshot;
        String expectedIntegrity;
        if (ConnectorPipelineDefinition.V2_EMBEDDED.equals(definition.hashAlgorithm())) {
            expectedSnapshot = ConnectorSnapshotIntegrity.v2SnapshotHash(mapper, stages);
            expectedIntegrity = expectedSnapshot;
        } else if (ConnectorPipelineDefinition.V1_DERIVED.equals(definition.hashAlgorithm())) {
            expectedSnapshot = ConnectorSnapshotIntegrity.v1SnapshotHash(mapper, stages);
            expectedIntegrity = ConnectorSnapshotIntegrity.v1IntegrityHash(
                    mapper, definition.snapshotHash(), metadata);
        } else {
            throw new IllegalArgumentException("Unsupported connector snapshot hash algorithm");
        }
        requireDigest(definition.snapshotHash(), expectedSnapshot, "snapshot", definition.pipelineVersion());
        requireDigest(definition.integrityHash(), expectedIntegrity, "integrity", definition.pipelineVersion());
    }

    public String sha256(JsonNode config) {
        try {
            JsonNode sorted = sort(config);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(sorted)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Config cannot be hashed", exception);
        }
    }

    private void validateTopology(List<ConnectorStageDefinition> stages) {
        if (stages.isEmpty() || stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("Pipeline must contain 1 to " + MAX_STAGES + " enabled stages");
        }
        long transports = stages.stream().filter(stage -> stage.capability() == StageCapability.TRANSPORT).count();
        if (transports != 1) {
            throw new IllegalArgumentException("Pipeline must contain exactly one TRANSPORT stage");
        }
        Set<Integer> orders = new HashSet<>();
        Set<String> keys = new HashSet<>();
        int previousCapability = -1;
        for (ConnectorStageDefinition stage : stages) {
            if (!orders.add(stage.order()) || !keys.add(stage.stageKey())) {
                throw new IllegalArgumentException("Pipeline stage order and stageKey must be unique");
            }
            if (stage.capability().ordinal() < previousCapability) {
                throw new IllegalArgumentException("Pipeline capabilities are out of order at " + stage.stageKey());
            }
            previousCapability = stage.capability().ordinal();
        }
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            var result = mapper.createObjectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.sort(String::compareTo);
            fields.forEach(field -> result.set(field, sort(value.get(field))));
            return result;
        }
        if (value.isArray()) {
            var result = mapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value;
    }
}
