package com.dataplatform.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Host-controlled adapters from the high-level connector SDK to the six-stage SPI. */
public final class VendorConnectorStageAdapters {

    private static final int MAX_MANAGED_TRANSPORT_CALLS = 5;
    public static final String VENDOR_BUSINESS_CODE_OUTPUT = "vendor.businessCode";
    public static final String VENDOR_SAFE_MESSAGE_OUTPUT = "vendor.safeMessage";

    private VendorConnectorStageAdapters() {
    }

    /**
     * Stage contexts for high-level plugins are supplied by the host runtime. The invocation and
     * managed session are request-scoped and must not be retained after stage execution.
     */
    public interface HostContext extends StageExecutionContext {

        VendorConnectorInvocation vendorInvocation();

        ManagedTransportSession managedTransportSession();
    }

    public static List<ConnectorStageFactory> create(AbstractVendorConnectorPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PluginDescriptor descriptor = Objects.requireNonNull(plugin.descriptor(), "plugin descriptor");
        Set<StageCapability> capabilities = descriptor.capabilities();
        requireCapability(capabilities, StageCapability.REQUEST_BUILDER);
        requireCapability(capabilities, StageCapability.RESPONSE_PARSER);
        validateTransportMode(plugin.transportMode(), capabilities);
        validateOutputMode(plugin, capabilities);

        List<ConnectorStageFactory> factories = new ArrayList<>();
        factories.add(new AdapterFactory(plugin, StageCapability.REQUEST_BUILDER));
        addIfDeclared(factories, plugin, capabilities, StageCapability.REQUEST_PROCESSOR);
        if (plugin.transportMode() == ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP) {
            factories.add(new AdapterFactory(plugin, StageCapability.TRANSPORT));
        }
        addIfDeclared(factories, plugin, capabilities, StageCapability.RESPONSE_PROCESSOR);
        factories.add(new AdapterFactory(plugin, StageCapability.RESPONSE_PARSER));
        if (plugin.outputMode() == ConnectorOutputMode.PLUGIN_NORMALIZED) {
            factories.add(new AdapterFactory(plugin, StageCapability.RESPONSE_NORMALIZER));
        }
        return List.copyOf(factories);
    }

    private static void addIfDeclared(
            List<ConnectorStageFactory> factories,
            AbstractVendorConnectorPlugin plugin,
            Set<StageCapability> capabilities,
            StageCapability capability) {
        if (capabilities.contains(capability)) {
            factories.add(new AdapterFactory(plugin, capability));
        }
    }

    private static void requireCapability(Set<StageCapability> capabilities, StageCapability capability) {
        if (!capabilities.contains(capability)) {
            throw new IllegalStateException("Simple connector must declare " + capability);
        }
    }

    private static void validateTransportMode(
            ConnectorTransportMode mode,
            Set<StageCapability> capabilities) {
        boolean declared = capabilities.contains(StageCapability.TRANSPORT);
        if (mode == ConnectorTransportMode.HOST_SINGLE_HTTP && declared) {
            throw new IllegalStateException("HOST_SINGLE_HTTP must not declare TRANSPORT");
        }
        if (mode == ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP && !declared) {
            throw new IllegalStateException("HOST_MANAGED_MULTI_HTTP must declare TRANSPORT");
        }
    }

    private static void validateOutputMode(
            AbstractVendorConnectorPlugin plugin,
            Set<StageCapability> capabilities) {
        boolean declared = capabilities.contains(StageCapability.RESPONSE_NORMALIZER);
        if (plugin.outputMode() == ConnectorOutputMode.HOST_MAPPING && declared) {
            throw new IllegalStateException("HOST_MAPPING must not declare RESPONSE_NORMALIZER");
        }
        if (plugin.outputMode() == ConnectorOutputMode.PLUGIN_NORMALIZED) {
            if (!declared) {
                throw new IllegalStateException("PLUGIN_NORMALIZED must declare RESPONSE_NORMALIZER");
            }
            if (!overridesNormalizeResponse(plugin.getClass())) {
                throw new IllegalStateException("PLUGIN_NORMALIZED must override normalizeResponse");
            }
        }
    }

    private static boolean overridesNormalizeResponse(Class<?> pluginType) {
        Class<?> current = pluginType;
        while (current != null && current != AbstractVendorConnectorPlugin.class) {
            try {
                current.getDeclaredMethod("normalizeResponse",
                        VendorConnectorInvocation.class, VendorParseResult.class);
                return true;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return false;
    }

    private static final class AdapterFactory implements ConnectorStageFactory {

        private final AbstractVendorConnectorPlugin plugin;
        private final StageCapability capability;

        private AdapterFactory(AbstractVendorConnectorPlugin plugin, StageCapability capability) {
            this.plugin = plugin;
            this.capability = capability;
        }

        @Override
        public StageCapability capability() {
            return capability;
        }

        @Override
        public void validate(JsonNode config, PluginValidationContext context) throws ConnectorException {
            Objects.requireNonNull(context, "context");
            if (config == null || !config.isObject()) {
                throw failure(ErrorCategory.CONFIGURATION_ERROR, "INVALID_CONNECTOR_CONFIG",
                        "Connector plugin config must be an object", RequestDeliveryState.NOT_SENT, null);
            }
        }

        @Override
        public ConnectorStage create(CompiledStageConfig config) throws ConnectorException {
            Objects.requireNonNull(config, "config");
            if (config.capability() != capability) {
                throw failure(ErrorCategory.CONFIGURATION_ERROR, "CAPABILITY_CONFIG_MISMATCH",
                        "Compiled capability does not match connector adapter",
                        RequestDeliveryState.NOT_SENT, null);
            }
            return new AdapterStage(plugin, capability, config.config());
        }
    }

    private static final class AdapterStage implements ConnectorStage {

        private final AbstractVendorConnectorPlugin plugin;
        private final StageCapability capability;
        private final JsonNode expectedPluginConfig;

        private AdapterStage(
                AbstractVendorConnectorPlugin plugin,
                StageCapability capability,
                JsonNode expectedPluginConfig) {
            this.plugin = plugin;
            this.capability = capability;
            this.expectedPluginConfig = expectedPluginConfig.deepCopy();
        }

        @Override
        public StageCapability capability() {
            return capability;
        }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext context)
                throws ConnectorException {
            Objects.requireNonNull(exchange, "exchange");
            HostContext hostContext = requireHostContext(context, capability);
            VendorConnectorInvocation invocation = requireInvocation(hostContext, expectedPluginConfig, capability);
            ensureActive(invocation, context, beforeDelivery(capability));

            switch (capability) {
                case REQUEST_BUILDER -> executeRequestBuilder(exchange, invocation, context);
                case REQUEST_PROCESSOR -> executeRequestProcessor(exchange, invocation, context);
                case TRANSPORT -> executeTransport(exchange, invocation, hostContext, context);
                case RESPONSE_PROCESSOR -> executeResponseProcessor(exchange, invocation, context);
                case RESPONSE_PARSER -> executeResponseParser(exchange, invocation, context);
                case RESPONSE_NORMALIZER -> executeResponseNormalizer(exchange, invocation, context);
            }
        }

        private void executeRequestBuilder(
                ConnectorExchange exchange,
                VendorConnectorInvocation invocation,
                StageExecutionContext context) throws ConnectorException {
            ConnectorRequest request = invokePlugin(StageCapability.REQUEST_BUILDER,
                    RequestDeliveryState.NOT_SENT, () -> plugin.buildRequest(invocation));
            if (request == null) {
                throw contract("REQUEST_BUILDER_RETURNED_NULL",
                        "Request builder returned no request", RequestDeliveryState.NOT_SENT, null);
            }
            ensureActive(invocation, context, RequestDeliveryState.NOT_SENT);
            exchange.setRequest(request);
        }

        private void executeRequestProcessor(
                ConnectorExchange exchange,
                VendorConnectorInvocation invocation,
                StageExecutionContext context) throws ConnectorException {
            ConnectorRequest current = exchange.request();
            if (current == null) {
                throw contract("REQUEST_MISSING", "Request processor requires a request",
                        RequestDeliveryState.NOT_SENT, null);
            }
            ConnectorRequest processed = invokePlugin(StageCapability.REQUEST_PROCESSOR,
                    RequestDeliveryState.NOT_SENT, () -> plugin.processRequest(invocation, current));
            if (processed == null) {
                throw contract("REQUEST_PROCESSOR_RETURNED_NULL",
                        "Request processor returned no request", RequestDeliveryState.NOT_SENT, null);
            }
            ensureActive(invocation, context, RequestDeliveryState.NOT_SENT);
            exchange.setRequest(processed);
        }

        private void executeTransport(
                ConnectorExchange exchange,
                VendorConnectorInvocation invocation,
                HostContext hostContext,
                StageExecutionContext context) throws ConnectorException {
            ConnectorRequest request = exchange.request();
            if (request == null) {
                throw contract("REQUEST_MISSING", "Managed transport requires a request",
                        RequestDeliveryState.NOT_SENT, null);
            }
            ManagedTransportSession session = Objects.requireNonNull(
                    hostContext.managedTransportSession(), "managedTransportSession");
            validateSession(invocation, session);
            ConnectorRawResponse response = invokePlugin(StageCapability.TRANSPORT,
                    RequestDeliveryState.NOT_SENT,
                    () -> plugin.executeManagedTransport(invocation, session, request));
            if (response == null) {
                throw contract("TRANSPORT_RETURNED_NULL",
                        "Managed transport returned no response", RequestDeliveryState.NOT_SENT, null);
            }
            ensureActive(invocation, context, RequestDeliveryState.SENT);
            exchange.setRawResponse(response);
        }

        private void executeResponseProcessor(
                ConnectorExchange exchange,
                VendorConnectorInvocation invocation,
                StageExecutionContext context) throws ConnectorException {
            ConnectorRawResponse current = exchange.rawResponse();
            if (current == null) {
                throw contract("RESPONSE_MISSING", "Response processor requires a response",
                        RequestDeliveryState.SENT, null);
            }
            ConnectorRawResponse processed = invokePlugin(StageCapability.RESPONSE_PROCESSOR,
                    RequestDeliveryState.SENT, () -> plugin.processResponse(invocation, current));
            if (processed == null) {
                throw contract("RESPONSE_PROCESSOR_RETURNED_NULL",
                        "Response processor returned no response", RequestDeliveryState.SENT, null);
            }
            ensureActive(invocation, context, RequestDeliveryState.SENT);
            exchange.setRawResponse(processed);
        }

        private void executeResponseParser(
                ConnectorExchange exchange,
                VendorConnectorInvocation invocation,
                StageExecutionContext context) throws ConnectorException {
            ConnectorRawResponse response = exchange.rawResponse();
            if (response == null) {
                throw contract("RESPONSE_MISSING", "Response parser requires a response",
                        RequestDeliveryState.SENT, null);
            }
            VendorParseResult parsed = invokePlugin(StageCapability.RESPONSE_PARSER,
                    RequestDeliveryState.SENT, () -> plugin.parseResponse(invocation, response));
            if (parsed == null) {
                throw contract("RESPONSE_PARSER_RETURNED_NULL",
                        "Response parser returned no result", RequestDeliveryState.SENT, null);
            }
            ensureActive(invocation, context, RequestDeliveryState.SENT);
            exchange.setParsedResponse(parsed);
            exchange.setBusinessStatus(parsed.businessStatus());
            exchange.setBillingSignal(parsed.billingSignal());
            exchange.setCacheSignal(parsed.cacheSignal());
            if (parsed.vendorBusinessCode() != null) {
                exchange.recordStageOutput(VENDOR_BUSINESS_CODE_OUTPUT, parsed.vendorBusinessCode());
            }
            if (parsed.safeMessage() != null) {
                exchange.recordStageOutput(VENDOR_SAFE_MESSAGE_OUTPUT, parsed.safeMessage());
            }
        }

        private void executeResponseNormalizer(
                ConnectorExchange exchange,
                VendorConnectorInvocation invocation,
                StageExecutionContext context) throws ConnectorException {
            if (!(exchange.parsedResponse() instanceof VendorParseResult parsed)) {
                throw contract("PARSE_RESULT_MISSING",
                        "Response normalizer requires a vendor parse result", RequestDeliveryState.SENT, null);
            }
            Map<String, Object> normalized = invokePlugin(StageCapability.RESPONSE_NORMALIZER,
                    RequestDeliveryState.SENT, () -> plugin.normalizeResponse(invocation, parsed));
            if (normalized == null) {
                throw contract("RESPONSE_NORMALIZER_RETURNED_NULL",
                        "Response normalizer returned no data", RequestDeliveryState.SENT, null);
            }
            Map<String, Object> snapshot;
            try {
                snapshot = VendorParseResult.success(normalized).data();
            } catch (IllegalArgumentException exception) {
                throw contract("NORMALIZED_DATA_INVALID",
                        "Response normalizer returned invalid structured data",
                        RequestDeliveryState.SENT, exception);
            }
            ensureActive(invocation, context, RequestDeliveryState.SENT);
            exchange.setNormalizedData(snapshot);
        }
    }

    private static HostContext requireHostContext(
            StageExecutionContext context,
            StageCapability capability) throws ConnectorException {
        if (!(context instanceof HostContext hostContext)) {
            throw contract("VENDOR_HOST_CONTEXT_MISSING",
                    "Host did not provide a vendor connector context", beforeDelivery(capability), null);
        }
        return hostContext;
    }

    private static VendorConnectorInvocation requireInvocation(
            HostContext hostContext,
            JsonNode expectedPluginConfig,
            StageCapability capability) throws ConnectorException {
        try {
            VendorConnectorInvocation invocation = Objects.requireNonNull(
                    hostContext.vendorInvocation(), "vendorInvocation");
            if (invocation.requestId() == null || invocation.requestId().isBlank()
                    || invocation.vendorConfigId() <= 0
                    || invocation.standardInput() == null
                    || invocation.pluginConfig() == null
                    || invocation.deadline() == null
                    || invocation.cancellationToken() == null
                    || invocation.attemptNo() <= 0
                    || invocation.idempotencyContext() == null
                    || invocation.secretResolver() == null
                    || invocation.objectCodec() == null
                    || invocation.clock() == null
                    || invocation.logger() == null
                    || invocation.metricRecorder() == null) {
                throw new IllegalArgumentException("invocation is incomplete");
            }
            if (!expectedPluginConfig.equals(invocation.pluginConfig())) {
                throw new IllegalArgumentException("plugin config does not match compiled config");
            }
            if (!hostContext.deadline().equals(invocation.deadline().expiresAt())) {
                throw new IllegalArgumentException("invocation deadline does not match stage deadline");
            }
            return invocation;
        } catch (RuntimeException exception) {
            throw contract("VENDOR_INVOCATION_INVALID",
                    "Host provided an invalid vendor invocation", beforeDelivery(capability), exception);
        }
    }

    private static void validateSession(
            VendorConnectorInvocation invocation,
            ManagedTransportSession session) throws ConnectorException {
        try {
            Deadline sessionDeadline = Objects.requireNonNull(session.deadline(), "session deadline");
            CancellationToken cancellationToken = Objects.requireNonNull(
                    session.cancellationToken(), "session cancellationToken");
            if (!sessionDeadline.expiresAt().equals(invocation.deadline().expiresAt())) {
                throw new IllegalArgumentException("session deadline does not match invocation deadline");
            }
            int remainingCalls = session.remainingCalls();
            if (remainingCalls <= 0 || remainingCalls > MAX_MANAGED_TRANSPORT_CALLS) {
                throw new IllegalArgumentException("remainingCalls must be between 1 and 5");
            }
            if (cancellationToken.isCancelled()) {
                throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "REQUEST_CANCELLED",
                        "Connector execution was cancelled", RequestDeliveryState.NOT_SENT, null);
            }
            if (sessionDeadline.isExpired()) {
                throw failure(ErrorCategory.TRANSPORT_TIMEOUT, "EXECUTION_DEADLINE_EXCEEDED",
                        "Connector execution deadline was exceeded", RequestDeliveryState.NOT_SENT, null);
            }
        } catch (ConnectorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw contract("MANAGED_SESSION_INVALID",
                    "Managed transport session is not executable", RequestDeliveryState.NOT_SENT, exception);
        }
    }

    private static void ensureActive(
            VendorConnectorInvocation invocation,
            StageExecutionContext context,
            RequestDeliveryState deliveryState) throws ConnectorException {
        try {
            if (context.cancellationRequested() || invocation.cancellationToken().isCancelled()) {
                throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "REQUEST_CANCELLED",
                        "Connector execution was cancelled", deliveryState, null);
            }
            Instant expiresAt = invocation.deadline().expiresAt();
            if (invocation.deadline().isExpired()
                    || !invocation.clock().instant().isBefore(expiresAt)) {
                throw failure(ErrorCategory.TRANSPORT_TIMEOUT, "EXECUTION_DEADLINE_EXCEEDED",
                        "Connector execution deadline was exceeded", deliveryState, null);
            }
        } catch (ConnectorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw contract("VENDOR_INVOCATION_STATE_INVALID",
                    "Vendor invocation state could not be evaluated", deliveryState, exception);
        }
    }

    private static <T> T invokePlugin(
            StageCapability capability,
            RequestDeliveryState conservativeDelivery,
            PluginCall<T> call) throws ConnectorException {
        try {
            return call.invoke();
        } catch (ConnectorException exception) {
            throw validatePluginException(exception, capability, conservativeDelivery);
        } catch (RuntimeException exception) {
            throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "VENDOR_PLUGIN_UNEXPECTED_ERROR",
                    "Vendor connector plugin method failed", conservativeDelivery, exception);
        }
    }

    private static ConnectorException validatePluginException(
            ConnectorException exception,
            StageCapability capability,
            RequestDeliveryState conservativeDelivery) {
        boolean invalidMessage = exception.safeMessage().length() > VendorParseResult.MAX_SAFE_MESSAGE_LENGTH;
        boolean invalidDelivery = switch (capability) {
            case REQUEST_BUILDER, REQUEST_PROCESSOR ->
                    exception.deliveryState() != RequestDeliveryState.NOT_SENT;
            // Managed transport delivery is an authoritative host fact. The runtime replaces
            // any plugin-declared value with the session aggregate after adapter execution.
            case TRANSPORT -> false;
            case RESPONSE_PROCESSOR, RESPONSE_PARSER, RESPONSE_NORMALIZER ->
                    exception.deliveryState() != RequestDeliveryState.SENT;
        };
        if (invalidMessage || invalidDelivery) {
            return contract("PLUGIN_ERROR_CONTRACT_INVALID",
                    "Vendor connector returned an invalid error", conservativeDelivery, exception);
        }
        return exception;
    }

    private static RequestDeliveryState beforeDelivery(StageCapability capability) {
        return switch (capability) {
            case REQUEST_BUILDER, REQUEST_PROCESSOR, TRANSPORT -> RequestDeliveryState.NOT_SENT;
            case RESPONSE_PROCESSOR, RESPONSE_PARSER, RESPONSE_NORMALIZER -> RequestDeliveryState.SENT;
        };
    }

    private static ConnectorException contract(
            String code,
            String message,
            RequestDeliveryState deliveryState,
            Throwable cause) {
        return failure(ErrorCategory.CONTRACT_VIOLATION, code, message, deliveryState, cause);
    }

    private static ConnectorException failure(
            ErrorCategory category,
            String code,
            String message,
            RequestDeliveryState deliveryState,
            Throwable cause) {
        return new ConnectorException(category, code, message, deliveryState, cause);
    }

    @FunctionalInterface
    private interface PluginCall<T> {
        T invoke() throws ConnectorException;
    }
}
