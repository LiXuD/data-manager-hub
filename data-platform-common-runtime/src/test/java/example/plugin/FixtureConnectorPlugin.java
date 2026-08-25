package example.plugin;

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
import java.util.List;
import java.util.Set;

public final class FixtureConnectorPlugin implements ConnectorPlugin {
    private boolean initialized;

    @Override public PluginDescriptor descriptor() {
        return new PluginDescriptor("fixture-plugin", "1.2.0", "1.0", "Fixture", "test",
                Set.of(StageCapability.RESPONSE_PARSER));
    }
    @Override public void initialize(PluginContext context) { initialized = true; }
    @Override public List<ConnectorStageFactory> stageFactories() { return List.of(new Factory()); }
    @Override public PluginSelfTestResult selfTest() {
        return initialized ? PluginSelfTestResult.success() : PluginSelfTestResult.failure("not initialized");
    }
    @Override public void close() { initialized = false; }

    private static final class Factory implements ConnectorStageFactory {
        @Override public StageCapability capability() { return StageCapability.RESPONSE_PARSER; }
        @Override public void validate(JsonNode config, PluginValidationContext context) { }
        @Override public ConnectorStage create(CompiledStageConfig config) {
            return new ConnectorStage() {
                @Override public StageCapability capability() { return StageCapability.RESPONSE_PARSER; }
                @Override public void execute(ConnectorExchange exchange, StageExecutionContext context) { }
            };
        }
    }
}
