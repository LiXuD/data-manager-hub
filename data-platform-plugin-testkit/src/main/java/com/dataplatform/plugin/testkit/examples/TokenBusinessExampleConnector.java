package com.dataplatform.plugin.testkit.examples;

import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.ManagedTransportSession;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
import com.dataplatform.plugin.spi.VendorParseResult;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One-entry-class Token + business request example using only the bounded host session. */
public final class TokenBusinessExampleConnector extends AbstractVendorConnectorPlugin {

    public static final String PLUGIN_ID = "testkit-token-business";

    public TokenBusinessExampleConnector() {
        super(ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP,
                ConnectorOutputMode.PLUGIN_NORMALIZED);
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(PLUGIN_ID, "1.0.0", "1.1", "TestKit Token Business",
                "data-platform", Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER));
    }

    @Override
    protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation) {
        return SingleHttpExampleConnector.request(
                URI.create(invocation.pluginConfig().path("tokenEndpoint").asText()), Map.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ConnectorRawResponse executeManagedTransport(
            VendorConnectorInvocation invocation,
            ManagedTransportSession session,
            ConnectorRequest tokenRequest) throws ConnectorException {
        ConnectorRawResponse tokenResponse = session.execute(tokenRequest);
        Map<String, Object> tokenBody = invocation.objectCodec().read(tokenResponse.body(), Map.class);
        String token = String.valueOf(tokenBody.get("access_token"));
        ConnectorRequest businessRequest = SingleHttpExampleConnector.request(
                URI.create(invocation.pluginConfig().path("businessEndpoint").asText()),
                Map.of("Authorization", List.of("Bearer " + token)));
        return session.execute(businessRequest);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected VendorParseResult parseResponse(
            VendorConnectorInvocation invocation,
            ConnectorRawResponse response) throws ConnectorException {
        return VendorParseResult.success(invocation.objectCodec().read(response.body(), Map.class),
                "SUCCESS", com.dataplatform.plugin.spi.BillingSignal.ELIGIBLE,
                com.dataplatform.plugin.spi.CacheSignal.CACHEABLE,
                "multi HTTP response accepted");
    }

    @Override
    protected Map<String, Object> normalizeResponse(
            VendorConnectorInvocation invocation,
            VendorParseResult parsed) {
        return parsed.data();
    }
}
