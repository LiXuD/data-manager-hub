package com.dataplatform.masterdata.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.config.ConnectorPluginProperties;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;

class PluginArtifactVerifierTest {

    @Test
    void verifiesHashManifestAndEd25519Signature() throws Exception {
        String manifest = """
                {"manifestVersion":"1","pluginId":"demo-http","version":"1.2.0","spiVersion":"1.0",
                 "displayName":"Demo","provider":"internal","entryClass":"example.DemoPlugin",
                 "capabilities":["TRANSPORT"],"minHostVersion":"1.0.0",
                 "configSchema":{"type":"object","properties":{"endpoint":{"type":"string"}}},
                 "permissions":{"networkProtocols":["https"],"networkHosts":["api.example.com"]}}
                """;
        byte[] artifact = jar(manifest, "example/DemoPlugin.class");
        String sha256 = sha256(artifact);
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String canonical = canonical(manifest);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update((canonical + "\n" + sha256).getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        ConnectorPluginProperties properties = properties();
        properties.getTrustedSigningKeys().put("release-2026", Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded()));
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(properties, new ObjectMapper());
        PluginImportRequestDTO request = new PluginImportRequestDTO(
                "https://repo.example/plugins/demo.jar", sha256, signature, "release-2026");

        VerifiedPluginArtifact verified = verifier.verifyDownloaded(
                request, verifier.validateArtifactUri(request.artifactUri()), artifact);

        assertEquals("demo-http", verified.pluginId());
        assertEquals("1.2.0", verified.version());
        assertEquals(List.of("TRANSPORT"), verified.capabilities());
    }

    @Test
    void rejectsNonHttpsOrNonWhitelistedArtifactUri() {
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(properties(), new ObjectMapper());
        assertThrows(IllegalArgumentException.class,
                () -> verifier.validateArtifactUri("http://repo.example/plugins/demo.jar"));
        assertThrows(IllegalArgumentException.class,
                () -> verifier.validateArtifactUri("https://evil.example/plugins/demo.jar"));
        assertThrows(IllegalArgumentException.class,
                () -> verifier.validateArtifactUri("https://repo.example/private/demo.jar"));
        assertThrows(IllegalArgumentException.class,
                () -> verifier.validateArtifactUri("https://repo.example/plugins.evil/demo.jar"));
    }

    @Test
    void controlPlaneAndRuntimeUseIdenticalCanonicalManifestBytes() throws Exception {
        String manifest = """
                {"version":"1.2.0","pluginId":"demo-http","manifestVersion":"1",
                 "spiVersion":"1.0","displayName":"Demo","provider":"internal",
                 "entryClass":"example.DemoPlugin","capabilities":["TRANSPORT"],
                 "minHostVersion":"1.0.0","configSchema":{"type":"object"},
                 "permissions":{"networkHosts":["api.example.com"],"networkProtocols":["https"]}}
                """;

        byte[] runtimeCanonical = new PluginManifestReader(new ObjectMapper())
                .canonicalize(manifest.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(canonical(manifest).getBytes(StandardCharsets.UTF_8), runtimeCanonical);
    }

    @Test
    void rejectsRemoteSchemaReference() throws Exception {
        String manifest = """
                {"manifestVersion":"1","pluginId":"demo-http","version":"1.2.0","spiVersion":"1.0",
                 "displayName":"Demo","provider":"internal","entryClass":"example.DemoPlugin",
                 "capabilities":["TRANSPORT"],
                 "configSchema":{"$ref":"https://evil.example/schema.json"},
                 "permissions":{"networkProtocols":["https"],"networkHosts":["api.example.com"]}}
                """;
        byte[] artifact = jar(manifest, "example/DemoPlugin.class");
        String sha256 = sha256(artifact);
        ConnectorPluginProperties properties = properties();
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        properties.getTrustedSigningKeys().put("key", Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded()));
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(properties, new ObjectMapper());
        PluginImportRequestDTO request = new PluginImportRequestDTO(
                "https://repo.example/plugins/demo.jar", sha256, "ignored", "key");
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyDownloaded(request, verifier.validateArtifactUri(request.artifactUri()), artifact));
    }

    @Test
    void rejectsWrongHashAsExpectedArtifactValidation() throws Exception {
        byte[] artifact = signedArtifact().artifact();
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(properties(), new ObjectMapper());
        PluginImportRequestDTO request = new PluginImportRequestDTO(
                "https://repo.example/plugins/demo.jar", "0".repeat(64), "unused", "unused");

        PluginArtifactValidationException exception = assertThrows(PluginArtifactValidationException.class,
                () -> verifier.verifyDownloaded(
                        request, verifier.validateArtifactUri(request.artifactUri()), artifact));

        assertEquals("插件制品SHA-256与期望值不一致", exception.getMessage());
    }

    @Test
    void rejectsInvalidSignatureAsExpectedArtifactValidation() throws Exception {
        SignedArtifact signed = signedArtifact();
        ConnectorPluginProperties properties = properties();
        properties.getTrustedSigningKeys().put("release-2026", Base64.getEncoder()
                .encodeToString(signed.keyPair().getPublic().getEncoded()));
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(properties, new ObjectMapper());
        PluginImportRequestDTO request = new PluginImportRequestDTO(
                "https://repo.example/plugins/demo.jar", sha256(signed.artifact()),
                Base64.getEncoder().encodeToString(new byte[64]), "release-2026");

        PluginArtifactValidationException exception = assertThrows(PluginArtifactValidationException.class,
                () -> verifier.verifyDownloaded(
                        request, verifier.validateArtifactUri(request.artifactUri()), signed.artifact()));

        assertEquals("插件Ed25519签名验证失败", exception.getMessage());
    }

    private ConnectorPluginProperties properties() {
        ConnectorPluginProperties properties = new ConnectorPluginProperties();
        properties.setArtifactAllowedHosts(List.of("repo.example"));
        properties.setArtifactAllowedPathPrefixes(List.of("/plugins/"));
        return properties;
    }

    private static SignedArtifact signedArtifact() throws Exception {
        String manifest = """
                {"manifestVersion":"1","pluginId":"demo-http","version":"1.2.0","spiVersion":"1.0",
                 "displayName":"Demo","provider":"internal","entryClass":"example.DemoPlugin",
                 "capabilities":["TRANSPORT"],"minHostVersion":"1.0.0","configSchema":{"type":"object"},
                 "permissions":{"networkProtocols":["https"],"networkHosts":["api.example.com"]}}
                """;
        return new SignedArtifact(jar(manifest, "example/DemoPlugin.class"),
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
    }

    private static byte[] jar(String manifest, String classEntry) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry(PluginArtifactVerifier.MANIFEST_PATH));
            jar.write(manifest.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry(classEntry));
            try (var classBytes = PluginArtifactVerifierTest.class.getResourceAsStream(
                    "PluginArtifactVerifierTest$LegalFixture.class")) {
                if (classBytes == null) throw new IllegalStateException("Legal fixture class is unavailable");
                jar.write(classBytes.readAllBytes());
            }
            jar.closeEntry();
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String canonical(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper.writeValueAsString(sort(mapper.readTree(json)));
    }

    private static JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = ((ObjectNode) value).objectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.sort(String::compareTo);
            fields.forEach(field -> result.set(field, sort(value.get(field))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = ((ArrayNode) value).arrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value;
    }

    private record SignedArtifact(byte[] artifact, KeyPair keyPair) {
    }

    private static final class LegalFixture {
    }
}
