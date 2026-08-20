package com.dataplatform.plugin.spi;

/** Determines whether the plugin or the host produces the final normalized response. */
public enum ConnectorOutputMode {
    PLUGIN_NORMALIZED,
    HOST_MAPPING
}
