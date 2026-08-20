package com.dataplatform.plugin.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One-class authoring entry point for a dedicated vendor connector.
 * Implementations keep request state in {@link VendorConnectorInvocation}, never in plugin fields.
 */
public abstract class AbstractVendorConnectorPlugin implements ConnectorPlugin {

    private final ConnectorTransportMode transportMode;
    private final ConnectorOutputMode outputMode;

    protected AbstractVendorConnectorPlugin() {
        this(ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING);
    }

    protected AbstractVendorConnectorPlugin(
            ConnectorTransportMode transportMode,
            ConnectorOutputMode outputMode) {
        this.transportMode = Objects.requireNonNull(transportMode, "transportMode");
        this.outputMode = Objects.requireNonNull(outputMode, "outputMode");
    }

    public final ConnectorAuthoringModel authoringModel() {
        return ConnectorAuthoringModel.SIMPLE_CONNECTOR;
    }

    public final ConnectorTransportMode transportMode() {
        return transportMode;
    }

    public final ConnectorOutputMode outputMode() {
        return outputMode;
    }

    protected abstract ConnectorRequest buildRequest(
            VendorConnectorInvocation invocation) throws ConnectorException;

    protected ConnectorRequest processRequest(
            VendorConnectorInvocation invocation,
            ConnectorRequest request) throws ConnectorException {
        return request;
    }

    protected ConnectorRawResponse processResponse(
            VendorConnectorInvocation invocation,
            ConnectorRawResponse response) throws ConnectorException {
        return response;
    }

    protected abstract VendorParseResult parseResponse(
            VendorConnectorInvocation invocation,
            ConnectorRawResponse response) throws ConnectorException;

    protected Map<String, Object> normalizeResponse(
            VendorConnectorInvocation invocation,
            VendorParseResult parsed) throws ConnectorException {
        return parsed.data();
    }

    protected ConnectorRawResponse executeManagedTransport(
            VendorConnectorInvocation invocation,
            ManagedTransportSession session,
            ConnectorRequest request) throws ConnectorException {
        return session.execute(request);
    }

    @Override
    public final List<ConnectorStageFactory> stageFactories() {
        return VendorConnectorStageAdapters.create(this);
    }

    @Override
    public final void initialize(PluginContext context) throws ConnectorException {
        onInitialize(Objects.requireNonNull(context, "context"));
    }

    protected void onInitialize(PluginContext context) throws ConnectorException {
        // Default high-level plugins need no mutable initialization state.
    }

    @Override
    public final PluginSelfTestResult selfTest() {
        PluginSelfTestResult result = performSelfTest();
        return result == null ? PluginSelfTestResult.failure("Plugin self-test returned no result") : result;
    }

    protected PluginSelfTestResult performSelfTest() {
        return PluginSelfTestResult.success();
    }

    @Override
    public final void close() throws Exception {
        onClose();
    }

    protected void onClose() throws Exception {
        // No resources are owned by default.
    }
}
