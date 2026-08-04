package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.StageCapability;
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

public final class PipelineCompiler {

    public static final int MAX_STAGES = 50;
    public static final int MAX_STAGE_CONFIG_BYTES = 64 * 1024;
    private final ConnectorPluginRegistry registry;
    private final PluginValidationContext validationContext;
    private final ObjectMapper mapper;

    public PipelineCompiler(ConnectorPluginRegistry registry,
                            PluginValidationContext validationContext,
                            ObjectMapper mapper) {
        this.registry = registry;
        this.validationContext = validationContext;
        this.mapper = mapper;
    }

    public CompiledConnectorPipeline compile(ConnectorPipelineDefinition definition) throws ConnectorException {
        List<ConnectorStageDefinition> stages = definition.stages().stream()
                .filter(ConnectorStageDefinition::enabled)
                .sorted(Comparator.comparingInt(ConnectorStageDefinition::order))
                .toList();
        validateTopology(stages);
        List<CompiledPipelineStep> compiled = new ArrayList<>();
        try {
            for (ConnectorStageDefinition stage : stages) {
                byte[] configBytes = mapper.writeValueAsBytes(stage.config());
                if (configBytes.length > MAX_STAGE_CONFIG_BYTES) {
                    throw new IllegalArgumentException("Stage config exceeds 64 KiB: " + stage.stageKey());
                }
                String actualHash = sha256(stage.config());
                if (!MessageDigest.isEqual(actualHash.getBytes(StandardCharsets.US_ASCII),
                        stage.configHash().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                    throw new IllegalArgumentException("Stage configHash mismatch: " + stage.stageKey());
                }
                PluginHandle.Lease lease = registry.acquire(stage.pluginId(), stage.pluginVersion());
                try {
                    ConnectorStageFactory factory = lease.handle().factory(stage.capability());
                    CompiledStageConfig config = new CompiledStageConfig(stage.stageKey(), stage.pluginId(),
                            stage.pluginVersion(), stage.capability(), stage.config(), actualHash);
                    lease.handle().withContextClassLoader(() -> {
                        factory.validate(stage.config(), validationContext);
                        return null;
                    });
                    ConnectorStage executable = lease.handle().withContextClassLoader(() -> factory.create(config));
                    if (executable == null || executable.capability() != stage.capability()) {
                        throw new IllegalArgumentException("Factory returned an invalid stage: " + stage.stageKey());
                    }
                    compiled.add(new CompiledPipelineStep(stage, executable, lease));
                } catch (Exception exception) {
                    lease.close();
                    if (exception instanceof ConnectorException connectorException) {
                        throw connectorException;
                    }
                    throw new IllegalArgumentException("Stage compilation failed: " + stage.stageKey(), exception);
                }
            }
            return new CompiledConnectorPipeline(definition, compiled);
        } catch (RuntimeException | ConnectorException exception) {
            compiled.forEach(step -> step.lease().close());
            throw exception;
        } catch (Exception exception) {
            compiled.forEach(step -> step.lease().close());
            throw new IllegalArgumentException("Pipeline compilation failed", exception);
        }
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
