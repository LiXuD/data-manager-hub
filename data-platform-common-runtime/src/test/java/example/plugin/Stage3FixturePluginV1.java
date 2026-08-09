package example.plugin;

import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.StageCapability;
import java.util.Set;

public final class Stage3FixturePluginV1 extends Stage3FixturePluginSupport {
    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor("stage3-fixture", "1.0.0", "1.0", "Stage 3 Fixture V1", "test",
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                        StageCapability.RESPONSE_NORMALIZER));
    }
}
