package com.dataplatform.masterdata.connector.service;

public class ConnectorConflictException extends RuntimeException {
    public ConnectorConflictException(String message) {
        super(message);
    }
}
