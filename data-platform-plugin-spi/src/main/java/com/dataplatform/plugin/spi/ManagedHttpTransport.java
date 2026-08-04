package com.dataplatform.plugin.spi;

public interface ManagedHttpTransport {

    ConnectorRawResponse execute(ConnectorRequest request, StageExecutionContext context)
            throws ConnectorException;
}
