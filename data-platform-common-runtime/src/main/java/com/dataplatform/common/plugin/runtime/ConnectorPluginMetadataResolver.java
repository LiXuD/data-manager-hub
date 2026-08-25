package com.dataplatform.common.plugin.runtime;

@FunctionalInterface
public interface ConnectorPluginMetadataResolver {
    ConnectorPluginMetadata resolve(String pluginId, String version);
}
