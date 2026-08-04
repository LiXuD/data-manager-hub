package com.dataplatform.common.plugin.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CompiledConnectorPipeline implements AutoCloseable {

    private final ConnectorPipelineDefinition definition;
    private final List<CompiledPipelineStep> steps;
    private final AtomicBoolean closed = new AtomicBoolean();

    CompiledConnectorPipeline(ConnectorPipelineDefinition definition, List<CompiledPipelineStep> steps) {
        this.definition = definition;
        this.steps = List.copyOf(steps);
    }

    public ConnectorPipelineDefinition definition() { return definition; }
    List<CompiledPipelineStep> steps() {
        if (closed.get()) {
            throw new IllegalStateException("Compiled pipeline is closed");
        }
        return steps;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            steps.forEach(step -> step.lease().close());
        }
    }
}
