package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict CAS wrapper for saving a SIMPLE connector draft. */
public final class ConnectorSpecSaveRequestDTO implements Serializable {
    private Integer expectedDraftVersion;
    private ConnectorSpecDTO connectorSpec;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public ConnectorSpecSaveRequestDTO() { }

    public ConnectorSpecSaveRequestDTO(Integer expectedDraftVersion, ConnectorSpecDTO connectorSpec) {
        this.expectedDraftVersion = expectedDraftVersion;
        this.connectorSpec = connectorSpec;
    }

    public Integer getExpectedDraftVersion() { return expectedDraftVersion; }
    public void setExpectedDraftVersion(Integer expectedDraftVersion) {
        this.expectedDraftVersion = expectedDraftVersion;
    }
    public ConnectorSpecDTO getConnectorSpec() { return connectorSpec; }
    public void setConnectorSpec(ConnectorSpecDTO connectorSpec) { this.connectorSpec = connectorSpec; }

    @JsonAnySetter
    public void captureUnknown(String name, Object ignored) { unknownFields.add(name); }

    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields); }
}
