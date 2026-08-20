package com.dataplatform.plugin.spi;

/** Declares whether a plugin uses the high-level connector SDK or the raw stage SPI. */
public enum ConnectorAuthoringModel {
    SIMPLE_CONNECTOR,
    ADVANCED_PIPELINE
}
