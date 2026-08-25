package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.IdempotencyContext;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
import com.dataplatform.plugin.spi.VendorConnectorStageAdapters;
import java.util.Map;

/** Builds the real request-scoped high-level SDK context from a pinned plugin handle. */
public final class VendorConnectorHostContextFactory {

    VendorConnectorStageHostContext create(
            ConnectorExecutionRequest request,
            ConnectorStageDefinition stage,
            ConnectorRequest actualRequest,
            PluginHandle handle) throws ConnectorException {
        PluginContext pluginContext = handle.pluginContext().orElseThrow(() -> failure(
                ErrorCategory.PLUGIN_NOT_READY, "PLUGIN_CONTEXT_UNAVAILABLE",
                "Connector plugin host context is unavailable", RequestDeliveryState.NOT_SENT, null));
        try {
            HostDeadline deadline = new HostDeadline(pluginContext.clock(), request.deadline());
            HostCancellationToken cancellation = new HostCancellationToken(request.cancellationRequested());
            DefaultStageExecutionContext stageContext = new DefaultStageExecutionContext(
                    pluginContext.clock(), request.deadline(), request.cancellationRequested(),
                    pluginContext.logger(), pluginContext.metrics());
            IdempotencyContext idempotency = actualRequest == null
                    ? request.idempotencyContext() : HostIdempotencyContext.fromRequest(actualRequest);
            VendorConnectorInvocation invocation = VendorConnectorInvocation.immutable(
                    request.requestId(), request.vendorConfigId(),
                    pluginContext.objectCodec().toTree(request.standardParameters()), stage.config(),
                    deadline, cancellation, request.attemptNo(), idempotency,
                    pluginContext.secretResolver(), pluginContext.objectCodec(), pluginContext.clock(),
                    pluginContext.logger(), pluginContext.metrics());
            HostManagedTransportSession session = managedSession(
                    handle, stage, pluginContext, stageContext, deadline, cancellation);
            return new VendorConnectorStageHostContext(stageContext, invocation, session);
        } catch (ConnectorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(ErrorCategory.CONTRACT_VIOLATION, "VENDOR_HOST_CONTEXT_INVALID",
                    "Connector host context could not be created", RequestDeliveryState.NOT_SENT, exception);
        }
    }

    private HostManagedTransportSession managedSession(
            PluginHandle handle,
            ConnectorStageDefinition stage,
            PluginContext pluginContext,
            DefaultStageExecutionContext stageContext,
            HostDeadline deadline,
            HostCancellationToken cancellation) {
        if (stage.capability() != StageCapability.TRANSPORT
                || !(handle.plugin() instanceof AbstractVendorConnectorPlugin vendorPlugin)
                || vendorPlugin.transportMode() != ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP) {
            return null;
        }
        return new HostManagedTransportSession(pluginContext.managedHttpTransport(), stageContext,
                deadline, cancellation, pluginContext.metrics(), Map.of(
                "pluginId", handle.key().pluginId(),
                "pluginVersion", handle.key().version(),
                "transportMode", ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP.name()));
    }

    private static ConnectorException failure(
            ErrorCategory category, String code, String message,
            RequestDeliveryState delivery, Throwable cause) {
        return new ConnectorException(category, code, message, delivery, cause);
    }
}

final class VendorConnectorStageHostContext implements VendorConnectorStageAdapters.HostContext {

    private final DefaultStageExecutionContext delegate;
    private final VendorConnectorInvocation invocation;
    private final HostManagedTransportSession session;

    VendorConnectorStageHostContext(
            DefaultStageExecutionContext delegate,
            VendorConnectorInvocation invocation,
            HostManagedTransportSession session) {
        this.delegate = delegate;
        this.invocation = invocation;
        this.session = session;
    }

    @Override public VendorConnectorInvocation vendorInvocation() { return invocation; }
    @Override public HostManagedTransportSession managedTransportSession() { return session; }
    HostManagedTransportSession hostManagedTransportSession() { return session; }
    @Override public java.time.Clock clock() { return delegate.clock(); }
    @Override public java.time.Instant deadline() { return delegate.deadline(); }
    @Override public boolean cancellationRequested() { return delegate.cancellationRequested(); }
    @Override public com.dataplatform.plugin.spi.PluginLogger logger() { return delegate.logger(); }
    @Override public com.dataplatform.plugin.spi.PluginMetricRecorder metrics() { return delegate.metrics(); }
}
