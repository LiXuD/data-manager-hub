package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.ConnectorStage;

record CompiledPipelineStep(ConnectorStageDefinition definition, ConnectorStage stage,
                            PluginHandle.Lease lease) {
}
