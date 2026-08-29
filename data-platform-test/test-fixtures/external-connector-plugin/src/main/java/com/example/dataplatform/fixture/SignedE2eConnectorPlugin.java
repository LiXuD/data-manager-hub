package com.example.dataplatform.fixture;

import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.ManagedTransportSession;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
import com.dataplatform.plugin.spi.VendorParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * External high-level connector used only by the isolated connector E2E fixture.
 *
 * <p>The same signed artifact exercises a platform-managed single call, a bounded
 * token/business exchange, and a bounded polling exchange. The host still owns
 * transport, delivery facts, retry, billing, caching, and error policy.</p>
 */
public final class SignedE2eConnectorPlugin extends AbstractVendorConnectorPlugin {

    public static final String PLUGIN_ID = "e2e-signed-connector";
    public static final String VERSION = "1.1.0";

    private static final String FLOW_SINGLE_HTTP = "single-http";
    private static final String FLOW_TOKEN_BUSINESS = "token-business";
    private static final String FLOW_ASYNC_POLLING = "async-polling";

    public SignedE2eConnectorPlugin() {
        super(ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP,
                ConnectorOutputMode.PLUGIN_NORMALIZED);
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(PLUGIN_ID, VERSION, "1.0", "E2E Signed Connector",
                "test-fixture", Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER));
    }

    @Override
    protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation)
            throws ConnectorException {
        JsonNode config = invocation.pluginConfig();
        String flow = text(config, "flow");
        URI endpoint = URI.create(endpoint(config, flow));
        Object payload = FLOW_TOKEN_BUSINESS.equals(flow)
                ? Map.of("clientId", text(config, "clientId"),
                "clientSecret", secretText(invocation, config, "clientSecret"))
                : invocation.standardInput();
        return request(invocation, endpoint, "POST", Map.of(),
                invocation.objectCodec().write(payload));
    }

    @Override
    protected ConnectorRawResponse executeManagedTransport(
            VendorConnectorInvocation invocation,
            ManagedTransportSession session,
            ConnectorRequest initialRequest) throws ConnectorException {
        String flow = text(invocation.pluginConfig(), "flow");
        return switch (flow) {
            case FLOW_SINGLE_HTTP -> session.execute(initialRequest);
            case FLOW_TOKEN_BUSINESS -> executeTokenBusiness(invocation, session, initialRequest);
            case FLOW_ASYNC_POLLING -> executePolling(invocation, session, initialRequest);
            default -> throw configurationFailure("FIXTURE_FLOW_INVALID",
                    "Fixture connector flow is not supported");
        };
    }

    @Override
    protected VendorParseResult parseResponse(
            VendorConnectorInvocation invocation,
            ConnectorRawResponse response) throws ConnectorException {
        Map<String, Object> data = readObject(invocation, response, "FIXTURE_RESPONSE_INVALID");
        if (Boolean.FALSE.equals(data.get("success"))
                || "FAILED".equals(data.get("status"))) {
            Object code = data.get("errorCode");
            String businessCode = code instanceof String && !((String) code).isBlank()
                    ? (String) code : "FIXTURE_VENDOR_REJECTED";
            return VendorParseResult.rejected(data, businessCode,
                    "Fixture vendor rejected the request");
        }
        if ("PENDING".equals(data.get("status"))) {
            return VendorParseResult.unknown(data, "FIXTURE_POLLING_INCOMPLETE",
                    "Fixture polling did not reach a terminal state");
        }
        return VendorParseResult.success(data, "FIXTURE_SUCCESS",
                BillingSignal.ELIGIBLE, CacheSignal.CACHEABLE,
                "Fixture vendor response accepted");
    }

    @Override
    protected Map<String, Object> normalizeResponse(
            VendorConnectorInvocation invocation,
            VendorParseResult parsed) {
        return parsed.data();
    }

    private ConnectorRawResponse executeTokenBusiness(
            VendorConnectorInvocation invocation,
            ManagedTransportSession session,
            ConnectorRequest tokenRequest) throws ConnectorException {
        ConnectorRawResponse tokenResponse = session.execute(tokenRequest);
        Map<String, Object> tokenBody = readObject(invocation, tokenResponse,
                "FIXTURE_TOKEN_RESPONSE_INVALID");
        String token = textValue(tokenBody.get("accessToken"));
        if (token == null) {
            token = textValue(tokenBody.get("access_token"));
        }
        if (token == null) {
            throw responseFailure("FIXTURE_TOKEN_RESPONSE_INVALID",
                    "Fixture token response did not contain a token");
        }
        byte[] body = invocation.objectCodec().write(invocation.standardInput());
        ConnectorRequest businessRequest = request(invocation,
                URI.create(text(invocation.pluginConfig(), "businessEndpoint")),
                "POST", Map.of("Authorization", List.of("Bearer ".concat(token))), body);
        return session.execute(businessRequest);
    }

    private ConnectorRawResponse executePolling(
            VendorConnectorInvocation invocation,
            ManagedTransportSession session,
            ConnectorRequest submitRequest) throws ConnectorException {
        ConnectorRawResponse submitted = session.execute(submitRequest);
        Map<String, Object> submitBody = readObject(invocation, submitted,
                "FIXTURE_SUBMIT_RESPONSE_INVALID");
        String pollPath = textValue(submitBody.get("pollPath"));
        if (pollPath == null) {
            throw responseFailure("FIXTURE_SUBMIT_RESPONSE_INVALID",
                    "Fixture submit response did not contain a polling path");
        }
        URI pollBase = URI.create(text(invocation.pluginConfig(), "pollEndpointBase"));
        int maxPolls = invocation.pluginConfig().path("maxPolls").asInt(2);
        if (maxPolls < 1 || maxPolls > 4) {
            throw configurationFailure("FIXTURE_POLLING_LIMIT_INVALID",
                    "Fixture polling limit is outside the allowed range");
        }

        ConnectorRawResponse last = submitted;
        for (int attempt = 0; attempt < maxPolls; attempt++) {
            URI pollEndpoint = pollBase.resolve(pollPath);
            last = requestAndExecute(invocation, session, pollEndpoint);
            Map<String, Object> pollBody = readObject(invocation, last,
                    "FIXTURE_POLL_RESPONSE_INVALID");
            if (!"PENDING".equals(pollBody.get("status"))) {
                return last;
            }
        }
        return last;
    }

    private ConnectorRawResponse requestAndExecute(
            VendorConnectorInvocation invocation,
            ManagedTransportSession session,
            URI endpoint) throws ConnectorException {
        return session.execute(request(invocation, endpoint, "GET", Map.of(), new byte[0]));
    }

    private ConnectorRequest request(
            VendorConnectorInvocation invocation,
            URI endpoint,
            String method,
            Map<String, List<String>> extraHeaders,
            byte[] body) {
        Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
        headers.put("Accept", List.of("application/json"));
        if ("POST".equals(method)) {
            headers.put("Content-Type", List.of("application/json; charset=utf-8"));
        }
        headers.putAll(extraHeaders);
        JsonNode config = invocation.pluginConfig();
        return new ConnectorRequest(method, endpoint, headers, Map.of(),
                "application/json; charset=utf-8", body,
                duration(config, "connectTimeoutMs", 2_000),
                duration(config, "readTimeoutMs", 5_000),
                duration(config, "totalTimeoutMs", 8_000),
                IdempotencyPolicy.IDEMPOTENT, null,
                config.path("maxResponseBytes").asLong(1024 * 1024));
    }

    private String endpoint(JsonNode config, String flow) throws ConnectorException {
        return switch (flow) {
            case FLOW_SINGLE_HTTP -> text(config, "endpoint");
            case FLOW_TOKEN_BUSINESS -> text(config, "tokenEndpoint");
            case FLOW_ASYNC_POLLING -> text(config, "submitEndpoint");
            default -> throw configurationFailure("FIXTURE_FLOW_INVALID",
                    "Fixture connector flow is not supported");
        };
    }

    private Map<String, Object> readObject(
            VendorConnectorInvocation invocation,
            ConnectorRawResponse response,
            String code) throws ConnectorException {
        try {
            Object value = invocation.objectCodec().read(response.body(), Object.class);
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("response is not an object");
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("response contains a non-string field");
                }
                result.put(key, entry.getValue());
            }
            return result;
        } catch (ConnectorException | RuntimeException exception) {
            throw new ConnectorException(
                    com.dataplatform.plugin.spi.ErrorCategory.RESPONSE_PARSE_ERROR,
                    code, "Fixture response could not be parsed",
                    RequestDeliveryState.SENT, exception);
        }
    }

    private Duration duration(JsonNode config, String field, long defaultValue) {
        return Duration.ofMillis(config.path(field).asLong(defaultValue));
    }

    private String text(JsonNode config, String field) throws ConnectorException {
        JsonNode value = config == null ? null : config.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw configurationFailure("FIXTURE_CONFIG_INVALID",
                    "Fixture connector configuration is incomplete");
        }
        return value.asText();
    }

    private String secretText(VendorConnectorInvocation invocation, JsonNode config, String field)
            throws ConnectorException {
        JsonNode value = config == null ? null : config.get(field);
        String secretRef = value != null && value.isTextual() ? value.asText()
                : value != null && value.isObject() && value.size() == 1
                && value.path("secretRef").isTextual() ? value.path("secretRef").asText() : null;
        if (secretRef == null || secretRef.isBlank()) {
            throw configurationFailure("FIXTURE_CONFIG_INVALID",
                    "Fixture connector configuration is incomplete");
        }
        try (SecretValue secret = invocation.secretResolver().resolve(secretRef)) {
            return secret.materialize();
        }
    }

    private String textValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private ConnectorException configurationFailure(String code, String message) {
        return new ConnectorException(
                com.dataplatform.plugin.spi.ErrorCategory.CONFIGURATION_ERROR,
                code, message, RequestDeliveryState.NOT_SENT);
    }

    private ConnectorException responseFailure(String code, String message) {
        return new ConnectorException(
                com.dataplatform.plugin.spi.ErrorCategory.RESPONSE_PARSE_ERROR,
                code, message, RequestDeliveryState.SENT);
    }
}
