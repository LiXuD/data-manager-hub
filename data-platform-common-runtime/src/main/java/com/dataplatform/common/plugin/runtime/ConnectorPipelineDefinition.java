package com.dataplatform.common.plugin.runtime;

import java.util.List;

public record ConnectorPipelineDefinition(String pipelineVersion, String snapshotHash,
                                          String hashAlgorithm, String integrityHash,
                                          List<ConnectorStageDefinition> stages) {
    public static final String UNVERIFIED = "UNVERIFIED";
    public static final String V1_DERIVED = "V1_DERIVED";
    public static final String V2_EMBEDDED = "V2_EMBEDDED";

    public ConnectorPipelineDefinition(String pipelineVersion, String snapshotHash,
                                       List<ConnectorStageDefinition> stages) {
        this(pipelineVersion, snapshotHash, UNVERIFIED, null, stages);
    }

    public ConnectorPipelineDefinition {
        if (pipelineVersion == null || pipelineVersion.isBlank()
                || snapshotHash == null || snapshotHash.isBlank()) {
            throw new IllegalArgumentException("pipelineVersion and snapshotHash are required");
        }
        stages = stages == null ? List.of() : List.copyOf(stages);
        hashAlgorithm = hashAlgorithm == null || hashAlgorithm.isBlank() ? UNVERIFIED : hashAlgorithm;
    }
}
