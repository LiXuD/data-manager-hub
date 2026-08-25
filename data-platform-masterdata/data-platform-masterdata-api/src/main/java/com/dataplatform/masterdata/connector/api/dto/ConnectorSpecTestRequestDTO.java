package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Strict, payload-only input for a controlled SIMPLE connector test. */
public final class ConnectorSpecTestRequestDTO implements Serializable {
    private Map<String, Object> params = new LinkedHashMap<>();
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public ConnectorSpecTestRequestDTO() { }

    public ConnectorSpecTestRequestDTO(Map<String, Object> params) { setParams(params); }

    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public void setParams(Map<String, Object> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    @JsonAnySetter
    public void captureUnknown(String name, Object value) { unknownFields.put(name, value); }

    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields.keySet()); }
}
