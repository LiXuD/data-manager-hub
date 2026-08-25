package com.dataplatform.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.Objects;

/** Read-only, request-scoped host capabilities exposed to a high-level connector plugin. */
public interface VendorConnectorInvocation {

    String requestId();

    long vendorConfigId();

    JsonNode standardInput();

    JsonNode pluginConfig();

    Deadline deadline();

    CancellationToken cancellationToken();

    int attemptNo();

    IdempotencyContext idempotencyContext();

    SecretResolver secretResolver();

    ObjectCodec objectCodec();

    Clock clock();

    PluginLogger logger();

    PluginMetricRecorder metricRecorder();

    /**
     * Creates the host-side immutable implementation. JSON values are copied on input and access,
     * so plugin code cannot mutate the invocation snapshot.
     */
    static VendorConnectorInvocation immutable(
            String requestId,
            long vendorConfigId,
            JsonNode standardInput,
            JsonNode pluginConfig,
            Deadline deadline,
            CancellationToken cancellationToken,
            int attemptNo,
            IdempotencyContext idempotencyContext,
            SecretResolver secretResolver,
            ObjectCodec objectCodec,
            Clock clock,
            PluginLogger logger,
            PluginMetricRecorder metricRecorder) {
        return new ImmutableVendorConnectorInvocation(requestId, vendorConfigId, standardInput, pluginConfig,
                deadline, cancellationToken, attemptNo, idempotencyContext, secretResolver, objectCodec,
                clock, logger, metricRecorder);
    }
}

final class ImmutableVendorConnectorInvocation implements VendorConnectorInvocation {

    private final String requestId;
    private final long vendorConfigId;
    private final JsonNode standardInput;
    private final JsonNode pluginConfig;
    private final Deadline deadline;
    private final CancellationToken cancellationToken;
    private final int attemptNo;
    private final IdempotencyContext idempotencyContext;
    private final SecretResolver secretResolver;
    private final ObjectCodec objectCodec;
    private final Clock clock;
    private final PluginLogger logger;
    private final PluginMetricRecorder metricRecorder;

    ImmutableVendorConnectorInvocation(
            String requestId,
            long vendorConfigId,
            JsonNode standardInput,
            JsonNode pluginConfig,
            Deadline deadline,
            CancellationToken cancellationToken,
            int attemptNo,
            IdempotencyContext idempotencyContext,
            SecretResolver secretResolver,
            ObjectCodec objectCodec,
            Clock clock,
            PluginLogger logger,
            PluginMetricRecorder metricRecorder) {
        this.requestId = requireText(requestId, "requestId");
        if (vendorConfigId <= 0) {
            throw new IllegalArgumentException("vendorConfigId must be positive");
        }
        this.vendorConfigId = vendorConfigId;
        this.standardInput = Objects.requireNonNull(standardInput, "standardInput").deepCopy();
        this.pluginConfig = Objects.requireNonNull(pluginConfig, "pluginConfig").deepCopy();
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        this.attemptNo = attemptNo;
        this.idempotencyContext = Objects.requireNonNull(idempotencyContext, "idempotencyContext");
        this.secretResolver = Objects.requireNonNull(secretResolver, "secretResolver");
        this.objectCodec = Objects.requireNonNull(objectCodec, "objectCodec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.metricRecorder = Objects.requireNonNull(metricRecorder, "metricRecorder");
    }

    @Override public String requestId() { return requestId; }
    @Override public long vendorConfigId() { return vendorConfigId; }
    @Override public JsonNode standardInput() { return standardInput.deepCopy(); }
    @Override public JsonNode pluginConfig() { return pluginConfig.deepCopy(); }
    @Override public Deadline deadline() { return deadline; }
    @Override public CancellationToken cancellationToken() { return cancellationToken; }
    @Override public int attemptNo() { return attemptNo; }
    @Override public IdempotencyContext idempotencyContext() { return idempotencyContext; }
    @Override public SecretResolver secretResolver() { return secretResolver; }
    @Override public ObjectCodec objectCodec() { return objectCodec; }
    @Override public Clock clock() { return clock; }
    @Override public PluginLogger logger() { return logger; }
    @Override public PluginMetricRecorder metricRecorder() { return metricRecorder; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
