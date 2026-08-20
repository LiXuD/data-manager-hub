package com.dataplatform.plugin.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VendorConnectorSdkContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));
    private static final PluginLogger LOGGER = new PluginLogger() {
        @Override public void debug(String event, Map<String, ?> safeFields) { }
        @Override public void info(String event, Map<String, ?> safeFields) { }
        @Override public void warn(String event, Map<String, ?> safeFields) { }
        @Override public void error(String event, Map<String, ?> safeFields) { }
    };
    private static final PluginMetricRecorder METRICS = new PluginMetricRecorder() {
        @Override public void increment(String metric, Map<String, String> lowCardinalityTags) { }
        @Override public void recordDuration(
                String metric, Duration duration, Map<String, String> lowCardinalityTags) { }
    };
    private static final ObjectCodec CODEC = new ObjectCodec() {
        @Override public JsonNode toTree(Object value) { return MAPPER.valueToTree(value); }
        @Override public byte[] write(Object value) { return new byte[0]; }
        @Override public <T> T read(byte[] value, Class<T> type) { throw new UnsupportedOperationException(); }
    };

    @Test
    void factorySetsFollowSingleMultiAndOutputOwnershipContracts() {
        TestPlugin single = new TestPlugin(ConnectorTransportMode.HOST_SINGLE_HTTP,
                ConnectorOutputMode.HOST_MAPPING,
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.REQUEST_PROCESSOR,
                        StageCapability.RESPONSE_PROCESSOR, StageCapability.RESPONSE_PARSER));
        assertEquals(List.of(StageCapability.REQUEST_BUILDER, StageCapability.REQUEST_PROCESSOR,
                        StageCapability.RESPONSE_PROCESSOR, StageCapability.RESPONSE_PARSER),
                capabilities(single));
        assertEquals(ConnectorAuthoringModel.SIMPLE_CONNECTOR, single.authoringModel());

        TestPlugin multi = new TestPlugin(ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP,
                ConnectorOutputMode.HOST_MAPPING,
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                        StageCapability.RESPONSE_PARSER));
        assertEquals(List.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                StageCapability.RESPONSE_PARSER), capabilities(multi));
        assertEquals(1, capabilities(multi).stream()
                .filter(StageCapability.TRANSPORT::equals).count());

        NormalizingPlugin normalized = new NormalizingPlugin();
        assertEquals(List.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER,
                StageCapability.RESPONSE_NORMALIZER), capabilities(normalized));

        TestPlugin invalidHostMapping = new TestPlugin(ConnectorTransportMode.HOST_SINGLE_HTTP,
                ConnectorOutputMode.HOST_MAPPING,
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER,
                        StageCapability.RESPONSE_NORMALIZER));
        assertThrows(IllegalStateException.class, invalidHostMapping::stageFactories);
    }

    @Test
    void adaptersAloneMutateExchangeAndManagedTransportUsesBoundedSession() throws Exception {
        ObjectNode config = MAPPER.createObjectNode().put("endpoint", "https://vendor.example/api");
        TestPlugin plugin = new TestPlugin(ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP,
                ConnectorOutputMode.HOST_MAPPING,
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                        StageCapability.RESPONSE_PARSER));
        TestDeadline deadline = new TestDeadline(NOW.plusSeconds(30), CLOCK);
        TestCancellationToken cancellation = new TestCancellationToken(false);
        CountingSession session = new CountingSession(deadline, cancellation);
        TestHostContext context = hostContext(config, deadline, cancellation, session);
        TestExchange exchange = new TestExchange(deadline.expiresAt());

        execute(plugin, StageCapability.REQUEST_BUILDER, config, exchange, context);
        execute(plugin, StageCapability.TRANSPORT, config, exchange, context);
        execute(plugin, StageCapability.RESPONSE_PARSER, config, exchange, context);

        assertEquals(1, plugin.buildCalls.get());
        assertEquals(1, session.executeCalls.get());
        assertEquals(1, exchange.requestMutations);
        assertEquals(1, exchange.responseMutations);
        assertEquals(1, exchange.parsedMutations);
        assertEquals(BusinessStatus.SUCCESS, exchange.businessStatus);
        assertEquals(BillingSignal.ELIGIBLE, exchange.billingSignal);
        assertEquals(CacheSignal.CACHEABLE, exchange.cacheSignal);
        VendorParseResult parsed = assertInstanceOf(VendorParseResult.class, exchange.parsedResponse);
        assertEquals("acme", parsed.data().get("name"));
        assertEquals(0, exchange.normalizedMutations);
    }

    @Test
    @SuppressWarnings("unchecked")
    void parseResultIsImmutableAndRejectsInvalidStatusSignalAndMessageCombinations() {
        List<Object> tags = new ArrayList<>(List.of("original"));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("tags", tags);
        VendorParseResult result = VendorParseResult.success(source, "OK",
                BillingSignal.ELIGIBLE, CacheSignal.CACHEABLE, "safe");

        tags.add("mutated");
        assertEquals(List.of("original"), result.data().get("tags"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) result.data().get("tags")).add("blocked"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.data().put("new", "blocked"));

        assertThrows(IllegalArgumentException.class, () -> VendorParseResult.success(Map.of(), null,
                BillingSignal.INELIGIBLE, CacheSignal.NOT_CACHEABLE, null));
        assertThrows(IllegalArgumentException.class, () -> VendorParseResult.rejected(Map.of(), "DENIED",
                BillingSignal.ELIGIBLE, CacheSignal.NOT_CACHEABLE, "denied"));
        assertThrows(IllegalArgumentException.class, () -> VendorParseResult.unknown(Map.of(), null,
                BillingSignal.UNKNOWN, CacheSignal.NOT_CACHEABLE, null));
        assertThrows(IllegalArgumentException.class,
                () -> VendorParseResult.success(Map.of(), null, BillingSignal.ELIGIBLE,
                        CacheSignal.CACHEABLE, "x".repeat(513)));
        assertEquals(128, VendorParseResult.success(Map.of(), "x".repeat(256),
                BillingSignal.ELIGIBLE, CacheSignal.CACHEABLE, null)
                .vendorBusinessCode().length());
    }

    @Test
    void invocationCopiesJsonAndCarriesReadOnlyAttemptDeadlineAndCancellationFacts() throws Exception {
        ObjectNode standardInput = MAPPER.createObjectNode().put("company", "before");
        ObjectNode pluginConfig = MAPPER.createObjectNode().put("endpoint", "https://vendor.example");
        TestDeadline deadline = new TestDeadline(NOW.plusSeconds(20), CLOCK);
        TestCancellationToken cancellation = new TestCancellationToken(false);
        VendorConnectorInvocation invocation = invocation(standardInput, pluginConfig, deadline, cancellation);

        standardInput.put("company", "after-input-mutation");
        pluginConfig.put("endpoint", "https://mutated.example");
        ObjectNode exposedInput = (ObjectNode) invocation.standardInput();
        exposedInput.put("company", "after-accessor-mutation");

        assertEquals("before", invocation.standardInput().path("company").asText());
        assertEquals("https://vendor.example", invocation.pluginConfig().path("endpoint").asText());
        assertNotSame(invocation.standardInput(), invocation.standardInput());
        assertEquals(1, invocation.attemptNo());
        assertEquals(Duration.ofSeconds(20), invocation.deadline().remaining());
        assertFalse(invocation.deadline().isExpired());
        assertFalse(invocation.cancellationToken().isCancelled());

        cancellation.cancelled.set(true);
        ConnectorException cancelled = assertThrows(ConnectorException.class,
                invocation.cancellationToken()::throwIfCancelled);
        assertEquals("REQUEST_CANCELLED", cancelled.errorCode());
    }

    @Test
    void cancellationAndDeadlineStopExecutionBeforePluginCodeRuns() throws Exception {
        ObjectNode config = MAPPER.createObjectNode();
        TestPlugin cancelledPlugin = simplePlugin();
        TestDeadline activeDeadline = new TestDeadline(NOW.plusSeconds(10), CLOCK);
        TestCancellationToken cancelledToken = new TestCancellationToken(true);
        ConnectorException cancelled = assertThrows(ConnectorException.class,
                () -> execute(cancelledPlugin, StageCapability.REQUEST_BUILDER, config,
                        new TestExchange(activeDeadline.expiresAt()),
                        hostContext(config, activeDeadline, cancelledToken, null)));
        assertEquals("REQUEST_CANCELLED", cancelled.errorCode());
        assertEquals(RequestDeliveryState.NOT_SENT, cancelled.deliveryState());
        assertEquals(0, cancelledPlugin.buildCalls.get());

        TestPlugin expiredPlugin = simplePlugin();
        TestDeadline expiredDeadline = new TestDeadline(NOW, CLOCK);
        ConnectorException expired = assertThrows(ConnectorException.class,
                () -> execute(expiredPlugin, StageCapability.REQUEST_BUILDER, config,
                        new TestExchange(expiredDeadline.expiresAt()),
                        hostContext(config, expiredDeadline, new TestCancellationToken(false), null)));
        assertEquals(ErrorCategory.TRANSPORT_TIMEOUT, expired.category());
        assertEquals("EXECUTION_DEADLINE_EXCEEDED", expired.errorCode());
        assertEquals(0, expiredPlugin.buildCalls.get());
    }

    @Test
    void nullReturnsAndOversizedPluginErrorsFailClosed() throws Exception {
        ObjectNode config = MAPPER.createObjectNode();
        TestDeadline deadline = new TestDeadline(NOW.plusSeconds(10), CLOCK);
        TestCancellationToken cancellation = new TestCancellationToken(false);

        TestPlugin nullBuilder = simplePlugin();
        nullBuilder.returnNullRequest = true;
        ConnectorException nullFailure = assertThrows(ConnectorException.class,
                () -> execute(nullBuilder, StageCapability.REQUEST_BUILDER, config,
                        new TestExchange(deadline.expiresAt()),
                        hostContext(config, deadline, cancellation, null)));
        assertEquals(ErrorCategory.CONTRACT_VIOLATION, nullFailure.category());
        assertEquals("REQUEST_BUILDER_RETURNED_NULL", nullFailure.errorCode());

        TestPlugin unsafeFailure = simplePlugin();
        unsafeFailure.buildFailure = new ConnectorException(ErrorCategory.REQUEST_BUILD_ERROR,
                "UNSAFE", "x".repeat(513), RequestDeliveryState.NOT_SENT);
        ConnectorException wrapped = assertThrows(ConnectorException.class,
                () -> execute(unsafeFailure, StageCapability.REQUEST_BUILDER, config,
                        new TestExchange(deadline.expiresAt()),
                        hostContext(config, deadline, cancellation, null)));
        assertEquals(ErrorCategory.CONTRACT_VIOLATION, wrapped.category());
        assertEquals("PLUGIN_ERROR_CONTRACT_INVALID", wrapped.errorCode());
        assertTrue(wrapped.safeMessage().length() <= VendorParseResult.MAX_SAFE_MESSAGE_LENGTH);
    }

    private static TestPlugin simplePlugin() {
        return new TestPlugin(ConnectorTransportMode.HOST_SINGLE_HTTP,
                ConnectorOutputMode.HOST_MAPPING,
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER));
    }

    private static List<StageCapability> capabilities(AbstractVendorConnectorPlugin plugin) {
        return plugin.stageFactories().stream().map(ConnectorStageFactory::capability).toList();
    }

    private static void execute(
            AbstractVendorConnectorPlugin plugin,
            StageCapability capability,
            JsonNode config,
            TestExchange exchange,
            TestHostContext context) throws ConnectorException {
        ConnectorStageFactory factory = plugin.stageFactories().stream()
                .filter(candidate -> candidate.capability() == capability)
                .findFirst().orElseThrow();
        ConnectorStage stage = factory.create(new CompiledStageConfig(
                "connector." + capability.name().toLowerCase(), "fixture-vendor", "2.0.0",
                capability, config, "sha256:fixture"));
        stage.execute(exchange, context);
    }

    private static TestHostContext hostContext(
            JsonNode config,
            TestDeadline deadline,
            TestCancellationToken cancellation,
            ManagedTransportSession session) {
        VendorConnectorInvocation invocation = invocation(
                MAPPER.createObjectNode().put("company", "acme"), config, deadline, cancellation);
        return new TestHostContext(invocation, session, deadline, cancellation);
    }

    private static VendorConnectorInvocation invocation(
            JsonNode standardInput,
            JsonNode pluginConfig,
            Deadline deadline,
            CancellationToken cancellation) {
        IdempotencyContext idempotency = new IdempotencyContext() {
            @Override public IdempotencyPolicy policy() { return IdempotencyPolicy.IDEMPOTENT; }
            @Override public String idempotencyKey() { return null; }
            @Override public boolean retryPermitted() { return true; }
        };
        return VendorConnectorInvocation.immutable("request-1", 42L, standardInput, pluginConfig,
                deadline, cancellation, 1, idempotency,
                ref -> new SecretValue("secret".toCharArray()), CODEC, CLOCK, LOGGER, METRICS);
    }

    private static ConnectorRequest request() {
        return new ConnectorRequest("GET", URI.create("https://vendor.example/api"), Map.of(), Map.of(),
                "application/json", new byte[0], Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(3), IdempotencyPolicy.IDEMPOTENT, null, 1024);
    }

    private static ConnectorRawResponse response() {
        return new ConnectorRawResponse(200, Map.of(), "{}".getBytes(), Duration.ofMillis(20),
                URI.create("https://vendor.example/api"), 10, 2);
    }

    private static class TestPlugin extends AbstractVendorConnectorPlugin {

        private final Set<StageCapability> capabilities;
        private final AtomicInteger buildCalls = new AtomicInteger();
        private boolean returnNullRequest;
        private ConnectorException buildFailure;

        TestPlugin(
                ConnectorTransportMode transportMode,
                ConnectorOutputMode outputMode,
                Set<StageCapability> capabilities) {
            super(transportMode, outputMode);
            this.capabilities = Set.copyOf(capabilities);
        }

        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor("fixture-vendor", "2.0.0", "1.1",
                    "Fixture Vendor", "test", capabilities);
        }

        @Override
        protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation)
                throws ConnectorException {
            buildCalls.incrementAndGet();
            if (buildFailure != null) {
                throw buildFailure;
            }
            return returnNullRequest ? null : request();
        }

        @Override
        protected VendorParseResult parseResponse(
                VendorConnectorInvocation invocation,
                ConnectorRawResponse response) {
            return VendorParseResult.success(Map.of("name", "acme"));
        }
    }

    private static final class NormalizingPlugin extends TestPlugin {

        private NormalizingPlugin() {
            super(ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.PLUGIN_NORMALIZED,
                    Set.of(StageCapability.REQUEST_BUILDER, StageCapability.RESPONSE_PARSER,
                            StageCapability.RESPONSE_NORMALIZER));
        }

        @Override
        protected Map<String, Object> normalizeResponse(
                VendorConnectorInvocation invocation,
                VendorParseResult parsed) {
            return parsed.data();
        }
    }

    private record TestDeadline(Instant expiresAt, Clock clock) implements Deadline {

        @Override
        public Duration remaining() {
            Duration remaining = Duration.between(clock.instant(), expiresAt);
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }

        @Override
        public boolean isExpired() {
            return !clock.instant().isBefore(expiresAt);
        }
    }

    private static final class TestCancellationToken implements CancellationToken {

        private final AtomicBoolean cancelled;

        private TestCancellationToken(boolean cancelled) {
            this.cancelled = new AtomicBoolean(cancelled);
        }

        @Override public boolean isCancelled() { return cancelled.get(); }

        @Override
        public void throwIfCancelled() throws ConnectorException {
            if (isCancelled()) {
                throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR, "REQUEST_CANCELLED",
                        "Connector execution was cancelled", RequestDeliveryState.NOT_SENT);
            }
        }
    }

    private record TestHostContext(
            VendorConnectorInvocation vendorInvocation,
            ManagedTransportSession managedTransportSession,
            TestDeadline testDeadline,
            TestCancellationToken cancellationToken) implements VendorConnectorStageAdapters.HostContext {

        @Override public Clock clock() { return CLOCK; }
        @Override public Instant deadline() { return testDeadline.expiresAt(); }
        @Override public boolean cancellationRequested() { return cancellationToken.isCancelled(); }
        @Override public PluginLogger logger() { return LOGGER; }
        @Override public PluginMetricRecorder metrics() { return METRICS; }
    }

    private static final class CountingSession implements ManagedTransportSession {

        private final Deadline deadline;
        private final CancellationToken cancellationToken;
        private final AtomicInteger executeCalls = new AtomicInteger();
        private int remainingCalls = 5;

        private CountingSession(Deadline deadline, CancellationToken cancellationToken) {
            this.deadline = deadline;
            this.cancellationToken = cancellationToken;
        }

        @Override
        public ConnectorRawResponse execute(ConnectorRequest request) {
            executeCalls.incrementAndGet();
            remainingCalls--;
            return response();
        }

        @Override public Deadline deadline() { return deadline; }
        @Override public CancellationToken cancellationToken() { return cancellationToken; }
        @Override public int remainingCalls() { return remainingCalls; }
    }

    private static final class TestExchange implements ConnectorExchange {

        private final Instant deadline;
        private ConnectorRequest request;
        private ConnectorRawResponse rawResponse;
        private Object parsedResponse;
        private Map<String, Object> normalizedData = Map.of();
        private BusinessStatus businessStatus = BusinessStatus.NOT_EVALUATED;
        private BillingSignal billingSignal = BillingSignal.UNKNOWN;
        private CacheSignal cacheSignal = CacheSignal.UNKNOWN;
        private int requestMutations;
        private int responseMutations;
        private int parsedMutations;
        private int normalizedMutations;

        private TestExchange(Instant deadline) {
            this.deadline = deadline;
        }

        @Override public Map<String, Object> standardParameters() { return Map.of("company", "acme"); }
        @Override public String vendorCode() { return "fixture"; }
        @Override public String pipelineVersion() { return "1"; }
        @Override public String snapshotHash() { return "snapshot"; }
        @Override public Instant deadline() { return deadline; }
        @Override public boolean cancellationRequested() { return false; }
        @Override public ConnectorRequest request() { return request; }
        @Override public ConnectorRawResponse rawResponse() { return rawResponse; }
        @Override public Object parsedResponse() { return parsedResponse; }
        @Override public Map<String, Object> normalizedData() { return normalizedData; }
        @Override public Map<String, Object> completedStageOutputs() { return Map.of(); }

        @Override public void setRequest(ConnectorRequest request) {
            requestMutations++;
            this.request = request;
        }
        @Override public void setRawResponse(ConnectorRawResponse response) {
            responseMutations++;
            this.rawResponse = response;
        }
        @Override public void setParsedResponse(Object response) {
            parsedMutations++;
            this.parsedResponse = response;
        }
        @Override public void setNormalizedData(Map<String, Object> data) {
            normalizedMutations++;
            this.normalizedData = Map.copyOf(data);
        }
        @Override public void recordStageOutput(String key, Object value) { }
        @Override public void setBusinessStatus(BusinessStatus status) { this.businessStatus = status; }
        @Override public void setBillingSignal(BillingSignal signal) { this.billingSignal = signal; }
        @Override public void setCacheSignal(CacheSignal signal) { this.cacheSignal = signal; }
    }
}
