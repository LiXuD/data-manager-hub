package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict request for a read-only upgrade preview of the current SIMPLE draft. */
public final class ConnectorSpecUpgradePreviewRequestDTO implements Serializable {
    private Integer expectedDraftVersion;
    private String targetPluginVersion;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public ConnectorSpecUpgradePreviewRequestDTO() { }

    public ConnectorSpecUpgradePreviewRequestDTO(
            Integer expectedDraftVersion, String targetPluginVersion) {
        this.expectedDraftVersion = expectedDraftVersion;
        this.targetPluginVersion = targetPluginVersion;
    }

    public Integer getExpectedDraftVersion() { return expectedDraftVersion; }
    public void setExpectedDraftVersion(Integer expectedDraftVersion) {
        this.expectedDraftVersion = expectedDraftVersion;
    }
    public String getTargetPluginVersion() { return targetPluginVersion; }
    public void setTargetPluginVersion(String targetPluginVersion) {
        this.targetPluginVersion = targetPluginVersion;
    }

    @JsonAnySetter
    public void captureUnknown(String name, Object ignored) { unknownFields.add(name); }

    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields); }
}
