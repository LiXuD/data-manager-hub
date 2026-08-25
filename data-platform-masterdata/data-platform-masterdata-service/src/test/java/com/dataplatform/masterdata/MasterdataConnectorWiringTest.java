package com.dataplatform.masterdata;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.dataplatform.access.connector.api.feign.ConnectorMigrationObservationInternalFeignClient;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.billing.api.feign.ConnectorBillingObservationInternalFeignClient;
import com.dataplatform.masterdata.connector.config.ConnectorPluginProperties;
import com.dataplatform.masterdata.connector.service.PluginArtifactVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;

class MasterdataConnectorWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ConnectorPluginProperties.class)
            .withBean(ObjectMapper.class)
            .withBean(PluginArtifactVerifier.class);

    @Test
    void scansConnectorMappersAndRegistersAccessRuntimeContracts() {
        MapperScan scan = MasterdataApplication.class.getAnnotation(MapperScan.class);
        EnableFeignClients feign = MasterdataApplication.class.getAnnotation(EnableFeignClients.class);

        assertTrue(List.of(scan.basePackages()).contains("com.dataplatform.masterdata.connector.mapper"));
        assertTrue(List.of(scan.basePackages()).contains("com.dataplatform.masterdata.connector.spec"));
        assertTrue(List.of(feign.clients()).contains(ConnectorPluginActivationInternalFeignClient.class));
        assertTrue(List.of(feign.clients()).contains(VendorConnectorRuntimeInternalFeignClient.class));
        assertTrue(List.of(feign.clients()).contains(ConnectorMigrationObservationInternalFeignClient.class));
        assertTrue(List.of(feign.clients()).contains(ConnectorBillingObservationInternalFeignClient.class));
    }

    @Test
    void createsPluginArtifactVerifierThroughSpringConstructorResolution() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PluginArtifactVerifier.class);
        });
    }
}
