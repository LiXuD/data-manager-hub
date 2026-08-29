package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict optimistic-lock request for completing or rolling back migration. */
public final class VendorConnectorMigrationActionRequestDTO implements Serializable {
    private Integer expectedRecordVersion;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public VendorConnectorMigrationActionRequestDTO() { }

    public VendorConnectorMigrationActionRequestDTO(Integer expectedRecordVersion) {
        this.expectedRecordVersion = expectedRecordVersion;
    }

    public Integer getExpectedRecordVersion() { return expectedRecordVersion; }
    public void setExpectedRecordVersion(Integer expectedRecordVersion) {
        this.expectedRecordVersion = expectedRecordVersion;
    }

    @JsonAnySetter
    public void captureUnknown(String name, Object ignored) { unknownFields.add(name); }

    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields); }
}
