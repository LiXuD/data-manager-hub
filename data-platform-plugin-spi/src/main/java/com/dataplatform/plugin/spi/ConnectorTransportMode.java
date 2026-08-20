package com.dataplatform.plugin.spi;

/** Determines which side owns the connector's network transport stage. */
public enum ConnectorTransportMode {
    HOST_SINGLE_HTTP,
    HOST_MANAGED_MULTI_HTTP
}
