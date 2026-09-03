package com.dataplatform.masterdata.connector.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.api.Result;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorSaveDraftRequestDTO;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorTestFact;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorTestFactMapper;
import com.dataplatform.masterdata.connector.service.ConnectorConfigSchemaValidator;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.ConnectorLegacyWriteRetiredException;
import com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator;
import com.dataplatform.masterdata.connector.config.ConnectorPluginProperties;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class VendorConnectorServiceImplTest {
    private final VendorConnectorVersionMapper connectorMapper = mock(VendorConnectorVersionMapper.class);
    private final ConnectorPluginVersionMapper pluginMapper = mock(ConnectorPluginVersionMapper.class);
    private final VendorConfigMapper vendorConfigMapper = mock(VendorConfigMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VendorConnectorTestFactMapper testFactMapper = mock(VendorConnectorTestFactMapper.class);
    private ConnectorPluginActivationInternalFeignClient activationClient;
    private VendorConnectorRuntimeInternalFeignClient runtimeClient;
    private ConnectorPluginReleaseCoordinator releaseCoordinator;
    private ConnectorPluginVersion pluginVersion;
    private ConnectorPluginProperties connectorPluginProperties;
    private VendorConnectorServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        activationClient = mock(ConnectorPluginActivationInternalFeignClient.class);
        runtimeClient = mock(VendorConnectorRuntimeInternalFeignClient.class);
        releaseCoordinator = mock(ConnectorPluginReleaseCoordinator.class);
        connectorPluginProperties = new ConnectorPluginProperties();
        service = new VendorConnectorServiceImpl(connectorMapper, pluginMapper, vendorConfigMapper,
                new ConnectorConfigSchemaValidator(objectMapper),
                mock(com.dataplatform.masterdata.connector.service.ConnectorSecretReferenceService.class),
                releaseCoordinator,
                runtimeClient,
                activationClient, testFactMapper, objectMapper, connectorPluginProperties);
        VendorConfig vendorConfig = new VendorConfig();
        vendorConfig.setId(7L);
        vendorConfig.setSecurityVersion(3);
        vendorConfig.setConnectorVersion(0);
        when(vendorConfigMapper.selectById(7L)).thenReturn(vendorConfig);

        pluginVersion = new ConnectorPluginVersion();
        pluginVersion.setPluginId("demo-http");
        pluginVersion.setVersion("1.0.0");
        pluginVersion.setStatus("ACTIVE");
        pluginVersion.setCapabilities("[\"TRANSPORT\"]");
        pluginVersion.setArtifactSha256("a".repeat(64));
        pluginVersion.setManifestJson("{}");
        pluginVersion.setConfigSchemaJson("{\"type\":\"object\",\"properties\":{\"endpoint\":{\"type\":\"string\"}}}");
        when(pluginMapper.selectOne(any())).thenReturn(pluginVersion);
        when(connectorMapper.insert(any(VendorConnectorVersion.class))).thenReturn(1);
        when(connectorMapper.updateById(any(VendorConnectorVersion.class))).thenReturn(1);
        when(connectorMapper.update(any(), any())).thenReturn(1);
        when(testFactMapper.insert(any(VendorConnectorTestFact.class))).thenReturn(1);
    }

    @Test
    void validatesOneActiveTransportAndProducesSnapshotHash() throws Exception {
        VendorConnectorVersion draft = draft(List.of(step("transport-a", 100),
                new ConnectorPipelineStepDTO("disabled", "TRANSPORT", "demo-http", "1.0.0",
                        200, false, Map.of(), null)));
        when(connectorMapper.selectOne(any())).thenReturn(draft);

        var result = service.validate(7L);

        assertTrue(result.valid());
        assertNotNull(result.snapshotHash());
    }

    @Test
    void rejectsMultipleEnabledTransports() throws Exception {
        VendorConnectorVersion draft = draft(List.of(step("transport-a", 100), step("transport-b", 200)));
        when(connectorMapper.selectOne(any())).thenReturn(draft);

        var result = service.validate(7L);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("恰好包含一个TRANSPORT")));
    }

    @Test
    void rollbackDoesNotMutatePointersBeforeAllAccessInstancesAreReady() throws Exception {
        VendorConnectorVersion target = draft(List.of(step("transport-a", 100)));
        target.setStatus("SUPERSEDED");
        target.setVersionNo(1);
        when(connectorMapper.selectOne(any())).thenReturn(target);
        ConnectorPluginActivationSummaryDTO summary = new ConnectorPluginActivationSummaryDTO();
        summary.setReady(false);
        when(activationClient.stage(any())).thenReturn(Result.success(summary));

        assertThrows(ConnectorConflictException.class,
                () -> service.rollback(7L, 1, 0, 9L));

        verify(connectorMapper, never()).insert(any(VendorConnectorVersion.class));
        verify(vendorConfigMapper, never()).update(any(), any());
    }

    @Test
    void rawRollbackKeepsBuiltInLegacyHttpInProcessWithoutStagingIt() throws Exception {
        ConnectorPipelineStepDTO transport = new ConnectorPipelineStepDTO(
                "transport", "TRANSPORT", "legacy-http", "1.0.0", 100, true,
                Map.of("endpoint", "https://api.example.com"), null);

        ReflectionTestUtils.invokeMethod(service, "ensureRollbackPluginsReady", List.of(transport));

        verify(activationClient, never()).stage(any());
    }

    @Test
    void nullOptionalConfigValueDoesNotBreakNormalization() throws Exception {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("endpoint", null);
        ConnectorPipelineStepDTO step = new ConnectorPipelineStepDTO(
                "transport-a", "TRANSPORT", "demo-http", "1.0.0", 100, true, config, null);
        when(connectorMapper.selectOne(any())).thenReturn(draft(List.of(step)));

        var result = service.validate(7L);

        assertFalse(result.valid());
    }

    @Test
    void controlledTestPersistsPayloadFreeFactBoundToDraftAndPlugins() throws Exception {
        pluginVersion.setStatus("STAGING");
        when(connectorMapper.selectOne(any())).thenReturn(draft(List.of(step("transport-a", 100))));
        VendorConnectorTestRespDTO response = new VendorConnectorTestRespDTO();
        response.setSuccess(true);
        response.setNormalizedData(Map.of("ok", true));
        response.setStageTimings(List.of());
        when(runtimeClient.test(any())).thenReturn(Result.success(response));

        service.test(7L, null, 99L);

        ArgumentCaptor<VendorConnectorTestFact> fact = ArgumentCaptor.forClass(VendorConnectorTestFact.class);
        verify(testFactMapper).insert(fact.capture());
        assertTrue(fact.getValue().getTestSucceeded());
        assertEquals(1, fact.getValue().getDraftVersion());
        assertEquals(64, fact.getValue().getSnapshotHash().length());
        assertTrue(fact.getValue().getPluginBindings().contains("demo-http:1.0.0"));
        assertEquals(99L, fact.getValue().getTestedBy());
        assertEquals(64, fact.getValue().getResultDigest().length());
        assertEquals("ADVANCED_LEGACY", fact.getValue().getAuthoringMode());
        assertNull(fact.getValue().getSpecHash());
        assertNull(fact.getValue().getCompileHash());
    }

    @Test
    void publishGateRejectsFactFromDifferentDraftOrSnapshot() {
        VendorConnectorTestFact stale = new VendorConnectorTestFact();
        stale.setDraftVersion(1);
        stale.setSnapshotHash("b".repeat(64));
        stale.setTestSucceeded(true);
        when(testFactMapper.selectList(any())).thenReturn(List.of(stale));

        assertThrows(ConnectorConflictException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "requireSuccessfulTestFact", 7L, 2, "a".repeat(64)));
    }

    @Test
    void savesDraftTwiceAndIncrementsVersionThroughJsonbSafeCasUpdate() throws Exception {
        AtomicReference<VendorConnectorVersion> stored = new AtomicReference<>();
        VendorConnectorVersion existing = draft(List.of(step("transport-a", 100)));
        existing.setId(11L);
        stored.set(existing);
        when(connectorMapper.selectOne(any())).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            VendorConnectorVersion inserted = invocation.getArgument(0);
            inserted.setId(11L);
            stored.set(inserted);
            return 1;
        }).when(connectorMapper).insert(any(VendorConnectorVersion.class));
        when(connectorMapper.updateDraft(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    VendorConnectorVersion current = stored.get();
                    Long id = invocation.getArgument(0);
                    Integer expectedVersion = invocation.getArgument(1);
                    if (current == null || !id.equals(current.getId())
                            || !expectedVersion.equals(current.getDraftVersion())) {
                        return 0;
                    }
                    current.setPipelineSnapshot(invocation.getArgument(2));
                    current.setSecurityVersion(invocation.getArgument(3));
                    current.setDraftVersion(invocation.getArgument(4));
                    current.setUpdatedBy(invocation.getArgument(5));
                    return 1;
                });

        var first = service.saveDraft(7L,
                new VendorConnectorSaveDraftRequestDTO(1, List.of(step("transport-a", 100))), 99L);
        ConnectorPipelineStepDTO modified = new ConnectorPipelineStepDTO(
                "transport-a", "TRANSPORT", "demo-http", "1.0.0", 100,
                true, Map.of("endpoint", "https://api.example.com/v2"), null);
        var second = service.saveDraft(7L,
                new VendorConnectorSaveDraftRequestDTO(2, List.of(modified)), 99L);

        assertEquals(2, first.draftVersion());
        assertEquals(3, second.draftVersion());
        assertEquals("https://api.example.com/v2",
                second.pipelineSnapshot().getFirst().config().get("endpoint"));
        assertEquals("ADVANCED_LEGACY", stored.get().getAuthoringMode());
        assertNull(stored.get().getConnectorSpec());
        assertNull(stored.get().getSpecHash());
        assertNull(stored.get().getCompilerVersion());
        assertNull(stored.get().getCompileHash());
        verify(connectorMapper).updateDraft(eq(11L), eq(1), any(String.class), eq(3), eq(2), eq(99L));
        verify(connectorMapper).updateDraft(eq(11L), eq(2), any(String.class), eq(3), eq(3), eq(99L));
    }

    @Test
    void immutablePublishedVersionKeepsLegacyProjectionExplicit() {
        VendorConnectorVersion version = ReflectionTestUtils.invokeMethod(service, "immutableVersion",
                7L, 2, List.of(step("transport-a", 100)), "a".repeat(64),
                "V2_EMBEDDED", "a".repeat(64), 3, 10L, 99L);

        assertNotNull(version);
        assertEquals("ADVANCED_LEGACY", version.getAuthoringMode());
        assertNull(version.getConnectorSpec());
        assertNull(version.getSpecHash());
        assertNull(version.getCompilerVersion());
        assertNull(version.getCompileHash());
    }

    @Test
    void preservesConflictWhenDraftChangesAfterReadBeforeCasUpdate() throws Exception {
        VendorConnectorVersion current = draft(List.of(step("transport-a", 100)));
        when(connectorMapper.selectOne(any())).thenReturn(current);
        when(connectorMapper.updateDraft(eq(1L), eq(1), any(String.class), eq(3), eq(2), eq(99L)))
                .thenReturn(0);

        assertThrows(ConnectorConflictException.class, () -> service.saveDraft(7L,
                new VendorConnectorSaveDraftRequestDTO(1, List.of(step("transport-a", 100))), 99L));
    }

    @Test
    void rawSaveTestAndPublishRejectSimpleDraftBeforeNormalizationOrAnyWrite() throws Exception {
        VendorConnectorVersion simple = simpleDraft();
        when(connectorMapper.selectOne(any())).thenReturn(simple);
        String connectorSpec = simple.getConnectorSpec();
        String specHash = simple.getSpecHash();
        String compileHash = simple.getCompileHash();
        String pipeline = simple.getPipelineSnapshot();

        assertProductApiRequired(() -> service.saveDraft(7L,
                new VendorConnectorSaveDraftRequestDTO(1, List.of(step("transport-a", 100))), 99L));
        assertProductApiRequired(() -> service.test(7L, null, 99L));
        assertProductApiRequired(() -> service.publish(7L, 1, 99L));

        verify(connectorMapper, never()).updateDraft(any(), any(), any(), any(), any(), any());
        verify(connectorMapper, never()).insert(any(VendorConnectorVersion.class));
        verify(vendorConfigMapper, never()).update(any(), any());
        verifyNoInteractions(runtimeClient, activationClient, testFactMapper, releaseCoordinator);
        assertEquals(connectorSpec, simple.getConnectorSpec());
        assertEquals(specHash, simple.getSpecHash());
        assertEquals(compileHash, simple.getCompileHash());
        assertEquals(pipeline, simple.getPipelineSnapshot());
        assertEquals("SIMPLE_CONNECTOR", simple.getAuthoringMode());
    }

    @Test
    void rawSaveCannotCreateAFromScratchLegacyDraft() {
        when(connectorMapper.selectOne(any())).thenReturn(null);

        ConnectorConflictException exception = assertThrows(ConnectorConflictException.class,
                () -> service.saveDraft(7L,
                        new VendorConnectorSaveDraftRequestDTO(0,
                                List.of(step("transport-a", 100))), 99L));

        assertEquals("LEGACY_DRAFT_REQUIRED", exception.getMessage());
        verify(connectorMapper, never()).insert(any(VendorConnectorVersion.class));
        verify(connectorMapper, never()).updateDraft(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(pluginMapper, runtimeClient, activationClient, testFactMapper, releaseCoordinator);
    }

    @Test
    void rawWriteReturnsGoneAfterRetirementFactsPass() {
        connectorPluginProperties.setLegacyWriteRetired(true);
        VendorConnectorVersionMapper.LegacyWriteRetirementFacts facts =
                new VendorConnectorVersionMapper.LegacyWriteRetirementFacts();
        facts.setActiveLegacyBindings(0L);
        facts.setLegacyDrafts(0L);
        facts.setOpenMigrations(0L);
        when(connectorMapper.legacyWriteRetirementFacts()).thenReturn(facts);

        ConnectorLegacyWriteRetiredException exception = assertThrows(
                ConnectorLegacyWriteRetiredException.class,
                () -> service.saveDraft(7L,
                        new VendorConnectorSaveDraftRequestDTO(0,
                                List.of(step("transport-a", 100))), 99L));

        assertEquals("CONNECTOR_LEGACY_WRITE_RETIRED", exception.getMessage());
        verify(connectorMapper).legacyWriteRetirementFacts();
        verify(vendorConfigMapper, never()).selectById(7L);
    }

    @Test
    void rawWriteRemainsBlockedUntilRetirementFactsPass() {
        connectorPluginProperties.setLegacyWriteRetired(true);
        VendorConnectorVersionMapper.LegacyWriteRetirementFacts facts =
                new VendorConnectorVersionMapper.LegacyWriteRetirementFacts();
        facts.setActiveLegacyBindings(1L);
        facts.setLegacyDrafts(0L);
        facts.setOpenMigrations(0L);
        when(connectorMapper.legacyWriteRetirementFacts()).thenReturn(facts);

        ConnectorConflictException exception = assertThrows(ConnectorConflictException.class,
                () -> service.saveDraft(7L,
                        new VendorConnectorSaveDraftRequestDTO(0,
                                List.of(step("transport-a", 100))), 99L));

        assertEquals("CONNECTOR_LEGACY_WRITE_RETIREMENT_GATE_NOT_PASSED", exception.getMessage());
        verify(vendorConfigMapper, never()).selectById(7L);
    }

    @Test
    void rawRollbackRejectsSimpleHistoryBeforeStageOrPersistence() throws Exception {
        VendorConnectorVersion simple = simpleDraft();
        simple.setStatus("SUPERSEDED");
        simple.setVersionNo(2);
        simple.setDraftVersion(0);
        when(connectorMapper.selectOne(any())).thenReturn(simple);

        assertProductApiRequired(() -> service.rollback(7L, 2, 0, 99L));

        verify(connectorMapper, never()).insert(any(VendorConnectorVersion.class));
        verify(connectorMapper, never()).updateById(any(VendorConnectorVersion.class));
        verify(vendorConfigMapper, never()).update(any(), any());
        verifyNoInteractions(runtimeClient, activationClient, testFactMapper, releaseCoordinator);
        assertEquals("SIMPLE_CONNECTOR", simple.getAuthoringMode());
        assertNotNull(simple.getConnectorSpec());
        assertNotNull(simple.getSpecHash());
        assertNotNull(simple.getCompileHash());
    }

    @Test
    void rawUpdateDraftSqlCanOnlyMutateAdvancedLegacyDrafts() throws Exception {
        String sql = VendorConnectorVersionMapper.class.getMethod("updateDraft", Long.class,
                        Integer.class, String.class, Integer.class, Integer.class, Long.class)
                .getAnnotation(org.apache.ibatis.annotations.Update.class).value()[0];
        assertTrue(sql.contains("status = 'DRAFT'"));
        assertTrue(sql.contains("authoring_mode = 'ADVANCED_LEGACY'"));
    }

    private VendorConnectorVersion draft(List<ConnectorPipelineStepDTO> pipeline) throws Exception {
        VendorConnectorVersion draft = new VendorConnectorVersion();
        draft.setId(1L);
        draft.setVendorConfigId(7L);
        draft.setStatus("DRAFT");
        draft.setDraftVersion(1);
        draft.setAuthoringMode("ADVANCED_LEGACY");
        draft.setPipelineSnapshot(objectMapper.writeValueAsString(pipeline));
        return draft;
    }

    private VendorConnectorVersion simpleDraft() throws Exception {
        VendorConnectorVersion draft = draft(List.of(step("transport-a", 100)));
        draft.setAuthoringMode("SIMPLE_CONNECTOR");
        draft.setConnectorSpec("{\"specVersion\":\"1\",\"config\":{}}");
        draft.setSpecHash("b".repeat(64));
        draft.setCompilerVersion("1.0.0");
        draft.setCompileHash("c".repeat(64));
        return draft;
    }

    private void assertProductApiRequired(org.junit.jupiter.api.function.Executable executable) {
        ConnectorConflictException exception = assertThrows(ConnectorConflictException.class, executable);
        assertEquals("SIMPLE_CONNECTOR_REQUIRES_PRODUCT_API", exception.getMessage());
    }

    private ConnectorPipelineStepDTO step(String key, int order) {
        return new ConnectorPipelineStepDTO(key, "TRANSPORT", "demo-http", "1.0.0", order,
                true, Map.of("endpoint", "https://api.example.com"), null);
    }
}
