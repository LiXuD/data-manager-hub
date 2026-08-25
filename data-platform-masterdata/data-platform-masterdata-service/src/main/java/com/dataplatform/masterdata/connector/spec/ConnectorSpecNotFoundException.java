package com.dataplatform.masterdata.connector.spec;

/** Stable 404 control-plane error. */
public final class ConnectorSpecNotFoundException extends RuntimeException {
    public ConnectorSpecNotFoundException(String message) { super(message); }
}
