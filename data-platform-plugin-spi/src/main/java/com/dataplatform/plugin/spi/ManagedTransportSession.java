package com.dataplatform.plugin.spi;

/**
 * Per-invocation host-managed session for a bounded multi-request connector.
 * Network policy and delivery facts remain owned by the host implementation.
 */
public interface ManagedTransportSession {

    ConnectorRawResponse execute(ConnectorRequest request) throws ConnectorException;

    Deadline deadline();

    CancellationToken cancellationToken();

    int remainingCalls();
}
