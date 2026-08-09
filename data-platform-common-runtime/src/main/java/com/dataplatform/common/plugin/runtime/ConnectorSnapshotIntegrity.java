package com.dataplatform.common.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical snapshot algorithms; V1 preserves old snapshot facts and adds a separate integrity fact. */
public final class ConnectorSnapshotIntegrity {

    private ConnectorSnapshotIntegrity() { }

    public static String v1SnapshotHash(ObjectMapper mapper, List<ConnectorStageDefinition> stages) {
        ArrayNode array = mapper.createArrayNode();
        for (ConnectorStageDefinition stage : stages) array.add(stageNode(mapper, stage, false));
        return sha256(mapper, array);
    }

    public static String v1IntegrityHash(ObjectMapper mapper, String snapshotHash,
                                         Iterable<ConnectorPluginMetadata> metadata) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("algorithm", ConnectorPipelineDefinition.V1_DERIVED);
        envelope.put("snapshotHash", snapshotHash);
        ArrayNode plugins = envelope.putArray("plugins");
        List<ConnectorPluginMetadata> sorted = new ArrayList<>();
        metadata.forEach(sorted::add);
        sorted.stream().collect(java.util.stream.Collectors.toMap(
                        item -> item.pluginId() + ":" + item.version(), item -> item,
                        (left, right) -> left, LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(ConnectorPluginMetadata::pluginId)
                        .thenComparing(ConnectorPluginMetadata::version))
                .forEach(item -> plugins.add(metadataNode(mapper, item)));
        return sha256(mapper, envelope);
    }

    public static String v2SnapshotHash(ObjectMapper mapper, List<ConnectorStageDefinition> stages) {
        ArrayNode array = mapper.createArrayNode();
        for (ConnectorStageDefinition stage : stages) array.add(stageNode(mapper, stage, true));
        return sha256(mapper, array);
    }

    public static String sha256(ObjectMapper mapper, JsonNode value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(sort(mapper, value))));
        } catch (Exception exception) {
            throw new IllegalStateException("Connector snapshot cannot be hashed", exception);
        }
    }

    private static ObjectNode stageNode(ObjectMapper mapper, ConnectorStageDefinition stage, boolean integrity) {
        ObjectNode node = mapper.createObjectNode();
        node.put("stageKey", stage.stageKey());
        node.put("capability", stage.capability().name());
        node.put("pluginId", stage.pluginId());
        node.put("pluginVersion", stage.pluginVersion());
        node.put("order", stage.order());
        node.put("enabled", stage.enabled());
        node.set("config", stage.config());
        node.put("configHash", stage.configHash());
        if (integrity) {
            node.put("artifactSha256", stage.artifactSha256());
            node.put("manifestHash", stage.manifestHash());
            node.put("schemaHash", stage.schemaHash());
        }
        return node;
    }

    private static ObjectNode metadataNode(ObjectMapper mapper, ConnectorPluginMetadata metadata) {
        ObjectNode node = mapper.createObjectNode();
        node.put("pluginId", metadata.pluginId());
        node.put("version", metadata.version());
        node.put("artifactSha256", metadata.artifactSha256());
        node.put("manifestHash", metadata.manifestHash());
        node.put("schemaHash", metadata.schemaHash());
        return node;
    }

    private static JsonNode sort(ObjectMapper mapper, JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.sort(String::compareTo);
            fields.forEach(field -> result.set(field, sort(mapper, value.get(field))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(sort(mapper, item)));
            return result;
        }
        return value;
    }
}
