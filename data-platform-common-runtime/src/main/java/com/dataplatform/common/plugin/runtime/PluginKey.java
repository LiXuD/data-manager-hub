package com.dataplatform.common.plugin.runtime;

public record PluginKey(String pluginId, String version) {

    public PluginKey {
        if (pluginId == null || pluginId.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("pluginId and version are required");
        }
    }
}
