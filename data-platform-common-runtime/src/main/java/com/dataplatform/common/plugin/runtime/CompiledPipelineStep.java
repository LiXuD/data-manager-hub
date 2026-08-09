package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.RequestScopedConnectorStage;
import com.dataplatform.plugin.spi.StageLifecycle;
import java.util.Set;

final class CompiledPipelineStep {
    private final ConnectorStageDefinition definition;
    private final ConnectorStageFactory factory;
    private final CompiledStageConfig config;
    private final StageLifecycle lifecycle;
    private final ConnectorStage sharedStage;
    private final PluginHandle.Lease lease;
    private final Set<String> secretReferences;

    CompiledPipelineStep(ConnectorStageDefinition definition,
                         ConnectorStageFactory factory,
                         CompiledStageConfig config,
                         StageLifecycle lifecycle,
                         ConnectorStage sharedStage,
                         PluginHandle.Lease lease,
                         Set<String> secretReferences) {
        this.definition = definition;
        this.factory = factory;
        this.config = config;
        this.lifecycle = lifecycle;
        this.sharedStage = sharedStage;
        this.lease = lease;
        this.secretReferences = secretReferences == null ? null : Set.copyOf(secretReferences);
    }

    ConnectorStageDefinition definition() { return definition; }
    PluginHandle.Lease lease() { return lease; }
    Set<String> secretReferences() { return secretReferences; }

    ExecutionStage openStage() throws Exception {
        if (lifecycle == StageLifecycle.SHARED) {
            return new ExecutionStage(sharedStage, false, lease.handle());
        }
        ConnectorStage created = lease.handle().withContextClassLoader(() -> factory.create(config));
        try {
            validate(created);
            if (!(created instanceof RequestScopedConnectorStage)
                    && !(created instanceof AutoCloseable)) {
                throw new IllegalArgumentException(
                        "REQUEST_SCOPED factory must return an AutoCloseable stage: " + definition.stageKey());
            }
            return new ExecutionStage(created, true, lease.handle());
        } catch (RuntimeException exception) {
            closeStage(created, lease.handle());
            throw exception;
        }
    }

    void closePipelineResources() {
        try {
            if (sharedStage != null) {
                closeStage(sharedStage, lease.handle());
            }
        } finally {
            lease.close();
        }
    }

    private void validate(ConnectorStage stage) {
        if (stage == null || stage.capability() != definition.capability()) {
            throw new IllegalArgumentException("Factory returned an invalid stage: " + definition.stageKey());
        }
    }

    private static void closeStage(ConnectorStage stage, PluginHandle handle) {
        if (!(stage instanceof AutoCloseable closeable)) return;
        try {
            handle.withContextClassLoader(() -> {
                closeable.close();
                return null;
            });
        } catch (Exception ignored) {
            // Stage cleanup is best effort; plugin handle and other stages must still be released.
        }
    }

    static final class ExecutionStage implements AutoCloseable {
        private final ConnectorStage stage;
        private final boolean closeAfterRequest;
        private final PluginHandle handle;
        private final AtomicClose once = new AtomicClose();

        private ExecutionStage(ConnectorStage stage, boolean closeAfterRequest, PluginHandle handle) {
            this.stage = stage;
            this.closeAfterRequest = closeAfterRequest;
            this.handle = handle;
        }

        ConnectorStage stage() { return stage; }

        @Override
        public void close() {
            if (closeAfterRequest && once.close()) {
                closeStage(stage, handle);
            }
        }
    }

    private static final class AtomicClose {
        private final java.util.concurrent.atomic.AtomicBoolean closed =
                new java.util.concurrent.atomic.AtomicBoolean();

        boolean close() { return closed.compareAndSet(false, true); }
    }
}
