package com.dataplatform.common.plugin.artifact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManifestV2ContractTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PluginManifestReader reader = new PluginManifestReader(mapper);

    @Test
    void preservesV1ProjectionConstructorUnknownFieldAndCanonicalBehavior() {
        ObjectNode source = validV1();
        PluginManifest parsed = reader.read(bytes(source));

        assertEquals(ConnectorAuthoringModel.ADVANCED_PIPELINE, parsed.authoringModel());
        assertNull(parsed.connectorKind());
        assertNull(parsed.transportMode());
        assertNull(parsed.outputMode());
        assertTrue(parsed.compatibility().isEmpty());

        PluginManifest constructed = new PluginManifest(parsed.manifestVersion(), parsed.pluginId(),
                parsed.version(), parsed.spiVersion(), parsed.displayName(), parsed.provider(),
                parsed.entryClass(), parsed.capabilities(), parsed.minHostVersion(),
                parsed.configSchema(), parsed.permissions());
        assertEquals(ConnectorAuthoringModel.ADVANCED_PIPELINE, constructed.authoringModel());
        assertTrue(constructed.compatibility().isEmpty());

        ObjectNode reordered = source.deepCopy();
        reordered.removeAll();
        source.fields().forEachRemaining(entry -> reordered.set(entry.getKey(), entry.getValue()));
        assertArrayEquals(reader.canonicalize(bytes(source)), reader.canonicalize(bytes(reordered)));

        source.put("authoringModel", "SIMPLE_CONNECTOR");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(source)));
    }

    @Test
    void parsesCompleteV2ProjectionAndExplicitWildcardCompatibility() {
        ObjectNode source = validV2();
        source.withObject("compatibility").putArray("dataTypeCodes").add("*");

        PluginManifest manifest = reader.read(bytes(source));

        assertEquals("2", manifest.manifestVersion());
        assertEquals(ConnectorAuthoringModel.SIMPLE_CONNECTOR, manifest.authoringModel());
        assertEquals(ConnectorKind.DEDICATED_VENDOR, manifest.connectorKind());
        assertEquals(ConnectorTransportMode.HOST_SINGLE_HTTP, manifest.transportMode());
        assertEquals(ConnectorOutputMode.HOST_MAPPING, manifest.outputMode());
        assertEquals(Set.of("ACME"), manifest.compatibility().vendorCodes());
        assertTrue(manifest.compatibility().supportsVendor("ACME"));
        assertTrue(manifest.compatibility().supportsDataType("ANY_TYPE"));
        assertFalse(manifest.compatibility().isEmpty());
    }

    @Test
    void acceptsManagedMultiHttpAndPluginNormalizedTopologies() {
        ObjectNode multi = validV2();
        multi.put("transportMode", "HOST_MANAGED_MULTI_HTTP");
        multi.withArray("capabilities").add(StageCapability.TRANSPORT.name());
        assertEquals(ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP,
                reader.read(bytes(multi)).transportMode());

        ObjectNode normalized = validV2();
        normalized.put("outputMode", "PLUGIN_NORMALIZED");
        normalized.withArray("capabilities").add(StageCapability.RESPONSE_NORMALIZER.name());
        assertEquals(ConnectorOutputMode.PLUGIN_NORMALIZED,
                reader.read(bytes(normalized)).outputMode());
    }

    @Test
    void rejectsMissingOrUnknownV2TopLevelFields() {
        for (String required : List.of("authoringModel", "connectorKind", "transportMode",
                "outputMode", "compatibility")) {
            ObjectNode invalid = validV2();
            invalid.remove(required);
            assertThrows(PluginArtifactException.class, () -> reader.read(bytes(invalid)), required);
        }
        ObjectNode unknown = validV2();
        unknown.put("unexpected", true);
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(unknown)));
    }

    @Test
    void rejectsInvalidV2CapabilityTopologyButKeepsAdvancedProjectionDistinct() {
        assertInvalidTopology(root -> root.putArray("capabilities")
                .add(StageCapability.RESPONSE_PARSER.name()));
        assertInvalidTopology(root -> root.withArray("capabilities")
                .add(StageCapability.TRANSPORT.name()));
        assertInvalidTopology(root -> root.put("transportMode", "HOST_MANAGED_MULTI_HTTP"));
        assertInvalidTopology(root -> root.put("outputMode", "PLUGIN_NORMALIZED"));
        assertInvalidTopology(root -> root.withArray("capabilities")
                .add(StageCapability.RESPONSE_NORMALIZER.name()));

        ObjectNode advanced = validV2();
        advanced.put("authoringModel", "ADVANCED_PIPELINE");
        advanced.putArray("capabilities").add(StageCapability.RESPONSE_PROCESSOR.name());
        advanced.withObject("configSchema").withObject("properties").remove("secretRef");
        assertEquals(ConnectorAuthoringModel.ADVANCED_PIPELINE,
                reader.read(bytes(advanced)).authoringModel());
    }

    @Test
    void rejectsEmptyBlankDuplicateAndMixedWildcardCompatibility() {
        ObjectNode empty = validV2();
        empty.putObject("compatibility").putArray("vendorCodes");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(empty)));

        ObjectNode blank = validV2();
        blank.withObject("compatibility").putArray("vendorCodes").add(" ");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(blank)));

        ObjectNode padded = validV2();
        padded.withObject("compatibility").putArray("vendorCodes").add(" ACME");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(padded)));

        ObjectNode vendorOnly = validV2();
        PluginManifest vendorOnlyManifest = reader.read(bytes(vendorOnly));
        assertTrue(vendorOnlyManifest.compatibility().supportsDataType("ANY_TYPE"));

        ObjectNode duplicate = validV2();
        duplicate.withObject("compatibility").putArray("vendorCodes").add("ACME").add("ACME");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(duplicate)));

        ObjectNode mixedWildcard = validV2();
        mixedWildcard.withObject("compatibility").putArray("vendorCodes").add("*").add("ACME");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(mixedWildcard)));

        ObjectNode unknown = validV2();
        unknown.withObject("compatibility").putArray("regions").add("CN");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(unknown)));
    }

    @Test
    void enforcesSecretScopeReferenceAndScriptSchemaPolicies() {
        ObjectNode missingScope = validV2();
        secretProperty(missingScope).remove("x-stage-scope");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(missingScope)));

        ObjectNode emptyScope = validV2();
        secretProperty(emptyScope).putArray("x-stage-scope");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(emptyScope)));

        ObjectNode undeclaredScope = validV2();
        secretProperty(undeclaredScope).putArray("x-stage-scope").add("REQUEST_PROCESSOR");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(undeclaredScope)));

        ObjectNode secretDefault = validV2();
        secretProperty(secretDefault).put("default", "plaintext");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(secretDefault)));

        ObjectNode remote = validV2();
        remote.withObject("configSchema").put("$ref", "https://evil.example/schema");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(remote)));

        ObjectNode dynamic = validV2();
        dynamic.withObject("configSchema").put("$dynamicRef", "#/$defs/value");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(dynamic)));

        ObjectNode recursive = validV2();
        recursive.withObject("configSchema").put("$ref", "#");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(recursive)));

        ObjectNode script = validV2();
        script.withObject("configSchema").put("x-validation-script", "return true");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(script)));

        reader.read(bytes(validV2()));
    }

    @Test
    void signatureCoversEveryV2ProductFieldAndArtifactHash() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ObjectNode original = validV2();
        Path originalJar = jar(bytes(original));
        String originalSha = sha256(originalJar);
        String originalSignature = sign(keyPair, bytes(original), originalSha);
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(mapper,
                key -> Optional.of(new TrustedSigningKey(key, keyPair.getPublic(), "Ed25519")));

        VerifiedPluginArtifact verified = verifier.verify(coordinates(
                originalJar, originalSha, originalSignature));
        assertEquals("2", verified.manifest().manifestVersion());

        List<Consumer<ObjectNode>> mutations = List.of(
                root -> root.put("authoringModel", "ADVANCED_PIPELINE"),
                root -> root.put("connectorKind", "GENERIC_HTTP"),
                root -> root.put("transportMode", "HOST_MANAGED_MULTI_HTTP"),
                root -> root.put("outputMode", "PLUGIN_NORMALIZED"),
                root -> root.withObject("compatibility").putArray("vendorCodes").add("OTHER"));
        for (Consumer<ObjectNode> mutation : mutations) {
            ObjectNode tampered = original.deepCopy();
            mutation.accept(tampered);
            Path tamperedJar = jar(bytes(tampered));
            String tamperedSha = sha256(tamperedJar);
            assertThrows(PluginArtifactException.class,
                    () -> verifier.verify(coordinates(tamperedJar, tamperedSha, originalSignature)));
        }
    }

    private void assertInvalidTopology(Consumer<ObjectNode> mutation) {
        ObjectNode root = validV2();
        mutation.accept(root);
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(root)));
    }

    private ObjectNode validV1() {
        ObjectNode root = commonManifest("1");
        root.putArray("capabilities").add(StageCapability.RESPONSE_PARSER.name());
        return root;
    }

    private ObjectNode validV2() {
        ObjectNode root = commonManifest("2");
        root.put("authoringModel", "SIMPLE_CONNECTOR");
        root.put("connectorKind", "DEDICATED_VENDOR");
        root.put("transportMode", "HOST_SINGLE_HTTP");
        root.put("outputMode", "HOST_MAPPING");
        root.putArray("capabilities")
                .add(StageCapability.REQUEST_BUILDER.name())
                .add(StageCapability.RESPONSE_PARSER.name());
        root.putObject("compatibility").putArray("vendorCodes").add("ACME");
        ObjectNode properties = root.withObject("configSchema").putObject("properties");
        properties.putObject("secretRef")
                .put("type", "string")
                .put("x-secret-ref", true)
                .putArray("x-stage-scope").add(StageCapability.REQUEST_BUILDER.name());
        return root;
    }

    private ObjectNode commonManifest(String manifestVersion) {
        ObjectNode root = mapper.createObjectNode();
        root.put("manifestVersion", manifestVersion);
        root.put("pluginId", "fixture-plugin");
        root.put("version", "2.0.0");
        root.put("spiVersion", "1.1");
        root.put("displayName", "Fixture");
        root.put("provider", "test");
        root.put("entryClass", "example.plugin.FixtureConnectorPlugin");
        root.put("minHostVersion", "2.0.0");
        root.putObject("configSchema").put("type", "object");
        ObjectNode permissions = root.putObject("permissions");
        permissions.putArray("networkProtocols").add("https");
        permissions.putArray("networkHosts").add("api.example.com");
        return root;
    }

    private ObjectNode secretProperty(ObjectNode manifest) {
        return manifest.withObject("configSchema").withObject("properties").withObject("secretRef");
    }

    private byte[] bytes(ObjectNode node) {
        return node.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Path jar(byte[] manifest) throws Exception {
        Path path = Files.createTempFile(tempDir, "manifest-v2-", ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(PluginManifestReader.MANIFEST_PATH));
            output.write(manifest);
            output.closeEntry();
        }
        return path;
    }

    private PluginArtifactCoordinates coordinates(Path jar, String sha, String signature) {
        return new PluginArtifactCoordinates(
                "fixture-plugin", "2.0.0", jar, sha, signature, "test-key");
    }

    private String sign(KeyPair pair, byte[] manifest, String sha) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(PluginArtifactVerifier.signaturePayload(reader.canonicalize(manifest), sha));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
