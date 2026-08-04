package com.dataplatform.common.plugin.artifact;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.common.plugin.runtime.PluginHandle;
import com.dataplatform.common.plugin.runtime.PluginLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import example.plugin.FixtureConnectorPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class PluginArtifactVerifierTest {
    @TempDir Path tempDir;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void verifiesSignedArtifactAndLoadsItWithVersionClassLoader() throws Exception {
        KeyPair pair = keyPair();
        byte[] manifest = manifest("{\"type\":\"object\"}");
        Path jar = createJar(manifest, false);
        String sha = sha256(jar);
        PluginArtifactVerifier verifier = verifier(pair);
        VerifiedPluginArtifact verified = verifier.verify(coordinates(jar, sha, sign(pair, manifest, sha)));

        assertEquals("fixture-plugin", verified.manifest().pluginId());
        PluginHandle handle = new PluginLoader(TestPluginContexts.context(), "2.1.0", "1.0")
                .load(verified);
        assertEquals("fixture-plugin", handle.key().pluginId());
        assertNotSame(FixtureConnectorPlugin.class.getClassLoader(), handle.classLoader());
        handle.retire();
        assertEquals(com.dataplatform.common.plugin.runtime.PluginHandleState.CLOSED, handle.state());
    }

    @Test
    void rejectsHashSignatureForbiddenPackagesAndRemoteSchemaReferences() throws Exception {
        KeyPair pair = keyPair();
        byte[] manifest = manifest("{\"type\":\"object\"}");
        Path jar = createJar(manifest, false);
        String sha = sha256(jar);
        PluginArtifactVerifier verifier = verifier(pair);

        assertThrows(PluginArtifactException.class,
                () -> verifier.verify(coordinates(jar, "0".repeat(64), sign(pair, manifest, sha))));
        assertThrows(PluginArtifactException.class,
                () -> verifier.verify(coordinates(jar, sha, Base64.getEncoder().encodeToString(new byte[64]))));

        Path forbidden = createJar(manifest, true);
        String forbiddenSha = sha256(forbidden);
        assertThrows(PluginArtifactException.class, () -> verifier.verify(
                coordinates(forbidden, forbiddenSha, sign(pair, manifest, forbiddenSha))));

        byte[] remoteSchema = manifest("{\"$ref\":\"https://evil.invalid/schema\"}");
        Path remoteJar = createJar(remoteSchema, false);
        String remoteSha = sha256(remoteJar);
        assertThrows(PluginArtifactException.class, () -> verifier.verify(
                coordinates(remoteJar, remoteSha, sign(pair, remoteSchema, remoteSha))));
    }

    private PluginArtifactVerifier verifier(KeyPair pair) {
        return new PluginArtifactVerifier(mapper, key -> Optional.of(
                new TrustedSigningKey(key, pair.getPublic(), "Ed25519")));
    }

    private PluginArtifactCoordinates coordinates(Path jar, String sha, String signature) {
        return new PluginArtifactCoordinates("fixture-plugin", "1.2.0", jar, sha, signature, "test-key");
    }

    private byte[] manifest(String schema) {
        String value = """
                {"manifestVersion":"1","pluginId":"fixture-plugin","version":"1.2.0","spiVersion":"1.0",
                 "displayName":"Fixture","provider":"test","entryClass":"example.plugin.FixtureConnectorPlugin",
                 "capabilities":["RESPONSE_PARSER"],"minHostVersion":"2.0.0","configSchema":%s,
                 "permissions":{"networkProtocols":["https"],"networkHosts":["api.example.com"]}}
                """.formatted(schema);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private Path createJar(byte[] manifest, boolean forbidden) throws Exception {
        Path jar = Files.createTempFile(tempDir, "plugin-", ".jar");
        Path classes = Path.of(FixtureConnectorPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(PluginManifestReader.MANIFEST_PATH));
            output.write(manifest);
            output.closeEntry();
            try (var files = Files.walk(classes.resolve("example/plugin"))) {
                files.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                    try {
                        String name = classes.relativize(path).toString().replace('\\', '/');
                        output.putNextEntry(new JarEntry(name));
                        output.write(Files.readAllBytes(path));
                        output.closeEntry();
                    } catch (IOException exception) { throw new RuntimeException(exception); }
                });
            }
            if (forbidden) {
                output.putNextEntry(new JarEntry("com/dataplatform/plugin/spi/Evil.class"));
                output.write(new byte[]{0, 1, 2});
                output.closeEntry();
            }
        }
        return jar;
    }

    private String sign(KeyPair pair, byte[] manifest, String sha) throws Exception {
        JsonNode tree = mapper.readTree(manifest);
        byte[] canonical = CanonicalJson.write(mapper, tree);
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(PluginArtifactVerifier.signaturePayload(canonical, sha));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
