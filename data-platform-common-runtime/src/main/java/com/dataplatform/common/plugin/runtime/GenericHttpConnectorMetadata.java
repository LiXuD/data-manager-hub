package com.dataplatform.common.plugin.runtime;

import com.dataplatform.common.plugin.artifact.PluginCompatibility;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** Single deterministic code fact for the host-owned generic-http connector. */
public final class GenericHttpConnectorMetadata {

    public static final String PLUGIN_ID = "generic-http";
    public static final String VERSION = "2.0.0";
    public static final String SPI_VERSION = "1.1";
    public static final String ARTIFACT_URI = "builtin://generic-http/2.0.0";
    public static final String BUILTIN_SIGNATURE = "builtin";
    public static final String BUILTIN_SIGNING_KEY = "builtin";
    public static final String ENTRY_CLASS = GenericHttpConnectorPlugin.class.getName();
    public static final String DISPLAY_NAME = "Generic HTTP";
    public static final String PROVIDER = "data-platform";
    public static final String DESCRIPTION = "Built-in standard single-request HTTPS connector";
    public static final String MIN_HOST_VERSION = "1.0.0";
    public static final List<String> CAPABILITY_NAMES = List.of(
            StageCapability.REQUEST_BUILDER.name(),
            StageCapability.REQUEST_PROCESSOR.name(),
            StageCapability.RESPONSE_PARSER.name());
    public static final Set<StageCapability> CAPABILITIES = Set.of(
            StageCapability.REQUEST_BUILDER,
            StageCapability.REQUEST_PROCESSOR,
            StageCapability.RESPONSE_PARSER);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode SCHEMA = parse(schemaJson());
    private static final JsonNode PERMISSIONS = parse("""
            {"networkProtocols":[],"networkHosts":[]}
            """);
    private static final String CANONICAL_SCHEMA = canonical(SCHEMA);
    private static final String CANONICAL_PERMISSIONS = canonical(PERMISSIONS);
    private static final String CANONICAL_COMPATIBILITY = canonical(parse("""
            {"vendorCodes":["*"],"dataTypeCodes":["*"]}
            """));
    private static final String CANONICAL_MANIFEST = canonicalManifest();
    private static final String MANIFEST_SHA256 = hash(parse(CANONICAL_MANIFEST));
    private static final String SCHEMA_SHA256 = hash(SCHEMA);
    private static final String ARTIFACT_SHA256 = artifactHash();
    private static final PluginCompatibility COMPATIBILITY =
            new PluginCompatibility(Set.of("*"), Set.of("*"));
    private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
            PLUGIN_ID, VERSION, SPI_VERSION, DISPLAY_NAME, PROVIDER, CAPABILITIES);

    private GenericHttpConnectorMetadata() {
    }

    public static PluginDescriptor descriptor() { return DESCRIPTOR; }
    public static JsonNode configSchema() { return SCHEMA.deepCopy(); }
    public static String canonicalSchemaJson() { return CANONICAL_SCHEMA; }
    public static String canonicalPermissionsJson() { return CANONICAL_PERMISSIONS; }
    public static String canonicalCompatibilityJson() { return CANONICAL_COMPATIBILITY; }
    public static String canonicalManifestJson() { return CANONICAL_MANIFEST; }
    public static String artifactSha256() { return ARTIFACT_SHA256; }
    public static String manifestSha256() { return MANIFEST_SHA256; }
    public static String schemaSha256() { return SCHEMA_SHA256; }
    public static PluginCompatibility compatibility() { return COMPATIBILITY; }

    public static ConnectorPluginMetadata metadata() {
        return new ConnectorPluginMetadata(PLUGIN_ID, VERSION, ARTIFACT_SHA256,
                MANIFEST_SHA256, SCHEMA_SHA256, SCHEMA, "2",
                ConnectorAuthoringModel.SIMPLE_CONNECTOR, ConnectorKind.GENERIC_HTTP,
                ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING,
                COMPATIBILITY);
    }

    private static String canonicalManifest() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("manifestVersion", "2");
        root.put("pluginId", PLUGIN_ID);
        root.put("version", VERSION);
        root.put("spiVersion", SPI_VERSION);
        root.put("displayName", DISPLAY_NAME);
        root.put("provider", PROVIDER);
        root.put("description", DESCRIPTION);
        root.put("entryClass", ENTRY_CLASS);
        root.put("authoringModel", ConnectorAuthoringModel.SIMPLE_CONNECTOR.name());
        root.put("connectorKind", ConnectorKind.GENERIC_HTTP.name());
        root.put("transportMode", ConnectorTransportMode.HOST_SINGLE_HTTP.name());
        root.put("outputMode", ConnectorOutputMode.HOST_MAPPING.name());
        var capabilities = root.putArray("capabilities");
        CAPABILITY_NAMES.forEach(capabilities::add);
        ObjectNode compatibility = root.putObject("compatibility");
        compatibility.putArray("vendorCodes").add("*");
        compatibility.putArray("dataTypeCodes").add("*");
        root.put("minHostVersion", MIN_HOST_VERSION);
        root.set("configSchema", SCHEMA);
        root.set("permissions", PERMISSIONS);
        return new String(new PluginManifestReader(MAPPER).canonicalize(bytes(root)), StandardCharsets.UTF_8);
    }

    private static String artifactHash() {
        ObjectNode fact = MAPPER.createObjectNode();
        fact.put("artifactUri", ARTIFACT_URI);
        fact.put("entryClass", ENTRY_CLASS);
        fact.put("manifestSha256", MANIFEST_SHA256);
        fact.put("pluginId", PLUGIN_ID);
        fact.put("schemaSha256", SCHEMA_SHA256);
        fact.put("spiVersion", SPI_VERSION);
        fact.put("version", VERSION);
        return hash(fact);
    }

    private static String canonical(JsonNode value) {
        return new String(new PluginManifestReader(MAPPER).canonicalize(bytes(value)), StandardCharsets.UTF_8);
    }

    private static String hash(JsonNode value) {
        return ConnectorSnapshotIntegrity.sha256(MAPPER, value);
    }

    private static JsonNode parse(String value) {
        try { return MAPPER.readTree(value); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static byte[] bytes(JsonNode value) {
        try { return MAPPER.writeValueAsBytes(value); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static String schemaJson() {
        return """
                {
                  "$schema":"https://json-schema.org/draft/2020-12/schema",
                  "type":"object",
                  "required":["endpoint","method","auth"],
                  "additionalProperties":false,
                  "x-platform-managed":["transport","timeouts","retry","responseMapping"],
                  "properties":{
                    "endpoint":{"type":"string","minLength":1,"maxLength":2048,"x-ui-group":"request"},
                    "method":{"type":"string","enum":["GET","POST","PUT","PATCH","DELETE","HEAD"],"x-ui-group":"request"},
                    "contentType":{"type":"string","enum":["application/json","application/json; charset=utf-8","application/x-www-form-urlencoded"],"x-ui-group":"request"},
                    "headers":{"type":"array","maxItems":64,"x-ui-group":"request","x-ui-advanced":true,"items":{"type":"object","required":["name","value"],"additionalProperties":false,"properties":{"name":{"type":"string","minLength":1,"maxLength":128},"value":{"type":"string","minLength":1,"maxLength":4096}}}},
                    "requestMapping":{"type":"array","maxItems":256,"x-ui-group":"request","items":{"type":"object","required":["sourceField","targetField"],"additionalProperties":false,"properties":{"sourceField":{"type":"string","minLength":1,"maxLength":256},"targetField":{"type":"string","minLength":1,"maxLength":256},"required":{"type":"boolean"},"defaultValue":{},"transformType":{"type":"string","enum":["none","trim","uppercase","lowercase"]}}}},
                    "auth":{"type":"object","required":["type"],"additionalProperties":false,"x-ui-group":"authentication","properties":{"type":{"type":"string","enum":["NONE","BEARER","BASIC","API_KEY"]},"tokenRef":{"type":"string","minLength":1,"maxLength":256,"x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BEARER"}},"usernameRef":{"type":"string","minLength":1,"maxLength":256,"x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BASIC"}},"passwordRef":{"type":"string","minLength":1,"maxLength":256,"x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"BASIC"}},"keyName":{"type":"string","minLength":1,"maxLength":128,"x-ui-visible-if":{"type":"API_KEY"}},"keyRef":{"type":"string","minLength":1,"maxLength":256,"x-secret-ref":true,"x-stage-scope":["REQUEST_PROCESSOR"],"x-ui-visible-if":{"type":"API_KEY"}},"location":{"type":"string","enum":["header","query"],"x-ui-visible-if":{"type":"API_KEY"}}},"allOf":[{"if":{"properties":{"type":{"enum":["NONE"]}}},"then":{"required":["type"],"not":{"anyOf":[{"required":["tokenRef"]},{"required":["usernameRef"]},{"required":["passwordRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]}}},{"if":{"properties":{"type":{"enum":["BEARER"]}}},"then":{"required":["type","tokenRef"],"not":{"anyOf":[{"required":["usernameRef"]},{"required":["passwordRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]}}},{"if":{"properties":{"type":{"enum":["BASIC"]}}},"then":{"required":["type","usernameRef","passwordRef"],"not":{"anyOf":[{"required":["tokenRef"]},{"required":["keyName"]},{"required":["keyRef"]},{"required":["location"]}]}}},{"if":{"properties":{"type":{"enum":["API_KEY"]}}},"then":{"required":["type","keyName","keyRef","location"],"not":{"anyOf":[{"required":["tokenRef"]},{"required":["usernameRef"]},{"required":["passwordRef"]}]}}}]},
                    "successHttpStatuses":{"type":"array","minItems":1,"maxItems":100,"uniqueItems":true,"x-ui-group":"response","items":{"type":"integer","minimum":100,"maximum":599}},
                    "businessCodePath":{"type":"string","minLength":1,"maxLength":256,"x-ui-group":"response","x-ui-advanced":true},
                    "successBusinessCodes":{"type":"array","minItems":1,"maxItems":128,"uniqueItems":true,"x-ui-group":"response","x-ui-advanced":true,"items":{"type":"string","minLength":1,"maxLength":128}},
                    "dataPath":{"type":"string","minLength":1,"maxLength":256,"x-ui-group":"response","x-ui-advanced":true}
                  }
                }
                """;
    }
}
