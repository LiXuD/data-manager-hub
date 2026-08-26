package com.dataplatform.access.connector.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.dataplatform.common.plugin.artifact.TrustedSigningKeyProvider;
import com.dataplatform.common.plugin.runtime.ConnectorPluginRegistry;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.common.plugin.runtime.PluginContextFactory;
import com.dataplatform.common.plugin.runtime.PluginRuntimeManager;
import com.dataplatform.plugin.spi.PluginContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConnectorRuntimeConfigurationGenericHttpTest {

    @Test
    void registersGenericHttpAsAContextBoundBuiltinWithTheStaticDescriptor() {
        ConnectorRuntimeConfiguration configuration = new ConnectorRuntimeConfiguration();
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        PluginContext context = mock(PluginContext.class);
        ConnectorRuntimeProperties properties = new ConnectorRuntimeProperties();
        properties.setHostVersion("1.0.0");

        try (PluginRuntimeManager manager = configuration.connectorPluginRuntimeManager(
                new ObjectMapper(), mock(TrustedSigningKeyProvider.class), context,
                mock(PluginContextFactory.class), registry, properties)) {
            assertTrue(manager.isLoaded(GenericHttpConnectorMetadata.PLUGIN_ID,
                    GenericHttpConnectorMetadata.VERSION));
            try (var lease = registry.acquire(GenericHttpConnectorMetadata.PLUGIN_ID,
                    GenericHttpConnectorMetadata.VERSION)) {
                assertEquals(GenericHttpConnectorMetadata.descriptor(), lease.handle().plugin().descriptor());
                assertSame(context, lease.handle().pluginContext().orElseThrow());
            }
        }
    }
}
