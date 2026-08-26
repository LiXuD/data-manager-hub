package com.dataplatform.masterdata.connector.compiler;

import com.dataplatform.common.plugin.runtime.ConnectorPipelineDefinition;
import com.dataplatform.common.plugin.runtime.ConnectorStageDefinition;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import java.util.List;

public record ConnectorSpecCompilationResult(
        String canonicalSpec,
        String specHash,
        String snapshotHash,
        String compileHash,
        String compilerVersion,
        long securityVersion,
        ConnectorPipelineDefinition pipeline,
        List<ConnectorStageDefinition> stageDefinitions,
        List<ConnectorPipelineStepDTO> pipelineSteps) {

    public ConnectorSpecCompilationResult {
        stageDefinitions = List.copyOf(stageDefinitions);
        pipelineSteps = List.copyOf(pipelineSteps);
    }
}
