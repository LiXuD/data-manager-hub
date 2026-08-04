package com.dataplatform.common.plugin.artifact;

import java.nio.file.Path;

public record VerifiedPluginArtifact(Path jarPath, PluginManifest manifest, String sha256) {
}
