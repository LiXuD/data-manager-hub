package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict request for starting the observation window of one migrated vendor. */
public final class VendorConnectorMigrationStartRequestDTO implements Serializable {
    private Integer expectedRecordVersion;
    private Integer minimumObservationMinutes;
    private Long minimumCalls;
    private Double maximumErrorRate;
    private Long maximumP95DurationMs;
    private Double minimumBillingCoverageRate;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public VendorConnectorMigrationStartRequestDTO() { }

    public VendorConnectorMigrationStartRequestDTO(Integer expectedRecordVersion,
                                                   Integer minimumObservationMinutes,
                                                   Long minimumCalls,
                                                   Double maximumErrorRate,
                                                   Long maximumP95DurationMs,
                                                   Double minimumBillingCoverageRate) {
        this.expectedRecordVersion = expectedRecordVersion;
        this.minimumObservationMinutes = minimumObservationMinutes;
        this.minimumCalls = minimumCalls;
        this.maximumErrorRate = maximumErrorRate;
        this.maximumP95DurationMs = maximumP95DurationMs;
        this.minimumBillingCoverageRate = minimumBillingCoverageRate;
    }

    public Integer getExpectedRecordVersion() { return expectedRecordVersion; }
    public void setExpectedRecordVersion(Integer expectedRecordVersion) {
        this.expectedRecordVersion = expectedRecordVersion;
    }
    public Integer getMinimumObservationMinutes() { return minimumObservationMinutes; }
    public void setMinimumObservationMinutes(Integer value) { this.minimumObservationMinutes = value; }
    public Long getMinimumCalls() { return minimumCalls; }
    public void setMinimumCalls(Long value) { this.minimumCalls = value; }
    public Double getMaximumErrorRate() { return maximumErrorRate; }
    public void setMaximumErrorRate(Double value) { this.maximumErrorRate = value; }
    public Long getMaximumP95DurationMs() { return maximumP95DurationMs; }
    public void setMaximumP95DurationMs(Long value) { this.maximumP95DurationMs = value; }
    public Double getMinimumBillingCoverageRate() { return minimumBillingCoverageRate; }
    public void setMinimumBillingCoverageRate(Double value) { this.minimumBillingCoverageRate = value; }

    @JsonAnySetter
    public void captureUnknown(String name, Object ignored) { unknownFields.add(name); }

    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields); }
}
