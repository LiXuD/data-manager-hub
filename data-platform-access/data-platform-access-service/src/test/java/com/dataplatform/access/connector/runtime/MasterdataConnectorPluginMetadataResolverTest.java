package com.dataplatform.access.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.api.Result;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorMetadata;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MasterdataConnectorPluginMetadataResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorPluginInternalFeignClient client =
            mock(ConnectorPluginInternalFeignClient.class);
    private final MasterdataConnectorPluginMetadataResolver resolver =
            new MasterdataConnectorPluginMetadataResolver(client, mapper);

    @Test
    void resolvesExactPlatformCoreCoordinateWithoutFeign() {
        var metadata = resolver.resolve(PlatformCoreConnectorMetadata.PLUGIN_ID,
                PlatformCoreConnectorMetadata.VERSION);

        assertEquals(PlatformCoreConnectorMetadata.metadata(), metadata);
        verifyNoInteractions(client);
    }

    @Test
    void rejectsWrongPlatformCoreVersionWithoutFeign() {
        assertThrows(IllegalStateException.class,
                () -> resolver.resolve(PlatformCoreConnectorMetadata.PLUGIN_ID, "1.0.1"));

        verifyNoInteractions(client);
    }

    @Test
    void ordinaryPluginStillUsesMasterdataExactlyOnce() {
        PluginArtifactDescriptorDTO descriptor = descriptor("{\"type\":\"object\"}");
        when(client.getArtifact("demo-http", "1.0.0")).thenReturn(Result.success(descriptor));

        assertEquals("demo-http", resolver.resolve("demo-http", "1.0.0").pluginId());

        verify(client).getArtifact("demo-http", "1.0.0");
    }

    @Test
    void bindsDescriptorToCanonicalManifestSchemaAndArtifact() {
        var metadata = resolver.validate(descriptor("{\"type\":\"object\"}"), "demo-http", "1.0.0");

        assertEquals("demo-http", metadata.pluginId());
        assertEquals("1.0.0", metadata.version());
        assertEquals("a".repeat(64), metadata.artifactSha256());
        assertEquals(64, metadata.manifestHash().length());
        assertEquals(64, metadata.schemaHash().length());
    }

    @Test
    void rejectsControlPlaneSchemaDriftFromTheFixedManifest() {
        assertThrows(IllegalStateException.class,
                () -> resolver.validate(descriptor("{\"type\":\"string\"}"), "demo-http", "1.0.0"));
    }

    @Test
    void bindsEveryV2ProjectionToTheSignedManifest() {
        var metadata = resolver.validate(v2Descriptor("HOST_MAPPING"), "demo-vendor", "2.0.0");

        assertEquals("2", metadata.manifestVersion());
        assertEquals("SIMPLE_CONNECTOR", metadata.authoringModel().name());
        assertEquals("DEDICATED_VENDOR", metadata.connectorKind().name());
        assertEquals("HOST_SINGLE_HTTP", metadata.transportMode().name());
        assertEquals("HOST_MAPPING", metadata.outputMode().name());
        assertEquals(List.of("VENDOR_A"), metadata.compatibility().vendorCodes().stream().toList());
    }

    @Test
    void rejectsUnsignedV2ProjectionDrift() {
        assertThrows(IllegalStateException.class,
                () -> resolver.validate(v2Descriptor("PLUGIN_NORMALIZED"),
                        "demo-vendor", "2.0.0"));
    }

    @Test
    void genericHttpRequiresCatalogButBindsEveryFactToHostCode() {
        PluginArtifactDescriptorDTO descriptor = genericDescriptor();
        when(client.getArtifact(GenericHttpConnectorMetadata.PLUGIN_ID,
                GenericHttpConnectorMetadata.VERSION)).thenReturn(Result.success(descriptor));

        assertEquals(GenericHttpConnectorMetadata.metadata(), resolver.resolve(
                GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION));

        verify(client).getArtifact(GenericHttpConnectorMetadata.PLUGIN_ID,
                GenericHttpConnectorMetadata.VERSION);
    }

    @Test
    void genericHttpRejectsStaticArtifactAndVersionDrift() {
        PluginArtifactDescriptorDTO good = genericDescriptor();
        PluginArtifactDescriptorDTO drifted = new PluginArtifactDescriptorDTO(
                good.pluginId(), good.version(), good.spiVersion(), good.entryClass(),
                good.artifactUri(), "f".repeat(64), good.detachedSignature(), good.signingKeyId(),
                good.manifestJson(), good.configSchemaJson(), good.capabilities(),
                good.permissionManifestJson(), good.minHostVersion(), good.status(),
                good.manifestVersion(), good.authoringModel(), good.connectorKind(),
                good.transportMode(), good.outputMode(), good.compatibilityJson());
        when(client.getArtifact(GenericHttpConnectorMetadata.PLUGIN_ID,
                GenericHttpConnectorMetadata.VERSION)).thenReturn(Result.success(drifted));

        assertThrows(IllegalStateException.class, () -> resolver.resolve(
                GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION));
        assertThrows(IllegalStateException.class, () -> resolver.resolve(
                GenericHttpConnectorMetadata.PLUGIN_ID, "2.0.1"));
        verify(client, never()).getArtifact(GenericHttpConnectorMetadata.PLUGIN_ID, "2.0.1");
    }

    private PluginArtifactDescriptorDTO descriptor(String schema) {
        String manifest = """
                {"manifestVersion":"1","pluginId":"demo-http","version":"1.0.0","spiVersion":"1.0",
                 "displayName":"Demo","provider":"internal","description":"control-plane metadata",
                 "entryClass":"example.DemoPlugin","capabilities":["TRANSPORT"],"minHostVersion":"2.1.0",
                 "configSchema":{"type":"object"},
                 "permissions":{"networkProtocols":["https"],"networkHosts":["api.example.com"]}}
                """;
        return new PluginArtifactDescriptorDTO("demo-http", "1.0.0", "1.0", "example.DemoPlugin",
                "https://repo.example/demo.jar", "a".repeat(64), "signature", "key-1",
                manifest, schema, List.of("TRANSPORT"),
                "{\"networkProtocols\":[\"https\"],\"networkHosts\":[\"api.example.com\"]}",
                "2.1.0", "VERIFIED");
    }

    private PluginArtifactDescriptorDTO v2Descriptor(String projectedOutputMode) {
        String manifest = """
                {"manifestVersion":"2","pluginId":"demo-vendor","version":"2.0.0","spiVersion":"1.1",
                 "displayName":"Demo Vendor","provider":"internal","entryClass":"example.DemoVendorPlugin",
                 "authoringModel":"SIMPLE_CONNECTOR","connectorKind":"DEDICATED_VENDOR",
                 "transportMode":"HOST_SINGLE_HTTP","outputMode":"HOST_MAPPING",
                 "capabilities":["REQUEST_BUILDER","RESPONSE_PARSER"],
                 "compatibility":{"vendorCodes":["VENDOR_A"]},"minHostVersion":"2.1.0",
                 "configSchema":{"type":"object"},
                 "permissions":{"networkProtocols":["https"],"networkHosts":["api.example.com"]}}
                """;
        return new PluginArtifactDescriptorDTO("demo-vendor", "2.0.0", "1.1",
                "example.DemoVendorPlugin", "https://repo.example/demo-vendor.jar",
                "b".repeat(64), "signature", "key-1", manifest, "{\"type\":\"object\"}",
                List.of("REQUEST_BUILDER", "RESPONSE_PARSER"),
                "{\"networkHosts\":[\"api.example.com\"],\"networkProtocols\":[\"https\"]}",
                "2.1.0", "VERIFIED", "2", "SIMPLE_CONNECTOR", "DEDICATED_VENDOR",
                "HOST_SINGLE_HTTP", projectedOutputMode, "{\"vendorCodes\":[\"VENDOR_A\"]}");
    }

    private PluginArtifactDescriptorDTO genericDescriptor() {
        return new PluginArtifactDescriptorDTO(
                GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION,
                GenericHttpConnectorMetadata.SPI_VERSION, GenericHttpConnectorMetadata.ENTRY_CLASS,
                GenericHttpConnectorMetadata.ARTIFACT_URI,
                GenericHttpConnectorMetadata.artifactSha256(),
                GenericHttpConnectorMetadata.BUILTIN_SIGNATURE,
                GenericHttpConnectorMetadata.BUILTIN_SIGNING_KEY,
                GenericHttpConnectorMetadata.canonicalManifestJson(),
                GenericHttpConnectorMetadata.canonicalSchemaJson(),
                GenericHttpConnectorMetadata.CAPABILITY_NAMES,
                GenericHttpConnectorMetadata.canonicalPermissionsJson(),
                GenericHttpConnectorMetadata.MIN_HOST_VERSION, "ACTIVE", "2",
                "SIMPLE_CONNECTOR", "GENERIC_HTTP", "HOST_SINGLE_HTTP", "HOST_MAPPING",
                GenericHttpConnectorMetadata.canonicalCompatibilityJson());
    }
}
