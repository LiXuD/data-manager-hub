package com.dataplatform.common.plugin.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class CompiledConnectorPipeline implements AutoCloseable {

    private final ConnectorPipelineDefinition definition;
    private final List<CompiledPipelineStep> steps;
    private final AtomicBoolean retired = new AtomicBoolean();
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final AtomicInteger requestReferences = new AtomicInteger();

    CompiledConnectorPipeline(ConnectorPipelineDefinition definition, List<CompiledPipelineStep> steps) {
        this.definition = definition;
        this.steps = List.copyOf(steps);
    }

    public ConnectorPipelineDefinition definition() { return definition; }

    public RequestLease acquire() {
        while (true) {
            if (retired.get()) {
                throw new IllegalStateException("Compiled pipeline is retired");
            }
            int current = requestReferences.get();
            if (requestReferences.compareAndSet(current, current + 1)) {
                if (retired.get()) {
                    release();
                    throw new IllegalStateException("Compiled pipeline retired during acquisition");
                }
                return new RequestLease(this);
            }
        }
    }

    List<CompiledPipelineStep> steps() {
        if (destroyed.get()) {
            throw new IllegalStateException("Compiled pipeline is destroyed");
        }
        return steps;
    }

    public int activeRequests() { return requestReferences.get(); }
    public boolean retired() { return retired.get(); }
    public boolean destroyed() { return destroyed.get(); }

    @Override
    public void close() {
        retired.set(true);
        destroyIfUnused();
    }

    private void release() {
        int remaining = requestReferences.decrementAndGet();
        if (remaining < 0) {
            requestReferences.incrementAndGet();
            throw new IllegalStateException("Compiled pipeline request reference underflow");
        }
        if (remaining == 0) {
            destroyIfUnused();
        }
    }

    private void destroyIfUnused() {
        if (retired.get() && requestReferences.get() == 0
                && destroyed.compareAndSet(false, true)) {
            for (CompiledPipelineStep step : steps) {
                try {
                    step.closePipelineResources();
                } catch (RuntimeException ignored) {
                    // One faulty stage must not pin the remaining plugin handles.
                }
            }
        }
    }

    public static final class RequestLease implements AutoCloseable {
        private final CompiledConnectorPipeline pipeline;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RequestLease(CompiledConnectorPipeline pipeline) {
            this.pipeline = pipeline;
        }

        public CompiledConnectorPipeline pipeline() {
            if (closed.get()) throw new IllegalStateException("Compiled pipeline lease is closed");
            return pipeline;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                pipeline.release();
            }
        }
    }
}
