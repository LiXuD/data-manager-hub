package com.dataplatform.masterdata.connector.service;

/** Signals that the compatibility write surface has been deliberately retired. */
public class ConnectorLegacyWriteRetiredException extends RuntimeException {
    public ConnectorLegacyWriteRetiredException(String message) {
        super(message);
    }
}
