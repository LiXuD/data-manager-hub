package com.dataplatform.common.plugin.artifact;

import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final Set<String> V1_FIELDS = Set.of(
            "manifestVersion", "pluginId", "version", "spiVersion", "displayName", "provider",
            "description", "entryClass", "capabilities", "minHostVersion", "configSchema", "permissions");
    private static final Set<String> V2_FIELDS = Set.of(
            "manifestVersion", "pluginId", "version", "spiVersion", "displayName", "provider",
            "description", "entryClass", "authoringModel", "connectorKind", "transportMode", "outputMode",
            "capabilities", "compatibility", "minHostVersion", "configSchema", "permissions");

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
            String manifestVersion = text(root, "manifestVersion");
            boolean manifestV2 = "2".equals(manifestVersion);
            if (!"1".equals(manifestVersion) && !manifestV2) {
                throw new PluginArtifactException("Unsupported manifestVersion: " + manifestVersion);
            }
            rejectUnknownFields(root, manifestV2 ? V2_FIELDS : V1_FIELDS);
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
            EnumSet<StageCapability> capabilities = capabilities(root.required("capabilities"), manifestV2);
            JsonNode schema = root.required("configSchema");
            requireObject(schema, "configSchema");
            if (mapper.writeValueAsBytes(schema).length > MAX_SCHEMA_BYTES) {
                throw new PluginArtifactException("Config Schema exceeds 128 KiB");
            }
            validateSchemaPolicy(schema, "$", manifestV2, capabilities);
            validateLocalReferenceGraph(schema);
            PluginPermissions permissions = permissions(root.required("permissions"));
            String displayName = text(root, "displayName");
            String provider = text(root, "provider");
            if (!manifestV2) {
                return new PluginManifest(manifestVersion, pluginId, version, spiVersion,
                        displayName, provider, entryClass, capabilities,
                        minHostVersion, schema, permissions);
            }
            ConnectorAuthoringModel authoringModel = enumValue(
                    root, "authoringModel", ConnectorAuthoringModel.class);
            ConnectorKind connectorKind = enumValue(root, "connectorKind", ConnectorKind.class);
            ConnectorTransportMode transportMode = enumValue(
                    root, "transportMode", ConnectorTransportMode.class);
            ConnectorOutputMode outputMode = enumValue(root, "outputMode", ConnectorOutputMode.class);
            PluginCompatibility compatibility = compatibility(root.required("compatibility"));
            validateV2Topology(authoringModel, transportMode, outputMode, capabilities);
            return new PluginManifest(manifestVersion, pluginId, version, spiVersion,
                    displayName, provider, entryClass, capabilities, minHostVersion, schema, permissions,
                    authoringModel, connectorKind, transportMode, outputMode, compatibility);
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

    private List<String> stringArray(JsonNode node, String field) {
        if (!node.isArray()) {
            throw new PluginArtifactException(field + " must be an array");
        }
        List<String> result = new ArrayList<>();
        node.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new PluginArtifactException(field + " contains an invalid value");
            }
            result.add(value.asText());
        });
        return result;
    }

    private EnumSet<StageCapability> capabilities(JsonNode node, boolean rejectDuplicates) {
        if (!node.isArray() || node.isEmpty()) {
            throw new PluginArtifactException("capabilities must be a non-empty array");
        }
        EnumSet<StageCapability> result = EnumSet.noneOf(StageCapability.class);
        node.forEach(value -> {
            if (!value.isTextual()) {
                throw new PluginArtifactException("capabilities contains an invalid value");
            }
            StageCapability capability;
            try {
                capability = StageCapability.valueOf(value.asText());
            } catch (IllegalArgumentException exception) {
                throw new PluginArtifactException("Unknown capability: " + value.asText());
            }
            if (rejectDuplicates && !result.add(capability)) {
                throw new PluginArtifactException("Duplicate capability: " + capability);
            }
            result.add(capability);
        });
        return result;
    }

    private PluginCompatibility compatibility(JsonNode node) {
        requireObject(node, "compatibility");
        rejectUnknownFields(node, Set.of("vendorCodes", "dataTypeCodes"));
        Set<String> vendorCodes = compatibilityCodes(node.get("vendorCodes"), "vendorCodes");
        Set<String> dataTypeCodes = compatibilityCodes(node.get("dataTypeCodes"), "dataTypeCodes");
        if (vendorCodes.isEmpty() && dataTypeCodes.isEmpty()) {
            throw new PluginArtifactException(
                    "compatibility must declare vendorCodes or dataTypeCodes");
        }
        return new PluginCompatibility(vendorCodes, dataTypeCodes);
    }

    private Set<String> compatibilityCodes(JsonNode node, String field) {
        if (node == null) {
            return Set.of();
        }
        if (!node.isArray()) {
            throw new PluginArtifactException("compatibility." + field + " must be an array");
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        node.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank()
                    || !value.asText().equals(value.asText().trim())) {
                throw new PluginArtifactException(
                        "compatibility." + field + " contains an invalid value");
            }
            if (!values.add(value.asText())) {
                throw new PluginArtifactException(
                        "compatibility." + field + " contains a duplicate value");
            }
        });
        if (values.contains("*") && values.size() != 1) {
            throw new PluginArtifactException(
                    "compatibility." + field + " cannot mix wildcard and explicit values");
        }
        return Collections.unmodifiableSet(values);
    }

    private void validateV2Topology(
            ConnectorAuthoringModel authoringModel,
            ConnectorTransportMode transportMode,
            ConnectorOutputMode outputMode,
            Set<StageCapability> capabilities) {
        if (authoringModel == ConnectorAuthoringModel.SIMPLE_CONNECTOR
                && (!capabilities.contains(StageCapability.REQUEST_BUILDER)
                || !capabilities.contains(StageCapability.RESPONSE_PARSER))) {
            throw new PluginArtifactException(
                    "SIMPLE_CONNECTOR requires REQUEST_BUILDER and RESPONSE_PARSER");
        }
        boolean transport = capabilities.contains(StageCapability.TRANSPORT);
        if (transportMode == ConnectorTransportMode.HOST_SINGLE_HTTP && transport) {
            throw new PluginArtifactException("HOST_SINGLE_HTTP must not declare TRANSPORT");
        }
        if (transportMode == ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP && !transport) {
            throw new PluginArtifactException("HOST_MANAGED_MULTI_HTTP must declare TRANSPORT");
        }
        boolean normalizer = capabilities.contains(StageCapability.RESPONSE_NORMALIZER);
        if (outputMode == ConnectorOutputMode.PLUGIN_NORMALIZED && !normalizer) {
            throw new PluginArtifactException(
                    "PLUGIN_NORMALIZED must declare RESPONSE_NORMALIZER");
        }
        if (outputMode == ConnectorOutputMode.HOST_MAPPING && normalizer) {
            throw new PluginArtifactException("HOST_MAPPING must not declare RESPONSE_NORMALIZER");
        }
    }

    private <T extends Enum<T>> T enumValue(JsonNode root, String field, Class<T> type) {
        String value = text(root, field);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new PluginArtifactException("Unknown " + field + ": " + value);
        }
    }

    private void validateSchemaPolicy(
            JsonNode node,
            String path,
            boolean manifestV2,
            Set<StageCapability> capabilities) {
        if (node.isObject()) {
            validateSchemaExtensions(node, path, manifestV2, capabilities);
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
                validateSchemaPolicy(child, path + "." + name, manifestV2, capabilities);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                validateSchemaPolicy(node.get(index), path + "[" + index + "]", manifestV2, capabilities);
            }
        }
    }

    private void validateSchemaExtensions(
            JsonNode node,
            String path,
            boolean manifestV2,
            Set<StageCapability> capabilities) {
        if (!manifestV2) {
            return;
        }
        node.fieldNames().forEachRemaining(name -> {
            if (isScriptExtension(name)) {
                throw new PluginArtifactException("Script Schema extensions are forbidden at " + path);
            }
        });
        JsonNode secretFlag = node.get("x-secret-ref");
        if (secretFlag != null && !secretFlag.isBoolean()) {
            throw new PluginArtifactException("x-secret-ref must be boolean at " + path);
        }
        JsonNode scope = node.get("x-stage-scope");
        if (secretFlag != null && secretFlag.asBoolean() && (scope == null || !scope.isArray() || scope.isEmpty())) {
            throw new PluginArtifactException(
                    "Secret reference fields require non-empty x-stage-scope at " + path);
        }
        if (scope != null) {
            validateStageScope(scope, path, capabilities);
        }
    }

    private void validateStageScope(
            JsonNode scope,
            String path,
            Set<StageCapability> capabilities) {
        if (!scope.isArray() || scope.isEmpty()) {
            throw new PluginArtifactException("x-stage-scope must be a non-empty array at " + path);
        }
        EnumSet<StageCapability> seen = EnumSet.noneOf(StageCapability.class);
        scope.forEach(value -> {
            if (!value.isTextual()) {
                throw new PluginArtifactException("x-stage-scope contains an invalid value at " + path);
            }
            StageCapability capability;
            try {
                capability = StageCapability.valueOf(value.asText());
            } catch (IllegalArgumentException exception) {
                throw new PluginArtifactException(
                        "x-stage-scope contains an unknown capability at " + path);
            }
            if (!capabilities.contains(capability)) {
                throw new PluginArtifactException(
                        "x-stage-scope references an undeclared capability at " + path);
            }
            if (!seen.add(capability)) {
                throw new PluginArtifactException(
                        "x-stage-scope contains a duplicate capability at " + path);
            }
        });
    }

    private boolean isScriptExtension(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.startsWith("x-")
                && (normalized.contains("script") || normalized.contains("javascript")
                || normalized.contains("groovy") || normalized.contains("spel")
                || normalized.contains("expression") || normalized.contains("eval"));
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
