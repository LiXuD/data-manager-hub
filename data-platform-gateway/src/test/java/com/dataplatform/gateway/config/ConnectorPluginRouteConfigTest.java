package com.dataplatform.gateway.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConnectorPluginRouteConfigTest {

    @Test
    void connectorPluginManagementRouteExistsInEveryEnvironment() throws Exception {
        assertRoute(Path.of("..", "nacos-config", "dev", "data-platform-gateway-dev.yml"));
        assertRoute(Path.of("..", "nacos-config", "prod", "data-platform-gateway-prod.yml"));
    }

    private void assertRoute(Path config) throws Exception {
        String yaml = Files.readString(config.normalize());
        assertTrue(yaml.contains("id: masterdata-connector-plugin-route"), config.toString());
        assertTrue(yaml.contains("uri: lb://data-platform-masterdata"), config.toString());
        assertTrue(yaml.contains("Path=/api/v1/connector-plugin/**"), config.toString());
        assertTrue(yaml.contains("StripPrefix=2"), config.toString());
        assertFalse(yaml.contains("/api/v1/data/**"), config.toString());
    }
}
