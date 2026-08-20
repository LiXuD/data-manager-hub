package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict optimistic-lock input for creating a new active version from immutable history. */
public final class ConnectorSpecRollbackRequestDTO implements Serializable {
    private Integer expectedConnectorVersion;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public ConnectorSpecRollbackRequestDTO() { }

    public ConnectorSpecRollbackRequestDTO(Integer expectedConnectorVersion) {
        this.expectedConnectorVersion = expectedConnectorVersion;
    }

    public Integer getExpectedConnectorVersion() { return expectedConnectorVersion; }
    public void setExpectedConnectorVersion(Integer expectedConnectorVersion) {
        this.expectedConnectorVersion = expectedConnectorVersion;
    }

    @JsonAnySetter
    public void captureUnknown(String name, Object ignored) { unknownFields.add(name); }

    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields); }
}
