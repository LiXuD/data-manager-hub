package com.dataplatform.plugin.spi;

/**
 * Optional stage contract for factories declaring {@link StageLifecycle#REQUEST_SCOPED}.
 * The host invokes {@link #close()} exactly once for every successfully created instance.
 */
public interface RequestScopedConnectorStage extends ConnectorStage, AutoCloseable {
    @Override
    void close();
}
