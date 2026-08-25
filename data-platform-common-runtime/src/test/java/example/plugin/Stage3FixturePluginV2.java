package example.plugin;

import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.StageCapability;
import java.util.Set;

public final class Stage3FixturePluginV2 extends Stage3FixturePluginSupport {
    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor("stage3-fixture", "2.0.0", "1.0", "Stage 3 Fixture V2", "test",
                Set.of(StageCapability.REQUEST_BUILDER, StageCapability.TRANSPORT,
                        StageCapability.RESPONSE_NORMALIZER));
    }
}
