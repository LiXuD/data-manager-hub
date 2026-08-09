package com.dataplatform.common.plugin.artifact;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.common.plugin.runtime.PluginLoader;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManifestPolicyMatrixTest {

    @TempDir
    Path tempDir;
    private final ObjectMapper mapper = new ObjectMapper();
    private final PluginManifestReader reader = new PluginManifestReader(mapper);

    @Test
    void rejectsMissingFieldsUnknownCapabilityAndOversizedSchema() throws Exception {
        ObjectNode missing = validManifest();
        missing.remove("entryClass");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(missing)));

        ObjectNode unknownCapability = validManifest();
        unknownCapability.putArray("capabilities").add("REMOTE_SCRIPT");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(unknownCapability)));

        ObjectNode oversized = validManifest();
        oversized.withObject("configSchema").put("description", "x".repeat(
                PluginManifestReader.MAX_SCHEMA_BYTES));
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(oversized)));
    }

    @Test
    void rejectsRemoteDynamicReferencesAndSecretDefaults() {
        ObjectNode remote = validManifest();
        remote.withObject("configSchema").put("$dynamicRef", "https://evil.example/schema");
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(remote)));

        ObjectNode secretDefault = validManifest();
        ObjectNode secret = secretDefault.withObject("configSchema")
                .put("type", "string")
                .put("x-secret-ref", true)
                .put("default", "plaintext-secret");
        assertTrue(secret.path("x-secret-ref").asBoolean());
        assertThrows(PluginArtifactException.class, () -> reader.read(bytes(secretDefault)));
    }

    @Test
    void allowsNonRecursiveLocalPointerButRejectsDirectIndirectAndArrayRecursion() {
        ObjectNode nonRecursive = validManifest();
        try {
            nonRecursive.set("configSchema", mapper.readTree("""
                    {"type":"object","$defs":{"endpoint":{"type":"string"}},
                     "properties":{"endpoint":{"$ref":"#/$defs/endpoint"}}}
                    """));
            reader.read(bytes(nonRecursive));

            for (String recursiveSchema : java.util.List.of(
                    "{\"$ref\":\"#\"}",
                    "{\"$defs\":{\"a\":{\"$ref\":\"#/$defs/b\"},\"b\":{\"$ref\":\"#/$defs/a\"}},\"$ref\":\"#/$defs/a\"}",
                    "{\"type\":\"array\",\"items\":{\"$ref\":\"#\"}}")) {
                ObjectNode recursive = validManifest();
                recursive.set("configSchema", mapper.readTree(recursiveSchema));
                assertThrows(PluginArtifactException.class, () -> reader.read(bytes(recursive)));
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void rejectsIncompatibleSpiAndHostBeforeLoadingPluginCode() throws Exception {
        Path placeholder = tempDir.resolve("not-opened.jar");
        PluginManifest base = reader.read(bytes(validManifest()));
        PluginManifest wrongSpi = new PluginManifest(base.manifestVersion(), base.pluginId(), base.version(),
                "2.0", base.displayName(), base.provider(), base.entryClass(), base.capabilities(),
                base.minHostVersion(), base.configSchema(), base.permissions());
        PluginManifest newerHost = new PluginManifest(base.manifestVersion(), base.pluginId(), base.version(),
                base.spiVersion(), base.displayName(), base.provider(), base.entryClass(), base.capabilities(),
                "9.0.0", base.configSchema(), base.permissions());
        PluginLoader loader = new PluginLoader(TestPluginContexts.context(), "2.1.0", "1.0");

        assertThrows(PluginArtifactException.class,
                () -> loader.load(new VerifiedPluginArtifact(placeholder, wrongSpi, "0".repeat(64))));
        assertThrows(PluginArtifactException.class,
                () -> loader.load(new VerifiedPluginArtifact(placeholder, newerHost, "0".repeat(64))));
    }

    @Test
    void rejectsManifestCoordinateMismatchForbiddenSpiClassAndOversizedArtifact() throws Exception {
        Path jar = jar(bytes(validManifest()), null);
        String hash = sha256(jar);
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(mapper, ignored -> Optional.empty());
        PluginArtifactCoordinates wrongCoordinates = new PluginArtifactCoordinates(
                "other-plugin", "1.2.0", jar, hash, "not-used", "not-used");
        assertThrows(PluginArtifactException.class, () -> verifier.verify(wrongCoordinates));

        Path forbidden = jar(bytes(validManifest()), "com/dataplatform/plugin/spi/Shadow.class");
        String forbiddenHash = sha256(forbidden);
        assertThrows(PluginArtifactException.class, () -> verifier.verify(new PluginArtifactCoordinates(
                "fixture-plugin", "1.2.0", forbidden, forbiddenHash, "not-used", "not-used")));

        Path oversized = tempDir.resolve("oversized.jar");
        try (SeekableByteChannel channel = Files.newByteChannel(oversized,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(PluginArtifactVerifier.MAX_ARTIFACT_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{1}));
        }
        assertThrows(PluginArtifactException.class, () -> verifier.verify(new PluginArtifactCoordinates(
                "fixture-plugin", "1.2.0", oversized, "0".repeat(64), "not-used", "not-used")));
    }

    @Test
    void rejectsUnsignedUnknownKeyMalformedAndAbnormallyExpandedArtifacts() throws Exception {
        Path valid = jar(bytes(validManifest()), null);
        String validHash = sha256(valid);
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(mapper, ignored -> Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new PluginArtifactCoordinates(
                "fixture-plugin", "1.2.0", valid, validHash, "", "release-key"));
        assertThrows(PluginArtifactException.class, () -> verifier.verify(new PluginArtifactCoordinates(
                "fixture-plugin", "1.2.0", valid, validHash, "AA==", "unknown-key")));

        Path malformed = tempDir.resolve("malformed.jar");
        Files.writeString(malformed, "not-a-jar", StandardCharsets.UTF_8);
        assertThrows(PluginArtifactException.class, () -> verifier.verify(new PluginArtifactCoordinates(
                "fixture-plugin", "1.2.0", malformed, sha256(malformed), "AA==", "unknown-key")));

        Path expanded = jarWithExpandedEntry(bytes(validManifest()));
        assertThrows(PluginArtifactException.class, () -> verifier.verify(new PluginArtifactCoordinates(
                "fixture-plugin", "1.2.0", expanded, sha256(expanded), "AA==", "unknown-key")));
    }

    private ObjectNode validManifest() {
        ObjectNode root = mapper.createObjectNode();
        root.put("manifestVersion", "1");
        root.put("pluginId", "fixture-plugin");
        root.put("version", "1.2.0");
        root.put("spiVersion", "1.0");
        root.put("displayName", "Fixture");
        root.put("provider", "test");
        root.put("entryClass", "example.plugin.FixtureConnectorPlugin");
        root.putArray("capabilities").add(StageCapability.RESPONSE_PARSER.name());
        root.put("minHostVersion", "2.0.0");
        root.putObject("configSchema").put("type", "object");
        ObjectNode permissions = root.putObject("permissions");
        permissions.putArray("networkProtocols").add("https");
        permissions.putArray("networkHosts").add("api.example.com");
        return root;
    }

    private byte[] bytes(ObjectNode node) {
        return node.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Path jar(byte[] manifest, String extraEntry) throws Exception {
        Path jar = Files.createTempFile(tempDir, "policy-", ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(PluginManifestReader.MANIFEST_PATH));
            output.write(manifest);
            output.closeEntry();
            if (extraEntry != null) {
                output.putNextEntry(new JarEntry(extraEntry));
                output.write(new byte[]{0, 1, 2});
                output.closeEntry();
            }
        }
        return jar;
    }

    private Path jarWithExpandedEntry(byte[] manifest) throws Exception {
        Path jar = Files.createTempFile(tempDir, "expanded-", ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(PluginManifestReader.MANIFEST_PATH));
            output.write(manifest);
            output.closeEntry();
            output.putNextEntry(new JarEntry("payload.bin"));
            byte[] block = new byte[8192];
            long remaining = 16L * 1024L * 1024L + 1L;
            while (remaining > 0) {
                int length = (int) Math.min(block.length, remaining);
                output.write(block, 0, length);
                remaining -= length;
            }
            output.closeEntry();
        }
        return jar;
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
