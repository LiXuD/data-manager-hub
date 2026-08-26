package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class DefaultConnectorExchange implements ConnectorExchange {

    private final Map<String, Object> standardParameters;
    private final String vendorCode;
    private final String pipelineVersion;
    private final String snapshotHash;
    private final Instant deadline;
    private final BooleanSupplier cancellationRequested;
    private final Map<String, Object> completedStageOutputs = new LinkedHashMap<>();
    private ConnectorRequest request;
    private ConnectorRawResponse rawResponse;
    private Object parsedResponse;
    private Map<String, Object> normalizedData = Map.of();
    private boolean normalizedDataProduced;
    private BusinessStatus businessStatus = BusinessStatus.NOT_EVALUATED;
    private BillingSignal billingSignal = BillingSignal.UNKNOWN;
    private CacheSignal cacheSignal = CacheSignal.UNKNOWN;
    private RequestDeliveryState deliveryState = RequestDeliveryState.NOT_SENT;
    private StageCapability activeCapability;

    DefaultConnectorExchange(ConnectorExecutionRequest request, ConnectorPipelineDefinition pipeline) {
        this.standardParameters = request.standardParameters();
        this.vendorCode = request.vendorCode();
        this.pipelineVersion = pipeline.pipelineVersion();
        this.snapshotHash = pipeline.snapshotHash();
        this.deadline = request.deadline();
        this.cancellationRequested = request.cancellationRequested();
    }

    void enter(StageCapability capability) { activeCapability = capability; }
    void leave() { activeCapability = null; }
    void transportAttempted() { deliveryState = RequestDeliveryState.MAYBE_SENT; }
    void transportCompleted() { deliveryState = RequestDeliveryState.SENT; }
    void mergeDeliveryState(RequestDeliveryState candidate) {
        if (candidate != null && candidate.ordinal() > deliveryState.ordinal()) {
            deliveryState = candidate;
        }
    }
    RequestDeliveryState deliveryState() { return deliveryState; }
    BusinessStatus businessStatus() { return businessStatus; }
    BillingSignal billingSignal() { return billingSignal; }
    CacheSignal cacheSignal() { return cacheSignal; }
    boolean normalizedDataProduced() { return normalizedDataProduced; }

    @Override public Map<String, Object> standardParameters() { return standardParameters; }
    @Override public String vendorCode() { return vendorCode; }
    @Override public String pipelineVersion() { return pipelineVersion; }
    @Override public String snapshotHash() { return snapshotHash; }
    @Override public Instant deadline() { return deadline; }
    @Override public boolean cancellationRequested() { return cancellationRequested.getAsBoolean(); }
    @Override public ConnectorRequest request() { return request; }
    @Override public ConnectorRawResponse rawResponse() { return rawResponse; }
    @Override public Object parsedResponse() { return parsedResponse; }
    @Override public Map<String, Object> normalizedData() { return normalizedData; }
    @Override public Map<String, Object> completedStageOutputs() { return Map.copyOf(completedStageOutputs); }

    @Override
    public void setRequest(ConnectorRequest request) {
        requireAny(StageCapability.REQUEST_BUILDER, StageCapability.REQUEST_PROCESSOR);
        this.request = java.util.Objects.requireNonNull(request, "request");
    }

    @Override
    public void setRawResponse(ConnectorRawResponse response) {
        requireAny(StageCapability.TRANSPORT, StageCapability.RESPONSE_PROCESSOR);
        this.rawResponse = java.util.Objects.requireNonNull(response, "response");
    }

    @Override
    public void setParsedResponse(Object response) {
        requireAny(StageCapability.RESPONSE_PARSER);
        this.parsedResponse = response;
    }

    @Override
    public void setNormalizedData(Map<String, Object> data) {
        requireAny(StageCapability.RESPONSE_NORMALIZER);
        this.normalizedData = data == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.normalizedDataProduced = true;
    }

    @Override
    public void recordStageOutput(String key, Object value) {
        if (activeCapability == null) {
            throw new IllegalStateException("No stage is active");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("output key cannot be blank");
        }
        completedStageOutputs.put(key, value);
    }

    @Override public void setBusinessStatus(BusinessStatus status) {
        requireAny(StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER);
        businessStatus = java.util.Objects.requireNonNull(status, "status");
    }
    @Override public void setBillingSignal(BillingSignal signal) {
        requireAny(StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER);
        billingSignal = java.util.Objects.requireNonNull(signal, "signal");
    }
    @Override public void setCacheSignal(CacheSignal signal) {
        requireAny(StageCapability.RESPONSE_PARSER, StageCapability.RESPONSE_NORMALIZER);
        cacheSignal = java.util.Objects.requireNonNull(signal, "signal");
    }

    private void requireAny(StageCapability... allowed) {
        for (StageCapability capability : allowed) {
            if (activeCapability == capability) {
                return;
            }
        }
        throw new IllegalStateException("Mutation is not allowed during " + activeCapability);
    }
}
