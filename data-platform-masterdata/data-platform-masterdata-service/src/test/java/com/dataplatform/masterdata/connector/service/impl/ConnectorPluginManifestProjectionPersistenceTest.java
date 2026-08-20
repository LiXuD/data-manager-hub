package com.dataplatform.masterdata.connector.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.common.plugin.artifact.PluginCompatibility;
import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginMapper;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorTestFactMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator;
import com.dataplatform.masterdata.connector.service.PluginArtifactVerifier;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorPluginManifestProjectionPersistenceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                ConnectorPluginVersion.class);
    }

    @Test
    void importPersistsSafeV1Projection() {
        Fixture fixture = fixture(v1Artifact());

        fixture.service.importVersion(request(), 9L);

        ConnectorPluginVersion stored = captureInsert(fixture.versionMapper);
        assertEquals("1", stored.getManifestVersion());
        assertEquals("ADVANCED_PIPELINE", stored.getAuthoringModel());
        assertNull(stored.getConnectorKind());
        assertNull(stored.getTransportMode());
        assertNull(stored.getOutputMode());
        assertNull(stored.getCompatibilityManifest());
    }

    @Test
    void importPersistsCompleteV2Projection() {
        Fixture fixture = fixture(v2Artifact());

        fixture.service.importVersion(request(), 9L);

        ConnectorPluginVersion stored = captureInsert(fixture.versionMapper);
        assertEquals("2", stored.getManifestVersion());
        assertEquals("SIMPLE_CONNECTOR", stored.getAuthoringModel());
        assertEquals("DEDICATED_VENDOR", stored.getConnectorKind());
        assertEquals("HOST_SINGLE_HTTP", stored.getTransportMode());
        assertEquals("HOST_MAPPING", stored.getOutputMode());
        var expectedCompatibility = MAPPER.createObjectNode();
        expectedCompatibility.putArray("vendorCodes").add("ACME");
        assertEquals(expectedCompatibility, readTree(stored.getCompatibilityManifest()));
    }

    @Test
    void reverifyRejectsPersistedProjectionDriftAndDoesNotRestoreVerifiedState() {
        Fixture fixture = fixture(v2Artifact());
        ConnectorPluginVersion current = v2Entity();
        current.setTransportMode("HOST_MANAGED_MULTI_HTTP");
        current.setStatus("STAGING_FAILED");
        when(fixture.versionMapper.selectOne(any())).thenReturn(current);

        assertThrows(IllegalStateException.class,
                () -> fixture.service.verify("fixture-plugin", "2.0.0", 9L));

        assertEquals("STAGING_FAILED", current.getStatus());
        assertEquals("STATIC_VERIFICATION_FAILED", current.getSafeErrorCode());
        verify(fixture.versionMapper).updateById(current);
    }

    @Test
    void artifactProjectionIsCrossCheckedAgainstSignedManifest() {
        Fixture fixture = fixture(v2Artifact());
        ConnectorPluginVersion current = v2Entity();
        current.setCompatibilityManifest("{\"vendorCodes\":[\"OTHER\"]}");
        current.setStatus("VERIFIED");
        when(fixture.versionMapper.selectOne(any())).thenReturn(current);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.service.artifact("fixture-plugin", "2.0.0"));

        assertEquals("插件Manifest索引投影与签名Manifest不一致", error.getMessage());
    }

    private Fixture fixture(VerifiedPluginArtifact artifact) {
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        PluginArtifactVerifier verifier = mock(PluginArtifactVerifier.class);
        when(verifier.verify(any())).thenReturn(artifact);
        ConnectorPluginCatalogServiceImpl service = new ConnectorPluginCatalogServiceImpl(
                mock(ConnectorPluginMapper.class), versionMapper,
                mock(VendorConnectorVersionMapper.class), mock(VendorConfigMapper.class),
                mock(VendorConnectorTestFactMapper.class), verifier,
                mock(ConnectorPluginActivationInternalFeignClient.class),
                mock(ConnectorPluginReleaseCoordinator.class), MAPPER);
        return new Fixture(service, versionMapper);
    }

    private ConnectorPluginVersion captureInsert(ConnectorPluginVersionMapper mapper) {
        ArgumentCaptor<ConnectorPluginVersion> captor = ArgumentCaptor.forClass(ConnectorPluginVersion.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    private PluginImportRequestDTO request() {
        return new PluginImportRequestDTO("https://repo.example/plugins/fixture.jar",
                "a".repeat(64), "signature", "test-key");
    }

    private VerifiedPluginArtifact v1Artifact() {
        return new VerifiedPluginArtifact("fixture-plugin", "2.0.0", "1.0", "Fixture", "test", null,
                "example.FixturePlugin", "https://repo.example/plugins/fixture.jar", "a".repeat(64),
                "signature", "test-key", validV1Manifest(), "{\"type\":\"object\"}",
                List.of("TRANSPORT"), "{\"networkProtocols\":[\"https\"],\"networkHosts\":[]}",
                "1.0.0", MAPPER.createObjectNode().put("type", "object"));
    }

    private VerifiedPluginArtifact v2Artifact() {
        return new VerifiedPluginArtifact("fixture-plugin", "2.0.0", "1.1", "Fixture", "test", null,
                "example.FixturePlugin", "https://repo.example/plugins/fixture.jar", "a".repeat(64),
                "signature", "test-key", validV2Manifest(), "{\"type\":\"object\"}",
                List.of("REQUEST_BUILDER", "RESPONSE_PARSER"),
                "{\"networkProtocols\":[\"https\"],\"networkHosts\":[]}", "2.0.0",
                MAPPER.createObjectNode().put("type", "object"), "2",
                ConnectorAuthoringModel.SIMPLE_CONNECTOR, ConnectorKind.DEDICATED_VENDOR,
                ConnectorTransportMode.HOST_SINGLE_HTTP, ConnectorOutputMode.HOST_MAPPING,
                new PluginCompatibility(Set.of("ACME"), Set.of()),
                "{\"vendorCodes\":[\"ACME\"]}");
    }

    private ConnectorPluginVersion v2Entity() {
        ConnectorPluginVersion entity = new ConnectorPluginVersion();
        entity.setPluginId("fixture-plugin");
        entity.setVersion("2.0.0");
        entity.setSpiVersion("1.1");
        entity.setEntryClass("example.FixturePlugin");
        entity.setArtifactUri("https://repo.example/plugins/fixture.jar");
        entity.setArtifactSha256("a".repeat(64));
        entity.setDetachedSignature("signature");
        entity.setSigningKeyId("test-key");
        entity.setManifestJson(validV2Manifest());
        entity.setConfigSchemaJson("{\"type\":\"object\"}");
        entity.setCapabilities("[\"REQUEST_BUILDER\",\"RESPONSE_PARSER\"]");
        entity.setPermissionManifest("{\"networkProtocols\":[\"https\"],\"networkHosts\":[]}");
        entity.setMinHostVersion("2.0.0");
        entity.setManifestVersion("2");
        entity.setAuthoringModel("SIMPLE_CONNECTOR");
        entity.setConnectorKind("DEDICATED_VENDOR");
        entity.setTransportMode("HOST_SINGLE_HTTP");
        entity.setOutputMode("HOST_MAPPING");
        entity.setCompatibilityManifest("{\"vendorCodes\":[\"ACME\"]}");
        return entity;
    }

    private String validV1Manifest() {
        return """
                {"manifestVersion":"1","pluginId":"fixture-plugin","version":"2.0.0",\
                "spiVersion":"1.0","displayName":"Fixture","provider":"test",\
                "entryClass":"example.FixturePlugin","capabilities":["TRANSPORT"],\
                "minHostVersion":"1.0.0","configSchema":{"type":"object"},\
                "permissions":{"networkProtocols":["https"],"networkHosts":[]}}
                """.replace("\\\n", "");
    }

    private String validV2Manifest() {
        return """
                {"manifestVersion":"2","pluginId":"fixture-plugin","version":"2.0.0",\
                "spiVersion":"1.1","displayName":"Fixture","provider":"test",\
                "entryClass":"example.FixturePlugin","authoringModel":"SIMPLE_CONNECTOR",\
                "connectorKind":"DEDICATED_VENDOR","transportMode":"HOST_SINGLE_HTTP",\
                "outputMode":"HOST_MAPPING","capabilities":["REQUEST_BUILDER","RESPONSE_PARSER"],\
                "compatibility":{"vendorCodes":["ACME"]},"minHostVersion":"2.0.0",\
                "configSchema":{"type":"object"},\
                "permissions":{"networkProtocols":["https"],"networkHosts":[]}}
                """.replace("\\\n", "");
    }

    private com.fasterxml.jackson.databind.JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private record Fixture(
            ConnectorPluginCatalogServiceImpl service,
            ConnectorPluginVersionMapper versionMapper) {
    }
}
