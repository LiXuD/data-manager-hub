package com.dataplatform.common.plugin.artifact;

public class PluginArtifactException extends RuntimeException {

    public PluginArtifactException(String message) {
        super(message);
    }

    public PluginArtifactException(String message, Throwable cause) {
        super(message, cause);
    }
}
