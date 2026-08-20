package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

/** Redacted, read-only execution plan. Stage configuration and SecretRefs are never returned. */
public record ConnectorExecutionPlanDTO(
        Long connectorVersionId,
        Integer version,
        Integer draftVersion,
        String authoringMode,
        String snapshotHashPrefix,
        List<Stage> stages) implements Serializable {

    public ConnectorExecutionPlanDTO {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public record Stage(
            String stageKey,
            String capability,
            String pluginId,
            String pluginVersion,
            Integer order,
            String configHash,
            String artifactHashPrefix,
            String manifestHashPrefix,
            String schemaHashPrefix,
            String source) implements Serializable { }
}
