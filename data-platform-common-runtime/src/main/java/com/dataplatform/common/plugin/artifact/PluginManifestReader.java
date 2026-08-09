package com.dataplatform.common.plugin.artifact;

import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PluginManifestReader {

    public static final String MANIFEST_PATH = "META-INF/data-platform/plugin.json";
    public static final int MAX_MANIFEST_BYTES = 256 * 1024;
    public static final int MAX_SCHEMA_BYTES = 128 * 1024;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9-]{2,63}");
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?(?:[-+][0-9A-Za-z.-]+)?");
    private static final Pattern CLASS_PATTERN = Pattern.compile("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+");

    private final ObjectMapper mapper;

    public PluginManifestReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public PluginManifest read(byte[] manifestBytes) {
        if (manifestBytes == null || manifestBytes.length == 0 || manifestBytes.length > MAX_MANIFEST_BYTES) {
            throw new PluginArtifactException("Manifest size is invalid");
        }
        try {
            JsonNode root = mapper.readTree(manifestBytes);
            requireObject(root, "manifest");
            rejectUnknownFields(root, Set.of("manifestVersion", "pluginId", "version", "spiVersion",
                    "displayName", "provider", "entryClass", "capabilities", "minHostVersion",
                    "configSchema", "permissions"));
            String manifestVersion = text(root, "manifestVersion");
            if (!"1".equals(manifestVersion)) {
                throw new PluginArtifactException("Unsupported manifestVersion: " + manifestVersion);
            }
            String pluginId = text(root, "pluginId");
            if (!ID_PATTERN.matcher(pluginId).matches()) {
                throw new PluginArtifactException("Invalid pluginId");
            }
            String version = version(root, "version");
            String spiVersion = version(root, "spiVersion");
            String minHostVersion = version(root, "minHostVersion");
            String entryClass = text(root, "entryClass");
            if (!CLASS_PATTERN.matcher(entryClass).matches()) {
                throw new PluginArtifactException("Invalid entryClass");
            }
            JsonNode capabilitiesNode = root.required("capabilities");
            if (!capabilitiesNode.isArray() || capabilitiesNode.isEmpty()) {
                throw new PluginArtifactException("capabilities must be a non-empty array");
            }
            EnumSet<StageCapability> capabilities = EnumSet.noneOf(StageCapability.class);
            capabilitiesNode.forEach(node -> {
                try {
                    capabilities.add(StageCapability.valueOf(node.asText()));
                } catch (IllegalArgumentException exception) {
                    throw new PluginArtifactException("Unknown capability: " + node.asText());
                }
            });
            JsonNode schema = root.required("configSchema");
            requireObject(schema, "configSchema");
            if (mapper.writeValueAsBytes(schema).length > MAX_SCHEMA_BYTES) {
                throw new PluginArtifactException("Config Schema exceeds 128 KiB");
            }
            validateSchemaPolicy(schema, "$");
            validateLocalReferenceGraph(schema);
            PluginPermissions permissions = permissions(root.required("permissions"));
            return new PluginManifest(manifestVersion, pluginId, version, spiVersion,
                    text(root, "displayName"), text(root, "provider"), entryClass,
                    capabilities, minHostVersion, schema, permissions);
        } catch (PluginArtifactException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new PluginArtifactException("Manifest is invalid", exception);
        }
    }

    public byte[] canonicalize(byte[] manifestBytes) {
        try {
            return CanonicalJson.write(mapper, mapper.readTree(manifestBytes));
        } catch (IOException exception) {
            throw new PluginArtifactException("Manifest is invalid", exception);
        }
    }

    private PluginPermissions permissions(JsonNode node) {
        requireObject(node, "permissions");
        rejectUnknownFields(node, Set.of("networkProtocols", "networkHosts"));
        var protocols = stringArray(node.required("networkProtocols"), "networkProtocols");
        var hosts = stringArray(node.required("networkHosts"), "networkHosts");
        if (protocols.stream().anyMatch(protocol -> !"https".equals(protocol.toLowerCase(Locale.ROOT)))) {
            throw new PluginArtifactException("Only HTTPS network protocol is allowed");
        }
        if (hosts.stream().anyMatch(host -> host.isBlank() || host.contains("/") || host.contains(":"))) {
            throw new PluginArtifactException("networkHosts must contain host names only");
        }
        return new PluginPermissions(protocols, hosts);
    }

    private java.util.List<String> stringArray(JsonNode node, String field) {
        if (!node.isArray()) {
            throw new PluginArtifactException(field + " must be an array");
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        node.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new PluginArtifactException(field + " contains an invalid value");
            }
            result.add(value.asText());
        });
        return result;
    }

    private void validateSchemaPolicy(JsonNode node, String path) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                JsonNode child = node.get(name);
                if ("$dynamicRef".equals(name) || "$recursiveRef".equals(name)) {
                    throw new PluginArtifactException(
                            "Dynamic or recursive Schema references are forbidden at " + path);
                }
                if ("$ref".equals(name) && (!child.isTextual() || !isLocalJsonPointer(child.asText()))) {
                    throw new PluginArtifactException(
                            "Remote or unsupported Schema references are forbidden at " + path);
                }
                if ("default".equals(name) && Boolean.TRUE.equals(node.path("x-secret-ref").asBoolean(false))) {
                    throw new PluginArtifactException("Secret reference fields cannot define defaults at " + path);
                }
                validateSchemaPolicy(child, path + "." + name);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                validateSchemaPolicy(node.get(index), path + "[" + index + "]");
            }
        }
    }

    private void validateLocalReferenceGraph(JsonNode root) {
        Set<JsonNode> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<JsonNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visitSchemaNode(root, root, "$", visiting, visited);
    }

    private void visitSchemaNode(JsonNode root, JsonNode node, String path,
                                 Set<JsonNode> visiting, Set<JsonNode> visited) {
        if (node == null || node.isValueNode() || visited.contains(node)) {
            return;
        }
        if (!visiting.add(node)) {
            throw new PluginArtifactException("Recursive local Schema reference is forbidden at " + path);
        }
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null && reference.isTextual() && isLocalJsonPointer(reference.asText())) {
                JsonNode target = resolveLocalPointer(root, reference.asText(), path);
                visitSchemaNode(root, target, path + ".$ref", visiting, visited);
            }
            node.fields().forEachRemaining(entry -> {
                if (!"$ref".equals(entry.getKey())) {
                    visitSchemaNode(root, entry.getValue(), path + "." + entry.getKey(), visiting, visited);
                }
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                visitSchemaNode(root, node.get(index), path + "[" + index + "]", visiting, visited);
            }
        }
        visiting.remove(node);
        visited.add(node);
    }

    private boolean isLocalJsonPointer(String reference) {
        return "#".equals(reference) || reference.startsWith("#/");
    }

    private JsonNode resolveLocalPointer(JsonNode root, String reference, String path) {
        JsonNode target = "#".equals(reference) ? root : root.at(reference.substring(1));
        if (target.isMissingNode()) {
            throw new PluginArtifactException("Local Schema reference does not resolve at " + path);
        }
        return target;
    }

    private String version(JsonNode root, String field) {
        String value = text(root, field);
        if (!VERSION_PATTERN.matcher(value).matches()) {
            throw new PluginArtifactException("Invalid " + field);
        }
        return value;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.required(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new PluginArtifactException(field + " must be non-blank text");
        }
        return value.asText();
    }

    private void requireObject(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            throw new PluginArtifactException(field + " must be an object");
        }
    }

    private void rejectUnknownFields(JsonNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new PluginArtifactException("Unknown manifest field: " + field);
            }
        });
    }
}
