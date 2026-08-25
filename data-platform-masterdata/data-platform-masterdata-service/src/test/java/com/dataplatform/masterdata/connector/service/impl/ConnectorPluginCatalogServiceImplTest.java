package com.dataplatform.masterdata.connector.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationDTO;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.entity.ConnectorPlugin;
import com.dataplatform.masterdata.connector.entity.VendorConnectorTestFact;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginMapper;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorTestFactMapper;
import com.dataplatform.masterdata.connector.service.PluginArtifactVerifier;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.api.Result;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorMetadata;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConnectorPluginCatalogServiceImplTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                ConnectorPluginVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                VendorConfig.class);
    }

    @Test
    void successfulVerificationExplicitlyClearsPreviousSafeFailure() {
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginVersion version = failedVersion("STAGING_FAILED");
        when(versionMapper.selectOne(any())).thenReturn(version);
        PluginArtifactVerifier artifactVerifier = mock(PluginArtifactVerifier.class);
        when(artifactVerifier.verify(any())).thenReturn(verifiedArtifact());
        ConnectorPluginCatalogServiceImpl service = service(versionMapper,
                mock(VendorConnectorTestFactMapper.class), artifactVerifier,
                mock(ConnectorPluginActivationInternalFeignClient.class));

        var result = service.verify("demo", "1.0.0", 9L);

        assertEquals("VERIFIED", result.status());
        assertNull(result.safeErrorCode());
        assertNull(result.safeErrorDigest());
        assertClearsSafeErrors(captureLastUpdate(versionMapper));
    }

    @Test
    void successfulRestageClearsFailedAttemptBeforeAccessReportsReady() {
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginVersion version = failedVersion("STAGING_FAILED");
        when(versionMapper.selectOne(any())).thenReturn(version);
        ConnectorPluginActivationInternalFeignClient activationClient =
                mock(ConnectorPluginActivationInternalFeignClient.class);
        ConnectorPluginActivationSummaryDTO ready = new ConnectorPluginActivationSummaryDTO();
        ready.setReady(true);
        ready.setInstances(List.of(new ConnectorPluginActivationDTO()));
        when(activationClient.stage(any())).thenReturn(Result.success(ready));
        ConnectorPluginCatalogServiceImpl service = service(versionMapper,
                mock(VendorConnectorTestFactMapper.class), mock(PluginArtifactVerifier.class), activationClient);

        ConnectorPluginActivationSummaryDTO result = service.stage("demo", "1.0.0");

        assertTrue(result.getReady());
        assertEquals("STAGING", version.getStatus());
        assertNull(version.getSafeErrorCode());
        assertNull(version.getSafeErrorDigest());
        assertClearsSafeErrors(captureLastUpdate(versionMapper));
    }

    @Test
    void successfulActivationExplicitlyClearsStaleStagingFailure() {
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginVersion version = failedVersion("STAGING");
        when(versionMapper.selectOne(any())).thenReturn(version);
        ConnectorPluginActivationInternalFeignClient activationClient =
                mock(ConnectorPluginActivationInternalFeignClient.class);
        ConnectorPluginActivationSummaryDTO ready = new ConnectorPluginActivationSummaryDTO();
        ready.setReady(true);
        when(activationClient.activation("demo", "1.0.0")).thenReturn(Result.success(ready));
        VendorConnectorTestFact fact = new VendorConnectorTestFact();
        fact.setPluginBindings("[\"demo:1.0.0\"]");
        fact.setTestSucceeded(true);
        VendorConnectorTestFactMapper factMapper = mock(VendorConnectorTestFactMapper.class);
        when(factMapper.selectList(any())).thenReturn(List.of(fact));
        ConnectorPluginCatalogServiceImpl service = service(versionMapper, factMapper,
                mock(PluginArtifactVerifier.class), activationClient);

        var result = service.activate("demo", "1.0.0", 9L);

        assertEquals("ACTIVE", result.status());
        assertNull(result.safeErrorCode());
        assertNull(result.safeErrorDigest());
        assertClearsSafeErrors(captureLastUpdate(versionMapper));
    }

    @Test
    void stageFailureIsRecordedAsSafeFailedState() {
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginVersion version = new ConnectorPluginVersion();
        version.setPluginId("demo");
        version.setVersion("1.0.0");
        version.setStatus("VERIFIED");
        when(versionMapper.selectOne(any())).thenReturn(version);
        ConnectorPluginActivationInternalFeignClient activationClient =
                mock(ConnectorPluginActivationInternalFeignClient.class);
        when(activationClient.stage(any())).thenThrow(new IllegalStateException("downstream unavailable"));
        ConnectorPluginCatalogServiceImpl service = new ConnectorPluginCatalogServiceImpl(
                mock(ConnectorPluginMapper.class), versionMapper,
                mock(VendorConnectorVersionMapper.class),
                mock(com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper.class),
                mock(VendorConnectorTestFactMapper.class),
                mock(PluginArtifactVerifier.class),
                activationClient, releaseCoordinator(), new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> service.stage("demo", "1.0.0"));

        assertEquals("STAGING_FAILED", version.getStatus());
        assertEquals("ACCESS_PRELOAD_UNAVAILABLE", version.getSafeErrorCode());
        assertEquals(64, version.getSafeErrorDigest().length());
        assertFalse(version.getSafeErrorDigest().contains("downstream"));
    }

    @Test
    void refusesToDisableVersionBoundByActiveConnector() {
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginVersion version = new ConnectorPluginVersion();
        version.setPluginId("demo");
        version.setVersion("1.0.0");
        version.setStatus("ACTIVE");
        when(versionMapper.selectOne(any())).thenReturn(version);
        VendorConnectorVersionMapper connectorMapper = mock(VendorConnectorVersionMapper.class);
        VendorConnectorVersion active = new VendorConnectorVersion();
        active.setStatus("ACTIVE");
        active.setPipelineSnapshot("[{\"stageKey\":\"transport\",\"capability\":\"TRANSPORT\","
                + "\"pluginId\":\"demo\",\"pluginVersion\":\"1.0.0\",\"order\":1,\"enabled\":true,\"config\":{}}]");
        when(connectorMapper.selectList(any())).thenReturn(java.util.List.of(active));
        ConnectorPluginCatalogServiceImpl service = new ConnectorPluginCatalogServiceImpl(
                mock(ConnectorPluginMapper.class), versionMapper, connectorMapper,
                mock(com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper.class),
                mock(VendorConnectorTestFactMapper.class),
                mock(PluginArtifactVerifier.class),
                mock(ConnectorPluginActivationInternalFeignClient.class), releaseCoordinator(), new ObjectMapper());

        assertThrows(ConnectorConflictException.class, () -> service.disable("demo", "1.0.0", 9L));
    }

    @Test
    void activationRequiresAReadyRuntimeAndSuccessfulDraftTestForSamePluginVersion() {
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginVersion version = new ConnectorPluginVersion();
        version.setPluginId("demo");
        version.setVersion("1.0.0");
        version.setStatus("STAGING");
        when(versionMapper.selectOne(any())).thenReturn(version);
        ConnectorPluginActivationInternalFeignClient activationClient =
                mock(ConnectorPluginActivationInternalFeignClient.class);
        ConnectorPluginActivationSummaryDTO ready = new ConnectorPluginActivationSummaryDTO();
        ready.setReady(true);
        when(activationClient.activation("demo", "1.0.0")).thenReturn(Result.success(ready));
        VendorConnectorTestFactMapper factMapper = mock(VendorConnectorTestFactMapper.class);
        when(factMapper.selectList(any())).thenReturn(java.util.List.of());
        ConnectorPluginCatalogServiceImpl service = new ConnectorPluginCatalogServiceImpl(
                mock(ConnectorPluginMapper.class), versionMapper,
                mock(VendorConnectorVersionMapper.class),
                mock(com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper.class), factMapper,
                mock(PluginArtifactVerifier.class), activationClient, releaseCoordinator(), new ObjectMapper());

        assertThrows(ConnectorConflictException.class, () -> service.activate("demo", "1.0.0", 9L));
        assertEquals("STAGING", version.getStatus());
    }

    @Test
    void pluginDtoCountsDistinctActiveVendorBindings() {
        ConnectorPluginMapper pluginMapper = mock(ConnectorPluginMapper.class);
        ConnectorPlugin plugin = new ConnectorPlugin();
        plugin.setId(1L);
        plugin.setPluginId("demo");
        plugin.setDisplayName("Demo");
        plugin.setProvider("internal");
        plugin.setStatus("ACTIVE");
        when(pluginMapper.selectOne(any())).thenReturn(plugin);
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginVersion activeVersion = new ConnectorPluginVersion();
        activeVersion.setVersion("1.0.0");
        when(versionMapper.selectOne(any())).thenReturn(activeVersion);
        VendorConnectorVersionMapper connectorMapper = mock(VendorConnectorVersionMapper.class);
        VendorConnectorVersion first = activeBinding(7L, "demo");
        VendorConnectorVersion duplicateVendor = activeBinding(7L, "demo");
        VendorConnectorVersion otherPlugin = activeBinding(8L, "other");
        when(connectorMapper.selectList(any())).thenReturn(java.util.List.of(
                first, duplicateVendor, otherPlugin));
        ConnectorPluginCatalogServiceImpl service = new ConnectorPluginCatalogServiceImpl(
                pluginMapper, versionMapper, connectorMapper,
                mock(com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper.class),
                mock(VendorConnectorTestFactMapper.class),
                mock(PluginArtifactVerifier.class),
                mock(ConnectorPluginActivationInternalFeignClient.class), releaseCoordinator(), new ObjectMapper());

        assertEquals(1L, service.get("demo").bindingCount());
    }

    @Test
    void requiredArtifactsFailsClosedWhenAnActiveVendorHasNoPublishedPluginBinding() {
        VendorConfigMapper configMapper = mock(VendorConfigMapper.class);
        VendorConfig invalid = new VendorConfig();
        invalid.setId(77L);
        invalid.setStatus(com.dataplatform.common.enums.CommonStatus.ACTIVE);
        invalid.setRuntimeMode("PLUGIN");
        invalid.setActiveConnectorVersionId(null);
        when(configMapper.selectList(any())).thenReturn(List.of(invalid));
        ConnectorPluginCatalogServiceImpl service = new ConnectorPluginCatalogServiceImpl(
                mock(ConnectorPluginMapper.class), mock(ConnectorPluginVersionMapper.class),
                mock(VendorConnectorVersionMapper.class), configMapper,
                mock(VendorConnectorTestFactMapper.class), mock(PluginArtifactVerifier.class),
                mock(ConnectorPluginActivationInternalFeignClient.class), releaseCoordinator(), new ObjectMapper());

        IllegalStateException error = assertThrows(IllegalStateException.class, service::requiredArtifacts);

        assertEquals("ACTIVE_CONNECTOR_BINDING_INVALID", error.getMessage());
    }

    @Test
    void requiredArtifactsSkipsOnlyExactPlatformCoreAndKeepsVendorArtifact() {
        VendorConfigMapper configMapper = mock(VendorConfigMapper.class);
        VendorConnectorVersionMapper connectorMapper = mock(VendorConnectorVersionMapper.class);
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        VendorConfig config = activeConfig(77L, 88L);
        VendorConnectorVersion connector = activePipeline(77L, """
                [
                  {"stageKey":"connector.request-builder","capability":"REQUEST_BUILDER",
                   "pluginId":"vendor-http","pluginVersion":"2.0.0","order":1,"enabled":true,"config":{}},
                  {"stageKey":"platform.transport","capability":"TRANSPORT",
                   "pluginId":"platform-core","pluginVersion":"1.0.0","order":2,"enabled":true,"config":{}},
                  {"stageKey":"platform.response-normalizer","capability":"RESPONSE_NORMALIZER",
                   "pluginId":"platform-core","pluginVersion":"1.0.0","order":3,"enabled":true,"config":{}}
                ]
                """);
        when(configMapper.selectList(any())).thenReturn(List.of(config));
        when(connectorMapper.selectById(88L)).thenReturn(connector);
        when(versionMapper.selectOne(any())).thenReturn(catalogVersion("vendor-http", "2.0.0"));
        ConnectorPluginCatalogServiceImpl service = catalogService(
                versionMapper, connectorMapper, configMapper);

        var artifacts = service.requiredArtifacts();

        assertEquals(1, artifacts.size());
        assertEquals("vendor-http", artifacts.getFirst().pluginId());
        verify(versionMapper, org.mockito.Mockito.times(1)).selectOne(any());
    }

    @Test
    void requiredArtifactsRejectsWrongPlatformCoreVersionWithoutCatalogLookup() {
        VendorConfigMapper configMapper = mock(VendorConfigMapper.class);
        VendorConnectorVersionMapper connectorMapper = mock(VendorConnectorVersionMapper.class);
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        when(configMapper.selectList(any())).thenReturn(List.of(activeConfig(77L, 88L)));
        when(connectorMapper.selectById(88L)).thenReturn(activePipeline(77L, """
                [{"stageKey":"platform.transport","capability":"TRANSPORT",
                  "pluginId":"platform-core","pluginVersion":"1.0.1","order":1,"enabled":true,"config":{}}]
                """));
        ConnectorPluginCatalogServiceImpl service = catalogService(
                versionMapper, connectorMapper, configMapper);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                service::requiredArtifacts);

        assertEquals("PLATFORM_CORE_VERSION_INVALID", error.getMessage());
        verify(versionMapper, never()).selectOne(any());
    }

    private VendorConnectorVersion activeBinding(Long vendorConfigId, String pluginId) {
        VendorConnectorVersion version = new VendorConnectorVersion();
        version.setVendorConfigId(vendorConfigId);
        version.setStatus("ACTIVE");
        version.setPipelineSnapshot("[{\"stageKey\":\"transport\",\"capability\":\"TRANSPORT\","
                + "\"pluginId\":\"" + pluginId + "\",\"pluginVersion\":\"1.0.0\","
                + "\"order\":1,\"enabled\":true,\"config\":{}}]");
        return version;
    }

    private VendorConfig activeConfig(Long id, Long versionId) {
        VendorConfig config = new VendorConfig();
        config.setId(id);
        config.setStatus(com.dataplatform.common.enums.CommonStatus.ACTIVE);
        config.setDeleted(false);
        config.setRuntimeMode("PLUGIN");
        config.setActiveConnectorVersionId(versionId);
        return config;
    }

    private VendorConnectorVersion activePipeline(Long vendorConfigId, String pipeline) {
        VendorConnectorVersion version = new VendorConnectorVersion();
        version.setId(88L);
        version.setVendorConfigId(vendorConfigId);
        version.setStatus("ACTIVE");
        version.setPipelineSnapshot(pipeline);
        return version;
    }

    private ConnectorPluginVersion catalogVersion(String pluginId, String versionValue) {
        ConnectorPluginVersion version = new ConnectorPluginVersion();
        version.setPluginId(pluginId);
        version.setVersion(versionValue);
        version.setSpiVersion("1.0");
        version.setEntryClass("example.VendorPlugin");
        version.setArtifactUri("https://repo.example/vendor.jar");
        version.setArtifactSha256("a".repeat(64));
        version.setDetachedSignature("signature");
        version.setSigningKeyId("key-1");
        version.setManifestJson("""
                {"manifestVersion":"1","pluginId":"%s","version":"%s","spiVersion":"1.0",
                 "displayName":"Vendor","provider":"internal","entryClass":"example.VendorPlugin",
                 "capabilities":["REQUEST_BUILDER"],"minHostVersion":"1.0.0",
                 "configSchema":{"type":"object"},
                 "permissions":{"networkProtocols":[],"networkHosts":[]}}
                """.formatted(pluginId, versionValue));
        version.setConfigSchemaJson("{\"type\":\"object\"}");
        version.setCapabilities("[\"REQUEST_BUILDER\"]");
        version.setPermissionManifest("{\"networkProtocols\":[],\"networkHosts\":[]}");
        version.setMinHostVersion("1.0.0");
        version.setManifestVersion("1");
        version.setAuthoringModel("ADVANCED_PIPELINE");
        version.setStatus("ACTIVE");
        return version;
    }

    private ConnectorPluginCatalogServiceImpl catalogService(
            ConnectorPluginVersionMapper versionMapper,
            VendorConnectorVersionMapper connectorMapper,
            VendorConfigMapper configMapper) {
        return new ConnectorPluginCatalogServiceImpl(
                mock(ConnectorPluginMapper.class), versionMapper, connectorMapper, configMapper,
                mock(VendorConnectorTestFactMapper.class), mock(PluginArtifactVerifier.class),
                mock(ConnectorPluginActivationInternalFeignClient.class), releaseCoordinator(),
                new ObjectMapper());
    }

    private ConnectorPluginCatalogServiceImpl service(
            ConnectorPluginVersionMapper versionMapper,
            VendorConnectorTestFactMapper factMapper,
            PluginArtifactVerifier artifactVerifier,
            ConnectorPluginActivationInternalFeignClient activationClient) {
        return new ConnectorPluginCatalogServiceImpl(
                mock(ConnectorPluginMapper.class), versionMapper,
                mock(VendorConnectorVersionMapper.class),
                mock(com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper.class), factMapper,
                artifactVerifier, activationClient, releaseCoordinator(), new ObjectMapper());
    }

    private com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator releaseCoordinator() {
        return mock(com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator.class);
    }

    private ConnectorPluginVersion failedVersion(String status) {
        ConnectorPluginVersion version = new ConnectorPluginVersion();
        version.setId(11L);
        version.setPluginId("demo");
        version.setVersion("1.0.0");
        version.setArtifactUri("https://repo.example/demo.jar");
        version.setArtifactSha256("sha256");
        version.setDetachedSignature("signature");
        version.setSigningKeyId("key-1");
        version.setCapabilities("[]");
        version.setManifestVersion("1");
        version.setAuthoringModel("ADVANCED_PIPELINE");
        version.setStatus(status);
        version.setSafeErrorCode("ACCESS_PRELOAD_FAILED");
        version.setSafeErrorDigest("old-error-digest");
        return version;
    }

    private VerifiedPluginArtifact verifiedArtifact() {
        return new VerifiedPluginArtifact("demo", "1.0.0", "1.0", "Demo", "internal", null,
                "com.example.DemoPlugin", "https://repo.example/demo.jar", "sha256", "signature", "key-1",
                "{}", "{}", List.of("TRANSPORT"), "{}", "1.0.0", new ObjectMapper().createObjectNode());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaUpdateWrapper<ConnectorPluginVersion> captureLastUpdate(
            ConnectorPluginVersionMapper versionMapper) {
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(versionMapper, org.mockito.Mockito.atLeastOnce()).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper> updates = captor.getAllValues();
        return updates.get(updates.size() - 1);
    }

    private void assertClearsSafeErrors(LambdaUpdateWrapper<ConnectorPluginVersion> update) {
        assertTrue(update.getSqlSet().contains("safe_error_code"));
        assertTrue(update.getSqlSet().contains("safe_error_digest"));
        long nullAssignments = update.getParamNameValuePairs().values().stream()
                .filter(java.util.Objects::isNull).count();
        assertTrue(nullAssignments >= 2, "安全错误列必须显式赋值NULL");
    }
}
