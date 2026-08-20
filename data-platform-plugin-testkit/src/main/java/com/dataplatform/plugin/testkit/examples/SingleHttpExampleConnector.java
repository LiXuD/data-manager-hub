package com.dataplatform.plugin.testkit.examples;

import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
import com.dataplatform.plugin.spi.VendorParseResult;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** One-entry-class HOST_SINGLE_HTTP example; platform test steps own transport and mapping. */
public final class SingleHttpExampleConnector extends AbstractVendorConnectorPlugin {

    public static final String PLUGIN_ID = "testkit-single-http";

    public SingleHttpExampleConnector() {
        super(ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING);
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(PLUGIN_ID, "1.0.0", "1.1", "TestKit Single HTTP",
                "data-platform", Set.of(StageCapability.REQUEST_BUILDER,
                StageCapability.RESPONSE_PARSER));
    }

    @Override
    protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation) {
        return request(URI.create(invocation.pluginConfig().path("endpoint").asText()), Map.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected VendorParseResult parseResponse(
            VendorConnectorInvocation invocation,
            ConnectorRawResponse response) throws ConnectorException {
        Map<String, Object> body = invocation.objectCodec().read(response.body(), Map.class);
        return VendorParseResult.success(body, "OK",
                com.dataplatform.plugin.spi.BillingSignal.ELIGIBLE,
                com.dataplatform.plugin.spi.CacheSignal.CACHEABLE,
                "single HTTP response accepted");
    }

    static ConnectorRequest request(URI endpoint, Map<String, java.util.List<String>> headers) {
        return new ConnectorRequest("GET", endpoint, headers, Map.of(), "application/json",
                new byte[0], Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3),
                IdempotencyPolicy.IDEMPOTENT, null, 64 * 1024L);
    }
}
