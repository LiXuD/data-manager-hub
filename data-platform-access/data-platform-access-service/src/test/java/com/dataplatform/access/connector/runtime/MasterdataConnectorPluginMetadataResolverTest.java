package com.dataplatform.access.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MasterdataConnectorPluginMetadataResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final MasterdataConnectorPluginMetadataResolver resolver =
            new MasterdataConnectorPluginMetadataResolver(
                    mock(ConnectorPluginInternalFeignClient.class), mapper);

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
}
