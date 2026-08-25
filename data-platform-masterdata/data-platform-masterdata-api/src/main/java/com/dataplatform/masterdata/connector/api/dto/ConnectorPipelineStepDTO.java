package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.Map;

public record ConnectorPipelineStepDTO(
        String stageKey,
        String capability,
        String pluginId,
        String pluginVersion,
        Integer order,
        Boolean enabled,
        Map<String, Object> config,
        String configHash,
        String artifactSha256,
        String manifestHash,
        String schemaHash) implements Serializable {

    /** Source-compatible constructor for pre-V046 callers; Masterdata enriches hashes before persistence. */
    public ConnectorPipelineStepDTO(String stageKey, String capability, String pluginId,
                                    String pluginVersion, Integer order, Boolean enabled,
                                    Map<String, Object> config, String configHash) {
        this(stageKey, capability, pluginId, pluginVersion, order, enabled, config, configHash,
                null, null, null);
    }
}
