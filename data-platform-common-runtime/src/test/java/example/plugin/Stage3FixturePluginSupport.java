package example.plugin;

import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Shared implementation for the two real isolated JARs used by stage-3 acceptance. */
public abstract class Stage3FixturePluginSupport implements ConnectorPlugin {

    private volatile boolean initialized;

    @Override
    public void initialize(PluginContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        initialized = true;
    }

    @Override
    public List<ConnectorStageFactory> stageFactories() {
        return List.of(
                new Factory(StageCapability.REQUEST_BUILDER),
                new Factory(StageCapability.TRANSPORT),
                new Factory(StageCapability.RESPONSE_NORMALIZER));
    }

    @Override
    public PluginSelfTestResult selfTest() {
        return initialized ? PluginSelfTestResult.success()
                : PluginSelfTestResult.failure("fixture was not initialized");
    }

    @Override
    public void close() {
        initialized = false;
    }

    private static final class Factory implements ConnectorStageFactory {
        private final StageCapability capability;

        private Factory(StageCapability capability) {
            this.capability = capability;
        }

        @Override
        public StageCapability capability() {
            return capability;
        }

        @Override
        public void validate(JsonNode config, PluginValidationContext context) {
            if (config == null || !config.isObject()) {
                throw new IllegalArgumentException("fixture config must be an object");
            }
        }

        @Override
        public ConnectorStage create(CompiledStageConfig config) {
            return switch (capability) {
                case REQUEST_BUILDER -> new RequestBuilderStage();
                case TRANSPORT -> new TransportStage();
                case RESPONSE_NORMALIZER -> new NormalizerStage();
                default -> throw new IllegalStateException("unsupported fixture capability");
            };
        }
    }

    private static final class RequestBuilderStage implements ConnectorStage {
        @Override
        public StageCapability capability() {
            return StageCapability.REQUEST_BUILDER;
        }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext context) {
            exchange.setRequest(new ConnectorRequest(
                    "POST", URI.create("https://fixture.invalid/echo"), Map.of(), Map.of(),
                    "application/json", new byte[0], Duration.ofSeconds(1), Duration.ofSeconds(1),
                    Duration.ofSeconds(2), IdempotencyPolicy.IDEMPOTENT, null, 1024));
        }
    }

    private static final class TransportStage implements ConnectorStage {
        @Override
        public StageCapability capability() {
            return StageCapability.TRANSPORT;
        }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext context) {
            byte[] body = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.setRawResponse(new ConnectorRawResponse(
                    200, Map.of(), body, Duration.ZERO, exchange.request().url(), 0, body.length));
        }
    }

    private static final class NormalizerStage implements ConnectorStage {
        @Override
        public StageCapability capability() {
            return StageCapability.RESPONSE_NORMALIZER;
        }

        @Override
        public void execute(ConnectorExchange exchange, StageExecutionContext context) {
            exchange.setNormalizedData(Map.of("accepted", true));
            exchange.setBillingSignal(BillingSignal.ELIGIBLE);
            exchange.setCacheSignal(CacheSignal.CACHEABLE);
        }
    }
}
