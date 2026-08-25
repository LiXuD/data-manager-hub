package com.dataplatform.plugin.spi;

import java.time.Instant;
import java.util.Map;

/**
 * Host-owned mutable exchange. Implementations enforce which mutation is valid
 * for the currently executing stage capability.
 */
public interface ConnectorExchange {

    Map<String, Object> standardParameters();

    String vendorCode();

    String pipelineVersion();

    String snapshotHash();

    Instant deadline();

    boolean cancellationRequested();

    ConnectorRequest request();

    ConnectorRawResponse rawResponse();

    Object parsedResponse();

    Map<String, Object> normalizedData();

    Map<String, Object> completedStageOutputs();

    void setRequest(ConnectorRequest request);

    void setRawResponse(ConnectorRawResponse response);

    void setParsedResponse(Object response);

    void setNormalizedData(Map<String, Object> data);

    void recordStageOutput(String key, Object value);

    void setBusinessStatus(BusinessStatus status);

    void setBillingSignal(BillingSignal signal);

    void setCacheSignal(CacheSignal signal);
}
