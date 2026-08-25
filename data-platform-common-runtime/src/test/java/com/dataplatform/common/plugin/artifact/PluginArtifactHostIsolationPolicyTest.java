package com.dataplatform.common.plugin.artifact;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginArtifactHostIsolationPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsAPluginThatAttemptsToShadowAHostDomainClass() throws Exception {
        Path jar = tempDir.resolve("host-shadow.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(PluginManifestReader.MANIFEST_PATH));
            output.write(manifest());
            output.closeEntry();
            output.putNextEntry(new JarEntry("com/dataplatform/access/shadow/HostService.class"));
            output.write(new byte[]{0, 1, 2});
            output.closeEntry();
        }
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(jar)));
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(
                new ObjectMapper(), ignored -> Optional.empty());

        PluginArtifactException error = assertThrows(PluginArtifactException.class,
                () -> verifier.verify(new PluginArtifactCoordinates(
                        "host-shadow", "1.0.0", jar, sha, "AA==", "unknown")));

        assertTrue(error.getMessage().contains("forbidden package"));
    }

    private byte[] manifest() {
        return """
                {"manifestVersion":"1","pluginId":"host-shadow","version":"1.0.0","spiVersion":"1.0",
                 "displayName":"Host shadow","provider":"test","entryClass":"example.HostShadowPlugin",
                 "capabilities":["RESPONSE_PARSER"],"minHostVersion":"2.0.0",
                 "configSchema":{"type":"object"},
                 "permissions":{"networkProtocols":[],"networkHosts":[]}}
                """.getBytes(StandardCharsets.UTF_8);
    }
}
