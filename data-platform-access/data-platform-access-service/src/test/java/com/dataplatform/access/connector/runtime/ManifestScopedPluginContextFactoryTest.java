package com.dataplatform.access.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.common.plugin.artifact.PluginArtifactException;
import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.common.plugin.artifact.PluginPermissions;
import com.dataplatform.plugin.spi.ManagedTaskExecutor;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

class ManifestScopedPluginContextFactoryTest {

    @Test
    void intersectsWildcardAndExactHostWithMostRestrictiveResult() {
        assertEquals(Set.of("api.vendor.example"),
                ManifestScopedPluginContextFactory.intersectHosts(
                        List.of("*.vendor.example"), List.of("api.vendor.example")));
        assertEquals(Set.of("*.api.vendor.example"),
                ManifestScopedPluginContextFactory.intersectHosts(
                        List.of("*.vendor.example"), List.of("*.api.vendor.example")));
    }

    @Test
    void refusesManifestDomainOutsidePlatformAllowlist() {
        ConnectorRuntimeProperties properties = new ConnectorRuntimeProperties();
        properties.setNetworkAllowedHosts(List.of("approved.example"));
        ManifestScopedPluginContextFactory factory = factory(properties);

        assertThrows(PluginArtifactException.class, () -> factory.create(manifest(
                new PluginPermissions(List.of("https"), List.of("unapproved.example")))));
    }

    @Test
    void refusesMissingManifestPermissionsByDefault() {
        ConnectorRuntimeProperties properties = new ConnectorRuntimeProperties();
        properties.setNetworkAllowedHosts(List.of("approved.example"));
        assertThrows(PluginArtifactException.class, () -> factory(properties).create(manifest(null)));
    }

    @Test
    void permitsExplicitNoNetworkPluginWithFailClosedTransport() {
        ConnectorRuntimeProperties properties = new ConnectorRuntimeProperties();
        assertNotNull(factory(properties).create(manifest(new PluginPermissions(List.of(), List.of()))));
    }

    private ManifestScopedPluginContextFactory factory(ConnectorRuntimeProperties properties) {
        return new ManifestScopedPluginContextFactory(properties, new OkHttpClient(),
                new ScopedConnectorSecretResolver(), Clock.systemUTC(), mock(PluginLogger.class),
                mock(PluginMetricRecorder.class), new ObjectMapper(), mock(ManagedTaskExecutor.class));
    }

    private PluginManifest manifest(PluginPermissions permissions) {
        return new PluginManifest("1", "demo", "1.0.0", "1.0", "Demo", "internal",
                "example.Demo", Set.of(StageCapability.TRANSPORT), "1.0.0",
                new ObjectMapper().createObjectNode(), permissions);
    }
}
