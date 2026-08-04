package com.dataplatform.common.plugin.runtime;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PluginRuntimeConcurrencyTest {

    @Test
    void retirementWaitsForAllConcurrentLeasesAndClosesExactlyOnce() throws Exception {
        CountingPlugin plugin = new CountingPlugin();
        plugin.initialize(TestPluginContexts.context());
        PluginHandle handle = PluginHandle.builtIn(plugin);
        List<PluginHandle.Lease> leases = java.util.Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                futures.add(executor.submit(() -> leases.add(handle.acquire())));
            }
            for (var future : futures) future.get();
        }
        assertEquals(200, handle.referenceCount());

        handle.retire();
        assertEquals(PluginHandleState.RETIRED, handle.state());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (PluginHandle.Lease lease : leases) executor.submit(lease::close);
        }

        assertEquals(PluginHandleState.CLOSED, handle.state());
        assertEquals(1, plugin.closeCount.get());
        assertThrows(IllegalStateException.class, handle::acquire);
    }

    private static final class CountingPlugin implements ConnectorPlugin {
        private final AtomicInteger closeCount = new AtomicInteger();
        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor("counting-plugin", "1.0.0", "1.0", "Counting", "test",
                    Set.of(StageCapability.REQUEST_BUILDER));
        }
        @Override public void initialize(PluginContext context) { }
        @Override public List<ConnectorStageFactory> stageFactories() { return List.of(new Factory()); }
        @Override public PluginSelfTestResult selfTest() { return PluginSelfTestResult.success(); }
        @Override public void close() { closeCount.incrementAndGet(); }
    }

    private static final class Factory implements ConnectorStageFactory {
        @Override public StageCapability capability() { return StageCapability.REQUEST_BUILDER; }
        @Override public void validate(JsonNode config, PluginValidationContext context) { }
        @Override public ConnectorStage create(CompiledStageConfig config) {
            return new ConnectorStage() {
                @Override public StageCapability capability() { return StageCapability.REQUEST_BUILDER; }
                @Override public void execute(ConnectorExchange exchange, StageExecutionContext context) { }
            };
        }
    }
}
