package com.dataplatform.common.plugin.runtime;

import com.dataplatform.common.plugin.artifact.PluginCompatibility;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Deterministic host-owned metadata for the non-catalogued platform-core plugin. */
public final class PlatformCoreConnectorMetadata {

    public static final String PLUGIN_ID = "platform-core";
    public static final String VERSION = "1.0.0";
    public static final String SPI_VERSION = "1.1";
    public static final String ENTRY_CLASS = PlatformCoreConnectorPlugin.class.getName();
    public static final Set<StageCapability> CAPABILITIES = Set.of(
            StageCapability.REQUEST_PROCESSOR,
            StageCapability.TRANSPORT,
            StageCapability.RESPONSE_PROCESSOR,
            StageCapability.RESPONSE_NORMALIZER);

    private static final String STATIC_FACTS_JSON = """
            {
              "displayName":"Platform Connector Core",
              "provider":"data-platform",
              "minHostVersion":"1.0.0",
              "permissions":{"networkProtocols":["https"],"networkHosts":[]},
              "configSchema":{
                "$schema":"https://json-schema.org/draft/2020-12/schema",
                "type":"object",
                "properties":{
                  "direction":{
                    "type":"string",
                    "enum":["REQUEST","RESPONSE"]
                  },
                  "securitySteps":{
                    "type":"array",
                    "items":{"type":"object"}
                  },
                  "secretRefs":{
                    "type":"array",
                    "items":{
                      "type":"string",
                      "minLength":1,
                      "x-secret-ref":true
                    }
                  },
                  "responseMapping":{
                    "type":"array",
                    "items":{
                    "type":"object",
                    "required":["targetField","sourcePath"],
                    "properties":{
                      "targetField":{"type":"string","minLength":1},
                      "sourcePath":{"type":"string","minLength":1},
                      "sourceType":{"type":"string","enum":["field","jsonPath"]},
                      "defaultValue":{},
                      "transformType":{"type":"string","enum":["none","toString","toNumber"]}
                    },
                    "additionalProperties":false
                  }
                  }
                },
                "additionalProperties":false
              }
            }
            """;

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();
    private static final JsonNode FACTS = parse(STATIC_FACTS_JSON);
    private static final JsonNode CONFIG_SCHEMA = FACTS.path("configSchema").deepCopy();
    private static final String CANONICAL_MANIFEST_JSON = canonicalManifest();
    private static final String MANIFEST_SHA256 = ConnectorSnapshotIntegrity.sha256(
            CANONICAL_MAPPER, parse(CANONICAL_MANIFEST_JSON));
    private static final String SCHEMA_SHA256 = ConnectorSnapshotIntegrity.sha256(
            CANONICAL_MAPPER, CONFIG_SCHEMA);
    private static final String ARTIFACT_SHA256 = calculateArtifactSha256();
    private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
            PLUGIN_ID, VERSION, SPI_VERSION, FACTS.path("displayName").asText(),
            FACTS.path("provider").asText(), CAPABILITIES);

    private PlatformCoreConnectorMetadata() {
    }

    public static PluginDescriptor descriptor() {
        return DESCRIPTOR;
    }

    public static JsonNode configSchema() {
        return CONFIG_SCHEMA.deepCopy();
    }

    public static String canonicalManifestJson() {
        return CANONICAL_MANIFEST_JSON;
    }

    public static String artifactSha256() {
        return ARTIFACT_SHA256;
    }

    public static String manifestSha256() {
        return MANIFEST_SHA256;
    }

    public static String schemaSha256() {
        return SCHEMA_SHA256;
    }

    public static ConnectorPluginMetadata metadata() {
        return new ConnectorPluginMetadata(
                PLUGIN_ID, VERSION, ARTIFACT_SHA256, MANIFEST_SHA256, SCHEMA_SHA256,
                CONFIG_SCHEMA, "1", ConnectorAuthoringModel.ADVANCED_PIPELINE,
                null, null, null, PluginCompatibility.empty());
    }

    private static String canonicalManifest() {
        ObjectNode manifest = CANONICAL_MAPPER.createObjectNode();
        manifest.put("manifestVersion", "1");
        manifest.put("pluginId", PLUGIN_ID);
        manifest.put("version", VERSION);
        manifest.put("spiVersion", SPI_VERSION);
        manifest.put("displayName", FACTS.path("displayName").asText());
        manifest.put("provider", FACTS.path("provider").asText());
        manifest.put("entryClass", ENTRY_CLASS);
        var capabilities = manifest.putArray("capabilities");
        capabilities.add(StageCapability.REQUEST_PROCESSOR.name());
        capabilities.add(StageCapability.TRANSPORT.name());
        capabilities.add(StageCapability.RESPONSE_PROCESSOR.name());
        capabilities.add(StageCapability.RESPONSE_NORMALIZER.name());
        manifest.put("minHostVersion", FACTS.path("minHostVersion").asText());
        manifest.set("configSchema", CONFIG_SCHEMA);
        manifest.set("permissions", FACTS.path("permissions"));
        byte[] canonical = new PluginManifestReader(CANONICAL_MAPPER)
                .canonicalize(bytes(manifest));
        return new String(canonical, StandardCharsets.UTF_8);
    }

    private static String calculateArtifactSha256() {
        ObjectNode artifact = CANONICAL_MAPPER.createObjectNode();
        artifact.put("pluginId", PLUGIN_ID);
        artifact.put("version", VERSION);
        artifact.put("spiVersion", SPI_VERSION);
        artifact.put("entryClass", ENTRY_CLASS);
        artifact.put("manifestSha256", MANIFEST_SHA256);
        artifact.put("schemaSha256", SCHEMA_SHA256);
        return ConnectorSnapshotIntegrity.sha256(CANONICAL_MAPPER, artifact);
    }

    private static JsonNode parse(String json) {
        try {
            return CANONICAL_MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static byte[] bytes(JsonNode value) {
        try {
            return CANONICAL_MAPPER.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
