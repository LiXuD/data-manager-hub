package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import java.io.Closeable;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginHandleClassLoaderGaugeTest {

    @Test
    void countsOnlyRealIsolatedClassLoadersAndReturnsToBaselineAfterRelease() {
        int baseline = PluginHandle.isolatedClassLoaderCount();
        CloseableLoader loader = new CloseableLoader();
        PluginHandle isolated = new PluginHandle(new EmptyPlugin("external"), loader, loader);
        assertEquals(baseline + 1, PluginHandle.isolatedClassLoaderCount());

        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        registry.register(isolated);
        registry.register(PluginHandle.builtIn(new EmptyPlugin("builtin")));
        assertEquals(baseline + 1, PluginHandle.isolatedClassLoaderCount());

        registry.release("external", "1.0.0");
        assertEquals(baseline, PluginHandle.isolatedClassLoaderCount());
        registry.close();
    }

    private static final class CloseableLoader extends ClassLoader implements Closeable {
        @Override public void close() { }
    }

    private static final class EmptyPlugin implements ConnectorPlugin {
        private final String id;
        private EmptyPlugin(String id) { this.id = id; }
        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(id, "1.0.0", "1.0", id, "test",
                    Set.of(StageCapability.TRANSPORT));
        }
        @Override public void initialize(PluginContext context) { }
        @Override public List<ConnectorStageFactory> stageFactories() { return List.of(new EmptyFactory()); }
        @Override public PluginSelfTestResult selfTest() { return PluginSelfTestResult.success(); }
        @Override public void close() { }
    }

    private static final class EmptyFactory implements ConnectorStageFactory {
        @Override public StageCapability capability() { return StageCapability.TRANSPORT; }
        @Override public void validate(JsonNode config, PluginValidationContext context) { }
        @Override public ConnectorStage create(CompiledStageConfig config) {
            return new ConnectorStage() {
                @Override public StageCapability capability() { return StageCapability.TRANSPORT; }
                @Override public void execute(ConnectorExchange exchange, StageExecutionContext context) { }
            };
        }
    }
}
