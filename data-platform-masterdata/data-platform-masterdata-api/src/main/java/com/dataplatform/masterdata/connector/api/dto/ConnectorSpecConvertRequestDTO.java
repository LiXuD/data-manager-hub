package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict CAS input for converting the current Legacy draft to a SIMPLE draft. */
public final class ConnectorSpecConvertRequestDTO implements Serializable {
    private Integer expectedDraftVersion;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public ConnectorSpecConvertRequestDTO() { }

    public ConnectorSpecConvertRequestDTO(Integer expectedDraftVersion) {
        this.expectedDraftVersion = expectedDraftVersion;
    }

    public Integer getExpectedDraftVersion() { return expectedDraftVersion; }
    public void setExpectedDraftVersion(Integer expectedDraftVersion) {
        this.expectedDraftVersion = expectedDraftVersion;
    }

    @JsonAnySetter
    public void captureUnknown(String name, Object ignored) { unknownFields.add(name); }

    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields); }
}
