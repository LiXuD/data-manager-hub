package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict request for refreshing one migration observation window. */
public final class VendorConnectorMigrationObserveRequestDTO implements Serializable {
    private Integer expectedRecordVersion;
    private LocalDateTime endedAt;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public VendorConnectorMigrationObserveRequestDTO() { }

    public VendorConnectorMigrationObserveRequestDTO(Integer expectedRecordVersion, LocalDateTime endedAt) {
        this.expectedRecordVersion = expectedRecordVersion;
        this.endedAt = endedAt;
    }

    public Integer getExpectedRecordVersion() { return expectedRecordVersion; }
    public void setExpectedRecordVersion(Integer expectedRecordVersion) {
        this.expectedRecordVersion = expectedRecordVersion;
    }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    @JsonAnySetter
    public void captureUnknown(String name, Object ignored) { unknownFields.add(name); }

    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields); }
}
