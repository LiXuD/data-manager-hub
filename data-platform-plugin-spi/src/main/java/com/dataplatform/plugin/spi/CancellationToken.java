package com.dataplatform.plugin.spi;

/** Host-owned cancellation state for one connector invocation. */
public interface CancellationToken {

    boolean isCancelled();

    void throwIfCancelled() throws ConnectorException;
}
