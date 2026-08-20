package com.dataplatform.access.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.artifact.ConnectorPluginArtifactCache;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.common.plugin.runtime.PluginRuntimeManager;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultConnectorPluginRuntimeOperationsTest {

    private final ConnectorPluginArtifactCache cache = mock(ConnectorPluginArtifactCache.class);
    private final PluginRuntimeManager manager = mock(PluginRuntimeManager.class);
    private final MasterdataConnectorPluginMetadataResolver resolver =
            mock(MasterdataConnectorPluginMetadataResolver.class);
    private final DefaultConnectorPluginRuntimeOperations operations =
            new DefaultConnectorPluginRuntimeOperations(cache, manager, resolver);

    @Test
    void genericBuiltinValidatesBeforeLoadedFastPathAndNeverDownloads() {
        PluginArtifactDescriptorDTO artifact = genericArtifact();
        when(manager.isLoaded(artifact.pluginId(), artifact.version())).thenReturn(true);
        when(resolver.validateGenericBuiltin(artifact)).thenReturn(GenericHttpConnectorMetadata.metadata());

        operations.preload(artifact);

        verify(resolver).validateGenericBuiltin(artifact);
        verify(manager).isLoaded(artifact.pluginId(), artifact.version());
        verifyNoInteractions(cache);
    }

    @Test
    void genericDriftAndMissingBuiltinFailBeforeArtifactCache() {
        PluginArtifactDescriptorDTO artifact = genericArtifact();
        when(resolver.validateGenericBuiltin(artifact)).thenReturn(GenericHttpConnectorMetadata.metadata());
        when(manager.isLoaded(artifact.pluginId(), artifact.version())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> operations.preload(artifact));
        verifyNoInteractions(cache);
    }

    @Test
    void genericStaticDriftFailsBeforeLoadedFastPathAndArtifactCache() {
        PluginArtifactDescriptorDTO artifact = genericArtifact();
        doThrow(new IllegalStateException("static drift"))
                .when(resolver).validateGenericBuiltin(artifact);

        assertThrows(IllegalStateException.class, () -> operations.preload(artifact));

        verifyNoInteractions(manager, cache);
    }

    @Test
    void alreadyLoadedExternalPluginPreservesOriginalNoDownloadBehavior() {
        PluginArtifactDescriptorDTO artifact = new PluginArtifactDescriptorDTO(
                "demo", "1.0.0", "1.0", "example.Demo", "https://repo/demo.jar",
                "a".repeat(64), "signature", "key", "{}", "{}",
                List.of("TRANSPORT"), "{}", "1.0.0", "ACTIVE");
        when(manager.isLoaded("demo", "1.0.0")).thenReturn(true);

        operations.preload(artifact);

        verifyNoInteractions(resolver, cache);
        verify(manager, never()).preload(org.mockito.ArgumentMatchers.any());
    }

    private PluginArtifactDescriptorDTO genericArtifact() {
        return new PluginArtifactDescriptorDTO(
                GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                GenericHttpConnectorMetadata.SPI_VERSION, GenericHttpConnectorMetadata.ENTRY_CLASS,
                GenericHttpConnectorMetadata.ARTIFACT_URI, GenericHttpConnectorMetadata.artifactSha256(),
                "builtin", "builtin", GenericHttpConnectorMetadata.canonicalManifestJson(),
                GenericHttpConnectorMetadata.canonicalSchemaJson(),
                GenericHttpConnectorMetadata.CAPABILITY_NAMES,
                GenericHttpConnectorMetadata.canonicalPermissionsJson(), "1.0.0", "ACTIVE",
                "2", "SIMPLE_CONNECTOR", "GENERIC_HTTP", "HOST_SINGLE_HTTP", "HOST_MAPPING",
                GenericHttpConnectorMetadata.canonicalCompatibilityJson());
    }
}
