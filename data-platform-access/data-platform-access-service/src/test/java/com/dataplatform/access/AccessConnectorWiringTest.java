package com.dataplatform.access;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.dataplatform.access.connector.artifact.ConnectorPluginArtifactCache;
import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AccessConnectorWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ConnectorRuntimeProperties.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(ConnectorPluginArtifactCache.class);

    @Test
    void scansConnectorActivationMapperPackage() {
        MapperScan scan = AccessApplication.class.getAnnotation(MapperScan.class);
        assertTrue(List.of(scan.value()).contains("com.dataplatform.access.connector.mapper"));
    }

    @Test
    void createsConnectorPluginArtifactCacheThroughSpringConstructorResolution() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ConnectorPluginArtifactCache.class);
        });
    }
}
