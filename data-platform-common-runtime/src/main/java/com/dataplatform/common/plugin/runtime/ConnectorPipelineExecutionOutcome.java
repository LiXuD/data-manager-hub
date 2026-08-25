package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import java.util.Objects;

/**
 * Host-only execution metadata. Request retry eligibility is deliberately kept
 * outside the plugin SPI result so a plugin cannot choose the platform retry count.
 */
public record ConnectorPipelineExecutionOutcome(
        ConnectorExecutionResult result,
        boolean requestRetryPermitted) {

    public ConnectorPipelineExecutionOutcome {
        Objects.requireNonNull(result, "result");
    }
}
