package com.dataplatform.common.plugin.runtime;

import java.util.List;

public record ConnectorPipelineDefinition(String pipelineVersion, String snapshotHash,
                                          List<ConnectorStageDefinition> stages) {
    public ConnectorPipelineDefinition {
        if (pipelineVersion == null || pipelineVersion.isBlank()
                || snapshotHash == null || snapshotHash.isBlank()) {
            throw new IllegalArgumentException("pipelineVersion and snapshotHash are required");
        }
        stages = stages == null ? List.of() : List.copyOf(stages);
    }
}
