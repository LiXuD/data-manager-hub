package com.dataplatform.masterdata.connector.service;

/**
 * Indicates that a connector plugin artifact failed an expected, safe validation check.
 */
public class PluginArtifactValidationException extends IllegalArgumentException {
    public PluginArtifactValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
