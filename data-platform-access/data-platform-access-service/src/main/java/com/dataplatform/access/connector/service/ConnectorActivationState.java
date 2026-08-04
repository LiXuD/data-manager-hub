package com.dataplatform.access.connector.service;

/** Persisted lifecycle states allowed by connector_plugin_activation. */
public enum ConnectorActivationState {
    LOADING,
    READY,
    FAILED,
    RELEASING,
    RELEASED
}
