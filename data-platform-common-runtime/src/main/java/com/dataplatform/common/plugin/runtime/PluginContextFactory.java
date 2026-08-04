package com.dataplatform.common.plugin.runtime;

import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.plugin.spi.PluginContext;

@FunctionalInterface
public interface PluginContextFactory {
    PluginContext create(PluginManifest manifest);
}
