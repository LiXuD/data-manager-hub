package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.schema.ConnectorJsonSchemaValidator;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformCoreConnectorMetadataTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorJsonSchemaValidator validator = new ConnectorJsonSchemaValidator();

    @Test
    void descriptorAndMetadataAreStableAndAligned() {
        var descriptor = PlatformCoreConnectorMetadata.descriptor();
        var metadata = PlatformCoreConnectorMetadata.metadata();

        assertEquals("platform-core", descriptor.pluginId());
        assertEquals("1.0.0", descriptor.version());
        assertEquals("1.1", descriptor.spiVersion());
        assertEquals(Set.of(StageCapability.REQUEST_PROCESSOR, StageCapability.TRANSPORT,
                StageCapability.RESPONSE_PROCESSOR, StageCapability.RESPONSE_NORMALIZER),
                descriptor.capabilities());
        assertEquals(descriptor.pluginId(), metadata.pluginId());
        assertEquals(descriptor.version(), metadata.version());
        assertEquals(64, PlatformCoreConnectorMetadata.artifactSha256().length());
        assertEquals(64, PlatformCoreConnectorMetadata.manifestSha256().length());
        assertEquals(64, PlatformCoreConnectorMetadata.schemaSha256().length());
        assertEquals(PlatformCoreConnectorMetadata.manifestSha256(), metadata.manifestHash());
        assertEquals(PlatformCoreConnectorMetadata.schemaSha256(), metadata.schemaHash());
        assertEquals(PlatformCoreConnectorMetadata.artifactSha256(), metadata.artifactSha256());
    }

    @Test
    void schemaCollectsHostSecurityRefsAndRejectsUnknownTopLevelFields() throws Exception {
        var config = mapper.readTree("""
                {"direction":"REQUEST","securitySteps":[],"secretRefs":["vendor.sign","vendor.token"]}
                """);

        assertEquals(Set.of("vendor.sign", "vendor.token"),
                validator.secretReferences(PlatformCoreConnectorMetadata.configSchema(), config));
        assertTrue(validator.validate(PlatformCoreConnectorMetadata.configSchema(),
                mapper.readTree("{\"unexpected\":true}"))
                .stream().anyMatch(error -> error.contains("未声明字段")));
    }

    @Test
    void schemaAndMetadataReturnDefensiveCopiesAndHashesDoNotDependOnMapperOrder() throws Exception {
        var mutatedSchema = PlatformCoreConnectorMetadata.configSchema();
        ((com.fasterxml.jackson.databind.node.ObjectNode) mutatedSchema).remove("properties");
        assertTrue(PlatformCoreConnectorMetadata.configSchema().has("properties"));

        var mapperWithOrder = new ObjectMapper();
        mapperWithOrder.configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        assertEquals(PlatformCoreConnectorMetadata.manifestSha256(),
                ConnectorSnapshotIntegrity.sha256(mapperWithOrder,
                        mapperWithOrder.readTree(PlatformCoreConnectorMetadata.canonicalManifestJson())));
        var firstMetadataSchema = PlatformCoreConnectorMetadata.metadata().configSchema();
        ((com.fasterxml.jackson.databind.node.ObjectNode) firstMetadataSchema).remove("properties");
        assertTrue(PlatformCoreConnectorMetadata.metadata().configSchema().has("properties"));
    }
}
