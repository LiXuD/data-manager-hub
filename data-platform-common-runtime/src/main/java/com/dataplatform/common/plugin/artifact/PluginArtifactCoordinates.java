package com.dataplatform.common.plugin.artifact;

import java.nio.file.Path;
import java.util.Objects;

public record PluginArtifactCoordinates(
        String pluginId,
        String version,
        Path jarPath,
        String expectedSha256,
        String detachedSignature,
        String signingKeyId) {

    public PluginArtifactCoordinates {
        pluginId = requireText(pluginId, "pluginId");
        version = requireText(version, "version");
        jarPath = Objects.requireNonNull(jarPath, "jarPath").toAbsolutePath().normalize();
        expectedSha256 = requireText(expectedSha256, "expectedSha256").toLowerCase();
        detachedSignature = requireText(detachedSignature, "detachedSignature");
        signingKeyId = requireText(signingKeyId, "signingKeyId");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
