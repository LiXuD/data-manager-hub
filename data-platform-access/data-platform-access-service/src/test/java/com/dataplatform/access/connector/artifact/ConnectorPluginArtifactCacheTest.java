package com.dataplatform.access.connector.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorPluginArtifactCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void reusesOnlyHashMatchingCacheEntry() throws Exception {
        byte[] bytes = "verified-plugin".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        ConnectorRuntimeProperties properties = properties();
        Path cached = tempDir.resolve("demo").resolve("1.0.0").resolve(hash)
                .resolve("connector-plugin.jar");
        Files.createDirectories(cached.getParent());
        Files.write(cached, bytes);
        ConnectorPluginArtifactCache cache = new ConnectorPluginArtifactCache(properties);

        Path resolved = cache.resolve(artifact("https://repo.example/plugins/demo.jar", hash));

        assertEquals(cached, resolved);
    }

    @Test
    void rejectsHttpAndRepositoryOutsideAllowlist() {
        ConnectorPluginArtifactCache cache = new ConnectorPluginArtifactCache(properties());
        String hash = "a".repeat(64);

        assertThrows(IllegalArgumentException.class,
                () -> cache.resolve(artifact("http://repo.example/plugins/demo.jar", hash)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.resolve(artifact("https://evil.example/demo.jar", hash)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.resolve(artifact("https://repo.example/plugins.evil/demo.jar", hash)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.resolve(artifact("https://repo.example/plugins/demo.jar?token=secret", hash)));
    }

    private ConnectorRuntimeProperties properties() {
        ConnectorRuntimeProperties properties = new ConnectorRuntimeProperties();
        properties.setCacheDirectory(tempDir.toString());
        properties.setRepositoryAllowedPrefixes(List.of("https://repo.example/plugins/"));
        return properties;
    }

    private PluginArtifactDescriptorDTO artifact(String uri, String hash) {
        return new PluginArtifactDescriptorDTO(
                "demo", "1.0.0", "1.0", "example.Demo", uri, hash,
                "signature", "key-1", "{}", "{}", List.of("TRANSPORT"),
                "{}", "1.0.0", "VERIFIED");
    }
}
