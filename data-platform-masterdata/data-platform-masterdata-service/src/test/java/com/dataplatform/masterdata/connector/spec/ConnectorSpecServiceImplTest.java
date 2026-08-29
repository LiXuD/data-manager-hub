package com.dataplatform.masterdata.connector.spec;

import static com.dataplatform.masterdata.connector.fixture.ConnectorProductModelFixtures.singleHttpLegacyPipeline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginStageReqDTO;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConvertRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecPublishRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecRollbackRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecSaveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecTestRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewRequestDTO;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator;
import com.dataplatform.masterdata.connector.service.LegacyHttpSpecConverter;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorSpecServiceImplTest {

    private static final long CONFIG_ID = 42L;
    private static final long VENDOR_ID = 7L;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private ConnectorSpecFactsMapper factsMapper;
    private ConnectorSpecDraftMapper draftMapper;
    private ConnectorSpecLifecycleMapper lifecycleMapper;
    private ConnectorSpecPublishMapper publishMapper;
    private VendorConnectorRuntimeInternalFeignClient runtimeClient;
    private ConnectorPluginActivationInternalFeignClient activationClient;
    private ConnectorPluginReleaseCoordinator releaseCoordinator;
    private ConnectorSpecServiceImpl service;

    @BeforeEach
    void setUp() {
        factsMapper = mock(ConnectorSpecFactsMapper.class);
        draftMapper = mock(ConnectorSpecDraftMapper.class);
        lifecycleMapper = mock(ConnectorSpecLifecycleMapper.class);
        publishMapper = mock(ConnectorSpecPublishMapper.class);
        runtimeClient = mock(VendorConnectorRuntimeInternalFeignClient.class);
        activationClient = mock(ConnectorPluginActivationInternalFeignClient.class);
        releaseCoordinator = mock(ConnectorPluginReleaseCoordinator.class);
        service = new ConnectorSpecServiceImpl(factsMapper, draftMapper, lifecycleMapper, publishMapper,
                runtimeClient, activationClient, releaseCoordinator, mapper,
                new ConnectorSpecMetrics(new SimpleMeterRegistry()), new LegacyHttpSpecConverter());
        when(factsMapper.findVendorFacts(CONFIG_ID)).thenReturn(vendorFacts());
        when(factsMapper.findOwnedSecretRefs(VENDOR_ID)).thenReturn(
                List.of("vendor.fixture.token", "vendor.secretKey"));
        when(activationClient.stage(any())).thenAnswer(invocation ->
                readyActivation(invocation.getArgument(0)));
    }

    private Result<ConnectorPluginActivationSummaryDTO> readyActivation(
            ConnectorPluginStageReqDTO request) {
        ConnectorPluginActivationSummaryDTO summary = new ConnectorPluginActivationSummaryDTO();
        summary.setPluginId(request.getPluginId());
        summary.setPluginVersion(request.getPluginVersion());
        summary.setReady(true);
        ConnectorPluginActivationDTO first = new ConnectorPluginActivationDTO();
        first.setServiceInstanceId("access-1");
        first.setPluginId(request.getPluginId());
        first.setPluginVersion(request.getPluginVersion());
        first.setState("READY");
        ConnectorPluginActivationDTO second = new ConnectorPluginActivationDTO();
        second.setServiceInstanceId("access-2");
        second.setPluginId(request.getPluginId());
        second.setPluginVersion(request.getPluginVersion());
        second.setState("READY");
        summary.setInstances(List.of(first, second));
        return Result.success(summary);
    }

    @Test
    void catalogFiltersSortsRecommendsActiveAndRejectsSignedProjectionDrift() {
        ConnectorPluginVersion vendorActive = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        ConnectorPluginVersion vendorStaging = plugin("vendor-exact", "3.0.0", "STAGING",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        ConnectorPluginVersion dataType = plugin("datatype-exact", "9.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of(), List.of("COMPANY"));
        ConnectorPluginVersion generic = plugin("generic-http", "1.0.0", "ACTIVE",
                ConnectorKind.GENERIC_HTTP, List.of("*"), List.of("*"));
        ConnectorPluginVersion incompatible = plugin("other-vendor", "1.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("OTHER"), List.of());
        List<ConnectorPluginVersion> versions = new ArrayList<>(List.of(
                generic, incompatible, dataType, vendorStaging, vendorActive));
        when(factsMapper.findSimpleCatalogVersions()).thenReturn(versions);
        versions.forEach(this::stubCatalogFacts);

        var catalog = service.catalog(CONFIG_ID);

        assertEquals(List.of("vendor-exact", "datatype-exact", "generic-http"),
                catalog.plugins().stream().map(item -> item.pluginId()).toList());
        assertEquals("2.0.0", catalog.plugins().getFirst().recommendedVersion());
        assertEquals(2, catalog.plugins().getFirst().versionCount());
        assertFalse(catalog.plugins().stream()
                .anyMatch(item -> "other-vendor".equals(item.pluginId())));
        assertEquals(List.of("2.0.0", "3.0.0"), service.versions(CONFIG_ID, "vendor-exact")
                .stream().map(item -> item.pluginVersion()).toList());

        vendorActive.setOutputMode("PLUGIN_NORMALIZED");
        assertThrows(IllegalStateException.class, () -> service.catalog(CONFIG_ID));

        vendorActive.setOutputMode("HOST_MAPPING");
        ConnectorSpecFactsMapper.CatalogPluginFacts drifted =
                new ConnectorSpecFactsMapper.CatalogPluginFacts();
        drifted.setPluginId("vendor-exact");
        drifted.setDisplayName("unsigned drift");
        drifted.setProvider("test");
        drifted.setStatus("ACTIVE");
        when(factsMapper.findCatalogPlugin("vendor-exact")).thenReturn(drifted);
        assertThrows(IllegalStateException.class, () -> service.catalog(CONFIG_ID));
    }

    @Test
    void legacyDraftSaveConflictsWithoutAnyWrite() {
        VendorConnectorVersion legacy = new VendorConnectorVersion();
        legacy.setId(99L);
        legacy.setVendorConfigId(CONFIG_ID);
        legacy.setDraftVersion(4);
        legacy.setStatus("DRAFT");
        legacy.setAuthoringMode("ADVANCED_LEGACY");
        legacy.setPipelineSnapshot("[]");
        when(factsMapper.findDraft(CONFIG_ID)).thenReturn(legacy);
        ConnectorSpecDTO spec = new ConnectorSpecDTO("1",
                new ConnectorSpecDTO.PluginRef("vendor-exact", "2.0.0"), Map.of(), null);

        ConnectorConflictException error = assertThrows(ConnectorConflictException.class,
                () -> service.saveDraft(CONFIG_ID, new ConnectorSpecSaveRequestDTO(4, spec), 8L));

        assertEquals("LEGACY_PIPELINE_REQUIRES_CONVERSION", error.getMessage());
        verifyNoInteractions(draftMapper);
    }

    @Test
    void conversionPreviewIsReadOnlyAndReturnsSafeDeterministicSpecOrReasons() throws Exception {
        VendorConnectorVersion legacy = legacyDraft(singleHttpLegacyPipeline());
        when(factsMapper.findDraft(CONFIG_ID)).thenReturn(legacy);

        var preview = service.convertPreview(CONFIG_ID);

        assertTrue(preview.convertible());
        assertNull(preview.errorCode());
        assertEquals("generic-http", preview.connectorSpec().getPlugin().getPluginId());
        assertEquals("vendor.fixture.token",
                ((Map<?, ?>) preview.connectorSpec().getConfig().get("auth")).get("tokenRef"));
        verifyNoInteractions(draftMapper, lifecycleMapper, runtimeClient, activationClient, releaseCoordinator);

        List<ConnectorPipelineStepDTO> unsafe = replaceLegacyConfig(singleHttpLegacyPipeline(), 0,
                Map.of("apiUrl", "https://fixture.example.test/api?secret=hidden"));
        legacy.setPipelineSnapshot(mapper.writeValueAsString(unsafe));
        var rejected = service.convertPreview(CONFIG_ID);
        assertFalse(rejected.convertible());
        assertEquals("LEGACY_PIPELINE_NOT_CONVERTIBLE", rejected.errorCode());
        assertFalse(mapper.writeValueAsString(rejected).contains("secret=hidden"));
        verifyNoInteractions(draftMapper);
    }

    @Test
    void conversionRejectsStaleOrNonConvertibleDraftWithoutWrite() throws Exception {
        VendorConnectorVersion legacy = legacyDraft(singleHttpLegacyPipeline());
        when(publishMapper.lockControl(CONFIG_ID)).thenReturn(control(2, 77L));
        when(publishMapper.lockDraft(CONFIG_ID)).thenReturn(legacy);

        assertEquals("连接器草稿版本冲突", assertThrows(ConnectorConflictException.class,
                () -> service.convert(CONFIG_ID, new ConnectorSpecConvertRequestDTO(3), 9L))
                .getMessage());
        verifyNoInteractions(draftMapper);

        legacy.setPipelineSnapshot(mapper.writeValueAsString(replaceLegacyConfig(
                singleHttpLegacyPipeline(), 0, Map.of("method", "HEAD"))));
        LegacyPipelineNotConvertibleException rejected = assertThrows(
                LegacyPipelineNotConvertibleException.class,
                () -> service.convert(CONFIG_ID, new ConnectorSpecConvertRequestDTO(4), 9L));
        assertEquals("LEGACY_PIPELINE_NOT_CONVERTIBLE", rejected.getMessage());
        assertFalse(rejected.preview().reasons().isEmpty());
        assertNull(rejected.preview().connectorSpec());
        verifyNoInteractions(draftMapper);
        assertEquals("ADVANCED_LEGACY", legacy.getAuthoringMode());
        assertNull(legacy.getConnectorSpec());
    }

    @Test
    void conversionCasWritesOneSimpleDraftAndPreservesActiveBinding() throws Exception {
        ConnectorPluginVersion generic = genericPlugin();
        // PostgreSQL JSONB retrieval is not a canonical serialization; the compiler must
        // compare signed compatibility facts semantically while passing canonical JSON onward.
        generic.setCompatibilityManifest("{\"vendorCodes\":[\"*\"], \"dataTypeCodes\":[\"*\"]}");
        stubGenericPlugin(generic);
        ConnectorSpecFactsMapper.VendorFacts vendor = vendorFacts();
        vendor.setActiveConnectorVersionId(77L);
        when(factsMapper.findVendorFacts(CONFIG_ID)).thenReturn(vendor);
        VendorConnectorVersion legacy = legacyDraft(singleHttpLegacyPipeline());
        AtomicReference<VendorConnectorVersion> stored = new AtomicReference<>(legacy);
        when(publishMapper.lockControl(CONFIG_ID)).thenReturn(control(2, 77L));
        when(publishMapper.lockDraft(CONFIG_ID)).thenReturn(legacy);
        when(factsMapper.findDraft(CONFIG_ID)).thenAnswer(ignored -> stored.get());
        when(draftMapper.convertLegacyDraft(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenAnswer(invocation -> {
            VendorConnectorVersion simple = new VendorConnectorVersion();
            simple.setId(invocation.getArgument(0));
            simple.setVendorConfigId(invocation.getArgument(1));
            simple.setDraftVersion(invocation.getArgument(3));
            simple.setPipelineSnapshot(invocation.getArgument(4));
            simple.setAuthoringMode("SIMPLE_CONNECTOR");
            simple.setConnectorSpec(invocation.getArgument(5));
            simple.setSpecHash(invocation.getArgument(6));
            simple.setCompilerVersion(invocation.getArgument(7));
            simple.setCompileHash(invocation.getArgument(8));
            simple.setSecurityVersion(invocation.getArgument(9));
            simple.setStatus("DRAFT");
            stored.set(simple);
            return 1;
        });

        var converted = service.convert(CONFIG_ID, new ConnectorSpecConvertRequestDTO(4), 9L);

        assertEquals("SIMPLE_CONNECTOR", converted.authoringMode());
        assertEquals(5, converted.draftVersion());
        assertEquals("generic-http", converted.connectorSpec().getPlugin().getPluginId());
        assertNotNull(converted.specHash());
        verify(draftMapper, times(1)).convertLegacyDraft(eq(99L), eq(CONFIG_ID), eq(4), eq(5),
                any(), any(), any(), eq("1.0.0"), any(), eq(0), eq(9L));
        verify(publishMapper, never()).casActivePointer(any(), any(), any(), any(), any());
        verifyNoInteractions(runtimeClient, lifecycleMapper, releaseCoordinator);
        assertEquals(77L, vendor.getActiveConnectorVersionId());
    }

    @Test
    void executionPlanUsesDraftAndRedactsConfigsWhileClassifyingSources() throws Exception {
        VendorConnectorVersion draft = new VendorConnectorVersion();
        draft.setId(81L);
        draft.setVendorConfigId(CONFIG_ID);
        draft.setDraftVersion(3);
        draft.setStatus("DRAFT");
        draft.setAuthoringMode("ADVANCED_LEGACY");
        draft.setSnapshotHash("f".repeat(64));
        draft.setPipelineSnapshot(mapper.writeValueAsString(List.of(
                step("connector.request-builder", "REQUEST_BUILDER", "vendor-exact", 100),
                step("platform.security.request.000", "REQUEST_PROCESSOR", "platform-core", 200),
                step("platform.transport", "TRANSPORT", "platform-core", 300),
                step("platform.response-normalizer", "RESPONSE_NORMALIZER", "platform-core", 400))));
        when(factsMapper.findDraft(CONFIG_ID)).thenReturn(draft);

        var plan = service.executionPlan(CONFIG_ID, null);

        assertEquals(81L, plan.connectorVersionId());
        assertEquals(List.of("CONNECTOR", "PLATFORM_SECURITY", "PLATFORM_TRANSPORT",
                        "PLATFORM_NORMALIZER"),
                plan.stages().stream().map(item -> item.source()).toList());
        String response = mapper.writeValueAsString(plan);
        assertFalse(response.contains("top-secret"));
        assertFalse(response.contains("secretRef"));
        assertFalse(response.contains("\"config\""));
    }

    @Test
    void simpleDraftCreateAndCasUpdatePersistCanonicalFactsAndRejectStaleVersion() throws Exception {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();

        var created = service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);

        ArgumentCaptor<ConnectorSpecDraftMapper.DraftWrite> insert =
                ArgumentCaptor.forClass(ConnectorSpecDraftMapper.DraftWrite.class);
        verify(draftMapper).insertDraft(insert.capture());
        ConnectorSpecDraftMapper.DraftWrite row = insert.getValue();
        assertEquals(CONFIG_ID, row.getVendorConfigId());
        assertEquals(0, row.getSecurityVersion());
        assertEquals("1.0.0", row.getCompilerVersion());
        assertTrue(row.getSpecHash().matches("[0-9a-f]{64}"));
        assertTrue(row.getCompileHash().matches("[0-9a-f]{64}"));
        assertTrue(mapper.readTree(row.getConnectorSpec()).isObject());
        assertTrue(mapper.readTree(row.getPipelineSnapshot()).isArray());
        assertFalse(row.getConnectorSpec().contains("pipelineSnapshot"));
        assertEquals(1, created.draftVersion());
        assertEquals("SIMPLE_CONNECTOR", created.authoringMode());
        assertNotNull(created.compiledSnapshotHash());
        assertNull(stored.get().getSnapshotHash());
        assertNull(stored.get().getHashAlgorithm());
        assertNull(stored.get().getIntegrityHash());

        var updated = service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(1, spec(plugin, "second")), 99L);

        assertEquals(2, updated.draftVersion());
        assertEquals("second", updated.connectorSpec().getConfig().get("endpoint"));
        verify(draftMapper).updateDraft(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        assertThrows(ConnectorConflictException.class,
                () -> service.saveDraft(CONFIG_ID,
                        new ConnectorSpecSaveRequestDTO(1, spec(plugin, "stale")), 99L));
        verify(draftMapper, times(1)).updateDraft(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void securitySnapshotMissingAndForeignOwnedReferenceBothFailClosed() {
        ConnectorSpecFactsMapper.VendorFacts facts = vendorFacts();
        facts.setSecurityVersion(1);
        when(factsMapper.findVendorFacts(CONFIG_ID)).thenReturn(facts);
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> service.saveDraft(CONFIG_ID,
                        new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L));
        assertEquals("SECURITY_VERSION_SNAPSHOT_MISSING", missing.getMessage());

        when(factsMapper.findSecuritySnapshot(CONFIG_ID, 1)).thenReturn("""
                [{"stepKey":"sign","direction":"REQUEST","stepType":"HMAC",
                  "stepName":"sign","sortNo":10,"enabled":true,
                  "config":{"inputFrom":"BODY","algorithm":"HMAC_SHA256",
                            "secretRef":"foreign.secret"}}]
                """);
        IllegalArgumentException ownership = assertThrows(IllegalArgumentException.class,
                () -> service.saveDraft(CONFIG_ID,
                        new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L));
        assertTrue(ownership.getMessage().startsWith("SECRET_REF_NOT_OWNED:"));
        verifyNoInteractions(draftMapper);
    }

    @Test
    void draftAndValidationSucceedForStoredFactsThenRejectCompilationDrift() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);

        var draft = service.draft(CONFIG_ID);
        var validation = service.validate(CONFIG_ID);

        assertTrue(draft.present());
        assertEquals("first", draft.connectorSpec().getConfig().get("endpoint"));
        assertTrue(validation.valid());
        assertEquals(draft.specHash(), validation.specHash());
        assertEquals(draft.compileHash(), validation.compileHash());
        assertEquals(draft.compiledSnapshotHash(), validation.compiledSnapshotHash());

        stored.get().setCompileHash("b".repeat(64));
        var invalid = service.validate(CONFIG_ID);
        assertFalse(invalid.valid());
        assertEquals("CONNECTOR_DRAFT_FACTS_DRIFTED", invalid.errorCode());
        assertEquals("CONNECTOR_DRAFT_FACTS_DRIFTED",
                assertThrows(IllegalStateException.class, () -> service.draft(CONFIG_ID)).getMessage());
    }

    @Test
    void simplePlanRejectsTamperedPluginDigestAndPublishedSnapshotFacts() throws Exception {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);

        List<ConnectorPipelineStepDTO> steps = mapper.readValue(stored.get().getPipelineSnapshot(),
                mapper.getTypeFactory().constructCollectionType(List.class, ConnectorPipelineStepDTO.class));
        ConnectorPipelineStepDTO original = steps.getFirst();
        List<ConnectorPipelineStepDTO> tampered = new ArrayList<>(steps);
        tampered.set(0, new ConnectorPipelineStepDTO(original.stageKey(), original.capability(),
                original.pluginId(), original.pluginVersion(), original.order(), original.enabled(),
                original.config(), original.configHash(), "e".repeat(64), original.manifestHash(),
                original.schemaHash()));
        stored.get().setPipelineSnapshot(mapper.writeValueAsString(tampered));
        assertEquals("CONNECTOR_PLAN_PLUGIN_DIGEST_DRIFT",
                assertThrows(IllegalStateException.class,
                        () -> service.executionPlan(CONFIG_ID, null)).getMessage());

        stored.get().setPipelineSnapshot(mapper.writeValueAsString(steps));
        stored.get().setStatus("ACTIVE");
        stored.get().setVersionNo(1);
        stored.get().setDraftVersion(null);
        stored.get().setSnapshotHash("0".repeat(64));
        stored.get().setHashAlgorithm("V2_EMBEDDED");
        stored.get().setIntegrityHash("0".repeat(64));
        assertEquals("CONNECTOR_DRAFT_INTEGRITY_DRIFT",
                assertThrows(IllegalStateException.class,
                        () -> service.executionPlan(CONFIG_ID, null)).getMessage());
    }

    @Test
    void securitySnapshotRejectsUnknownFieldsAndAmbiguousIds() {
        ConnectorSpecFactsMapper.VendorFacts facts = vendorFacts();
        facts.setSecurityVersion(1);
        when(factsMapper.findVendorFacts(CONFIG_ID)).thenReturn(facts);
        when(factsMapper.findSecuritySnapshot(CONFIG_ID, 1)).thenReturn("""
                [{"stepKey":"sign","direction":"REQUEST","stepType":"HMAC","sortNo":10,
                  "enabled":true,"config":{},"unexpected":true}]
                """);
        assertEquals("SECURITY_VERSION_SNAPSHOT_INVALID",
                assertThrows(IllegalStateException.class, () -> service.draft(CONFIG_ID)).getMessage());

        when(factsMapper.findSecuritySnapshot(CONFIG_ID, 1)).thenReturn("""
                [{"id":"one","stepKey":"two","direction":"REQUEST","stepType":"HMAC",
                  "sortNo":10,"enabled":true,"config":{}}]
                """);
        assertEquals("SECURITY_VERSION_SNAPSHOT_INVALID",
                assertThrows(IllegalStateException.class, () -> service.draft(CONFIG_ID)).getMessage());
    }

    @Test
    void controlledTestSendsDefensiveV2PlanAndPersistsCompleteAtomicFact() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "STAGING",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        installDraftPersistence();
        plugin.setStatus("ACTIVE");
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);
        plugin.setStatus("STAGING");
        VendorConnectorTestRespDTO response = validResponse(true);
        when(runtimeClient.test(any())).thenReturn(Result.success(response));
        when(lifecycleMapper.insertTestFact(any())).thenReturn(1);
        LinkedHashMap<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("nullable", null);
        ArrayList<Object> nestedList = new ArrayList<>();
        nestedList.add("one");
        nestedList.add(null);
        nestedMap.put("items", nestedList);
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("company", nestedMap);

        var result = service.test(CONFIG_ID, new ConnectorSpecTestRequestDTO(params), 99L);

        assertTrue(result.success());
        ArgumentCaptor<com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO> access =
                ArgumentCaptor.forClass(com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO.class);
        verify(runtimeClient).test(access.capture());
        assertEquals("V2_EMBEDDED", access.getValue().getHashAlgorithm());
        assertEquals(access.getValue().getSnapshotHash(), access.getValue().getIntegrityHash());
        assertFalse(access.getValue().getPipelineSnapshot().isEmpty());
        params.put("late", "mutation");
        nestedMap.put("lateNested", "mutation");
        nestedList.add("mutation");
        assertFalse(access.getValue().getParams().containsKey("late"));
        @SuppressWarnings("unchecked") Map<String, Object> sentNested =
                (Map<String, Object>) access.getValue().getParams().get("company");
        @SuppressWarnings("unchecked") List<Object> sentList =
                (List<Object>) sentNested.get("items");
        assertFalse(sentNested.containsKey("lateNested"));
        assertEquals(java.util.Arrays.asList("one", null), new ArrayList<>(sentList));
        assertThrows(UnsupportedOperationException.class,
                () -> sentNested.put("blocked", true));
        assertThrows(UnsupportedOperationException.class, () -> sentList.add("blocked"));

        ArgumentCaptor<ConnectorSpecLifecycleMapper.TestFactWrite> fact =
                ArgumentCaptor.forClass(ConnectorSpecLifecycleMapper.TestFactWrite.class);
        verify(lifecycleMapper).insertTestFact(fact.capture());
        assertEquals(CONFIG_ID, fact.getValue().getVendorConfigId());
        assertEquals(1, fact.getValue().getDraftVersion());
        assertEquals(0, fact.getValue().getSecurityVersion());
        assertEquals("1.0.0", fact.getValue().getCompilerVersion());
        assertTrue(fact.getValue().getSpecHash().matches("[0-9a-f]{64}"));
        assertTrue(fact.getValue().getSnapshotHash().matches("[0-9a-f]{64}"));
        assertTrue(fact.getValue().getCompileHash().matches("[0-9a-f]{64}"));
        assertEquals(List.of("platform-core:1.0.0", "vendor-exact:2.0.0"),
                readStrings(fact.getValue().getPluginBindings()));
        assertFalse(fact.getValue().getResultDigest().contains("payload"));
    }

    @Test
    void controlledTestPersistsValidFailureButRejectsMalformedResponseWithoutFact() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);
        VendorConnectorTestRespDTO failure = validResponse(false);
        when(runtimeClient.test(any())).thenReturn(Result.success(failure));
        when(lifecycleMapper.insertTestFact(any())).thenReturn(1);

        assertFalse(service.test(CONFIG_ID, new ConnectorSpecTestRequestDTO(Map.of()), 99L).success());
        ArgumentCaptor<ConnectorSpecLifecycleMapper.TestFactWrite> fact =
                ArgumentCaptor.forClass(ConnectorSpecLifecycleMapper.TestFactWrite.class);
        verify(lifecycleMapper).insertTestFact(fact.capture());
        assertFalse(fact.getValue().getTestSucceeded());

        org.mockito.Mockito.clearInvocations(lifecycleMapper);
        VendorConnectorTestRespDTO malformed = validResponse(true);
        malformed.setSafeMessage("bad\nheader");
        when(runtimeClient.test(any())).thenReturn(Result.success(malformed));
        assertThrows(IllegalStateException.class,
                () -> service.test(CONFIG_ID, new ConnectorSpecTestRequestDTO(Map.of()), 99L));
        verifyNoInteractions(lifecycleMapper);
    }

    @Test
    void controlledTestReturnsNullBearingNormalizedDataAsDeeplyImmutableSnapshot() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);
        LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
        ArrayList<Object> items = new ArrayList<>();
        items.add(null);
        nested.put("items", items);
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("nullable", null);
        normalized.put("nested", nested);
        VendorConnectorTestRespDTO response = validResponse(true);
        response.setNormalizedData(normalized);
        when(runtimeClient.test(any())).thenReturn(Result.success(response));
        when(lifecycleMapper.insertTestFact(any())).thenReturn(1);

        var result = service.test(CONFIG_ID, new ConnectorSpecTestRequestDTO(Map.of()), 99L);

        nested.put("later", true);
        items.add("mutation");
        assertTrue(result.normalizedData().containsKey("nullable"));
        assertNull(result.normalizedData().get("nullable"));
        @SuppressWarnings("unchecked") Map<String, Object> resultNested =
                (Map<String, Object>) result.normalizedData().get("nested");
        @SuppressWarnings("unchecked") List<Object> resultItems =
                (List<Object>) resultNested.get("items");
        assertFalse(resultNested.containsKey("later"));
        assertEquals(1, resultItems.size());
        assertNull(resultItems.getFirst());
        assertThrows(UnsupportedOperationException.class,
                () -> resultNested.put("blocked", true));
        assertThrows(UnsupportedOperationException.class, () -> resultItems.add("blocked"));
    }

    @Test
    void controlledTestRejectsInvalidInputAndDraftRaceWithoutStaleFact() {
        assertThrows(IllegalArgumentException.class,
                () -> service.test(CONFIG_ID, new ConnectorSpecTestRequestDTO(Map.of()), 0L));
        verifyNoInteractions(runtimeClient, lifecycleMapper);

        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);
        ConnectorSpecTestRequestDTO unknown = new ConnectorSpecTestRequestDTO(Map.of());
        unknown.captureUnknown("force", true);
        assertThrows(IllegalArgumentException.class, () -> service.test(CONFIG_ID, unknown, 99L));

        ConnectorSpecTestRequestDTO oversized = new ConnectorSpecTestRequestDTO(
                Map.of("payload", "x".repeat(70_000)));
        assertThrows(IllegalArgumentException.class,
                () -> service.test(CONFIG_ID, oversized, 99L));
        verifyNoInteractions(runtimeClient, lifecycleMapper);

        when(runtimeClient.test(any())).thenReturn(Result.success(validResponse(true)));
        when(lifecycleMapper.insertTestFact(any())).thenReturn(0);
        assertEquals("CONNECTOR_DRAFT_CHANGED_DURING_TEST",
                assertThrows(ConnectorConflictException.class,
                        () -> service.test(CONFIG_ID,
                                new ConnectorSpecTestRequestDTO(Map.of()), 99L)).getMessage());
    }

    @Test
    void successfulFirstPublishWritesAllFrozenFactsAndSwitchesPointer() throws Exception {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);
        stubPublishControl(3, null, stored.get(), List.of(), 0);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any())).thenReturn(true);
        when(publishMapper.insertPublished(any())).thenAnswer(invocation -> {
            ConnectorSpecPublishMapper.PublishedWrite row = invocation.getArgument(0);
            row.setId(501L);
            return 1;
        });
        when(publishMapper.casActivePointer(any(), any(), any(), any(), any())).thenReturn(1);

        var result = service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L);

        ArgumentCaptor<ConnectorSpecPublishMapper.PublishedWrite> inserted =
                ArgumentCaptor.forClass(ConnectorSpecPublishMapper.PublishedWrite.class);
        verify(publishMapper).insertPublished(inserted.capture());
        ConnectorSpecPublishMapper.PublishedWrite row = inserted.getValue();
        assertEquals(501L, result.id());
        assertEquals(CONFIG_ID, result.vendorConfigId());
        assertEquals(1, result.version());
        assertEquals("SIMPLE_CONNECTOR", result.authoringMode());
        assertEquals("ACTIVE", result.status());
        assertEquals("V2_EMBEDDED", result.hashAlgorithm());
        assertEquals(result.snapshotHash(), result.integrityHash());
        assertEquals(row.getSnapshotHash(), result.snapshotHash());
        assertEquals(row.getSpecHash(), result.specHash());
        assertEquals(row.getCompileHash(), result.compileHash());
        assertEquals(row.getCompilerVersion(), result.compilerVersion());
        assertEquals(row.getPublishedAt(), result.publishedAt());
        assertEquals(99L, result.publishedBy());
        assertEquals(stored.get().getId(), row.getDraftId());
        assertEquals(1, row.getExpectedDraftVersion());
        assertEquals(1, row.getVersionNo());
        assertNull(row.getPreviousVersionId());
        assertEquals(0, row.getSecurityVersion());
        assertTrue(mapper.readTree(row.getConnectorSpec()).isObject());
        assertTrue(mapper.readTree(row.getPipelineSnapshot()).isArray());
        verify(publishMapper).casActivePointer(CONFIG_ID, 3, null, 501L, row.getPublishedAt());
        verify(releaseCoordinator).reconcileAfterCommit();
    }

    @Test
    void publishFromLegacyActiveSupersedesAndLinksPrevious() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "next")), 99L);
        VendorConnectorVersion active = activeVersion(77L, "ADVANCED_LEGACY");
        stubPublishControl(3, 77L, stored.get(), List.of(active), 4);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any())).thenReturn(true);
        when(publishMapper.insertPublished(any())).thenAnswer(invocation -> {
            ConnectorSpecPublishMapper.PublishedWrite row = invocation.getArgument(0);
            row.setId(502L);
            return 1;
        });
        when(publishMapper.supersedeActive(any(), any(), any(), any())).thenReturn(1);
        when(publishMapper.casActivePointer(any(), any(), any(), any(), any())).thenReturn(1);

        var result = service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L);

        ArgumentCaptor<ConnectorSpecPublishMapper.PublishedWrite> inserted =
                ArgumentCaptor.forClass(ConnectorSpecPublishMapper.PublishedWrite.class);
        verify(publishMapper).insertPublished(inserted.capture());
        assertEquals(5, inserted.getValue().getVersionNo());
        assertEquals(77L, inserted.getValue().getPreviousVersionId());
        assertEquals(77L, result.previousVersionId());
        verify(publishMapper).supersedeActive(77L, CONFIG_ID, 99L, inserted.getValue().getPublishedAt());
        verify(publishMapper).casActivePointer(
                CONFIG_ID, 3, 77L, 502L, inserted.getValue().getPublishedAt());
        verify(releaseCoordinator).reconcileAfterCommit();
    }

    @Test
    void publishRequiresExactSuccessfulFiveTupleAndActivePlugin() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "first")), 99L);
        stubPublishControl(3, null, stored.get(), List.of(), 0);

        ConnectorConflictException missing = assertThrows(ConnectorConflictException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));

        assertEquals("CONNECTOR_SUCCESSFUL_TEST_REQUIRED", missing.getMessage());
        ArgumentCaptor<String> snapshotHash = ArgumentCaptor.forClass(String.class);
        verify(publishMapper).hasSuccessfulTestFact(eq(CONFIG_ID),
                eq(stored.get().getDraftVersion()), eq(stored.get().getSpecHash()),
                snapshotHash.capture(), eq(stored.get().getCompileHash()));
        assertTrue(snapshotHash.getValue().matches("[0-9a-f]{64}"));
        verify(publishMapper, times(0)).insertPublished(any());
        verifyNoInteractions(releaseCoordinator);

        org.mockito.Mockito.clearInvocations(publishMapper, releaseCoordinator);
        plugin.setStatus("STAGING");
        assertThrows(IllegalArgumentException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));
        verify(publishMapper, times(0)).hasSuccessfulTestFact(any(), any(), any(), any(), any());
        verify(publishMapper, times(0)).insertPublished(any());
        verifyNoInteractions(releaseCoordinator);
    }

    @Test
    void publishRejectsExactDuplicateSimpleActiveVersion() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "same")), 99L);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any())).thenReturn(true);
        VendorConnectorVersion duplicate = activeVersion(77L, "SIMPLE_CONNECTOR");
        duplicate.setCompileHash(stored.get().getCompileHash());
        stubPublishControl(3, 77L, stored.get(), List.of(duplicate), 4);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    duplicate.setSnapshotHash(invocation.getArgument(3));
                    return true;
                });

        ConnectorConflictException error = assertThrows(ConnectorConflictException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));

        assertEquals("CONNECTOR_VERSION_ALREADY_ACTIVE", error.getMessage());
        verify(publishMapper, times(0)).insertPublished(any());
        verifyNoInteractions(releaseCoordinator);
    }

    @Test
    void publishRejectsInvalidRequestBeforeMappersAndInvalidControlFactsFailClosed() {
        ConnectorSpecPublishRequestDTO unknown = new ConnectorSpecPublishRequestDTO(1);
        unknown.captureUnknown("force", true);
        assertThrows(IllegalArgumentException.class, () -> service.publish(CONFIG_ID, null, 99L));
        assertThrows(IllegalArgumentException.class, () -> service.publish(CONFIG_ID, unknown, 99L));
        assertThrows(IllegalArgumentException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(0), 99L));
        assertThrows(IllegalArgumentException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> service.publish(0L, new ConnectorSpecPublishRequestDTO(1), 99L));
        verifyNoInteractions(publishMapper, factsMapper, releaseCoordinator);

        ConnectorSpecPublishMapper.ControlFacts control = new ConnectorSpecPublishMapper.ControlFacts();
        when(publishMapper.lockControl(CONFIG_ID)).thenReturn(null);
        assertThrows(ConnectorSpecNotFoundException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));
        for (Object invalid : List.of("LEGACY", Integer.valueOf(-1), Integer.valueOf(Integer.MAX_VALUE))) {
            control.setId(CONFIG_ID);
            control.setRuntimeMode(invalid instanceof String text ? text : "PLUGIN");
            control.setConnectorVersion(invalid instanceof Integer number ? number : 0);
            when(publishMapper.lockControl(CONFIG_ID)).thenReturn(control);
            assertThrows(IllegalStateException.class,
                    () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));
        }
        control.setRuntimeMode("PLUGIN");
        control.setConnectorVersion(null);
        assertThrows(IllegalStateException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));
        verifyNoInteractions(releaseCoordinator);
    }

    @Test
    void normalPublishLocksControlBeforeReadingAnyOtherDatabaseFacts() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "ordered")), 99L);
        stubPublishControl(3, null, stored.get(), List.of(), 0);

        org.mockito.Mockito.clearInvocations(publishMapper, factsMapper);
        assertThrows(ConnectorConflictException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));

        var ordered = inOrder(publishMapper, factsMapper);
        ordered.verify(publishMapper).lockControl(CONFIG_ID);
        ordered.verify(publishMapper).lockDraft(CONFIG_ID);
        ordered.verify(factsMapper).findVendorFacts(CONFIG_ID);
    }

    @Test
    void publishRejectsEveryInvalidActiveBindingShapeWithoutWriting() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "binding")), 99L);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any())).thenReturn(true);
        VendorConnectorVersion valid = activeVersion(77L, "ADVANCED_LEGACY");
        VendorConnectorVersion wrongId = activeVersion(78L, "ADVANCED_LEGACY");
        VendorConnectorVersion wrongConfig = activeVersion(77L, "ADVANCED_LEGACY");
        wrongConfig.setVendorConfigId(999L);
        VendorConnectorVersion wrongStatus = activeVersion(77L, "ADVANCED_LEGACY");
        wrongStatus.setStatus("SUPERSEDED");
        List<ActiveBindingCase> invalid = List.of(
                new ActiveBindingCase(null, List.of(valid)),
                new ActiveBindingCase(77L, List.of()),
                new ActiveBindingCase(77L, List.of(valid, wrongId)),
                new ActiveBindingCase(77L, List.of(wrongId)),
                new ActiveBindingCase(77L, List.of(wrongConfig)),
                new ActiveBindingCase(77L, List.of(wrongStatus)),
                new ActiveBindingCase(77L, java.util.Arrays.asList((VendorConnectorVersion) null)));

        for (ActiveBindingCase item : invalid) {
            org.mockito.Mockito.clearInvocations(publishMapper, releaseCoordinator);
            stubPublishControl(3, item.pointer(), stored.get(), item.active(), 4);
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));
            assertEquals("ACTIVE_CONNECTOR_BINDING_INVALID", error.getMessage());
            verify(publishMapper, times(0)).insertPublished(any());
            verifyNoInteractions(releaseCoordinator);
        }
    }

    @Test
    void publishRejectsInvalidVersionSequenceAndInsertFailuresWithoutRelease() {
        PublishFixture fixture = publishFixture(null, List.of(), 0);
        for (Integer invalid : java.util.Arrays.asList(null, -1, Integer.MAX_VALUE)) {
            org.mockito.Mockito.clearInvocations(publishMapper, releaseCoordinator);
            when(publishMapper.maxVersionNo(CONFIG_ID)).thenReturn(invalid);
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));
            assertEquals("CONNECTOR_VERSION_SEQUENCE_INVALID", error.getMessage());
            verify(publishMapper, times(0)).insertPublished(any());
            verifyNoInteractions(releaseCoordinator);
        }

        when(publishMapper.maxVersionNo(CONFIG_ID)).thenReturn(0);
        when(publishMapper.insertPublished(any())).thenReturn(0);
        assertEquals("CONNECTOR_DRAFT_CHANGED_DURING_PUBLISH",
                assertThrows(ConnectorConflictException.class,
                        () -> service.publish(CONFIG_ID,
                                new ConnectorSpecPublishRequestDTO(1), 99L)).getMessage());
        verifyNoInteractions(releaseCoordinator);

        org.mockito.Mockito.clearInvocations(publishMapper, releaseCoordinator);
        stubPublishControl(3, null, fixture.draft(), List.of(), 0);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any())).thenReturn(true);
        when(publishMapper.insertPublished(any())).thenReturn(1);
        assertEquals("CONNECTOR_DRAFT_CHANGED_DURING_PUBLISH",
                assertThrows(ConnectorConflictException.class,
                        () -> service.publish(CONFIG_ID,
                                new ConnectorSpecPublishRequestDTO(1), 99L)).getMessage());
        verifyNoInteractions(releaseCoordinator);
    }

    @Test
    void publishRollsBackOnSupersedeOrPointerCasFailureAndNeverReleases() {
        VendorConnectorVersion current = activeVersion(77L, "SIMPLE_CONNECTOR");
        current.setCompileHash("0".repeat(64));
        current.setSnapshotHash("1".repeat(64));
        PublishFixture fixture = publishFixture(77L, List.of(current), 4);
        when(publishMapper.insertPublished(any())).thenAnswer(invocation -> {
            ConnectorSpecPublishMapper.PublishedWrite row = invocation.getArgument(0);
            row.setId(503L);
            return 1;
        });
        when(publishMapper.supersedeActive(any(), any(), any(), any())).thenReturn(0);
        assertEquals("ACTIVE_CONNECTOR_CHANGED_DURING_PUBLISH",
                assertThrows(ConnectorConflictException.class,
                        () -> service.publish(CONFIG_ID,
                                new ConnectorSpecPublishRequestDTO(1), 99L)).getMessage());
        verifyNoInteractions(releaseCoordinator);

        org.mockito.Mockito.clearInvocations(publishMapper, releaseCoordinator);
        stubPublishControl(3, 77L, fixture.draft(), List.of(current), 4);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any())).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            ConnectorSpecPublishMapper.PublishedWrite row = invocation.getArgument(0);
            row.setId(504L);
            return 1;
        }).when(publishMapper).insertPublished(any());
        when(publishMapper.supersedeActive(any(), any(), any(), any())).thenReturn(1);
        when(publishMapper.casActivePointer(any(), any(), any(), any(), any())).thenReturn(0);
        assertEquals("ACTIVE_CONNECTOR_POINTER_CHANGED_DURING_PUBLISH",
                assertThrows(ConnectorConflictException.class,
                        () -> service.publish(CONFIG_ID,
                                new ConnectorSpecPublishRequestDTO(1), 99L)).getMessage());
        verifyNoInteractions(releaseCoordinator);
    }

    @Test
    void publishRejectsMissingLegacyStaleAndDriftedDraftsBeforeInsert() {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "draft-gates")), 99L);
        stubPublishControl(3, null, stored.get(), List.of(), 0);

        when(publishMapper.lockDraft(CONFIG_ID)).thenReturn(null);
        assertThrows(ConnectorSpecNotFoundException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));

        VendorConnectorVersion legacy = stored.get();
        legacy.setAuthoringMode("ADVANCED_LEGACY");
        when(publishMapper.lockDraft(CONFIG_ID)).thenReturn(legacy);
        assertThrows(ConnectorConflictException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));
        legacy.setAuthoringMode("SIMPLE_CONNECTOR");
        assertThrows(ConnectorConflictException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(2), 99L));

        for (int index = 0; index < 3; index++) {
            legacy.setSnapshotHash(index == 0 ? "a".repeat(64) : null);
            legacy.setHashAlgorithm(index == 1 ? "V2_EMBEDDED" : null);
            legacy.setIntegrityHash(index == 2 ? "a".repeat(64) : null);
            assertThrows(IllegalStateException.class,
                    () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));
        }
        legacy.setSnapshotHash(null);
        legacy.setHashAlgorithm(null);
        legacy.setIntegrityHash(null);
        legacy.setCompileHash("0".repeat(64));
        assertThrows(ConnectorConflictException.class,
                () -> service.publish(CONFIG_ID, new ConnectorSpecPublishRequestDTO(1), 99L));

        verify(publishMapper, times(0)).insertPublished(any());
        verifyNoInteractions(releaseCoordinator);
    }

    @Test
    void historyExplainsDisabledSimpleAndLegacyVersionsWithoutExposingPipeline() throws Exception {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        var saved = service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "history")), 99L);
        VendorConnectorVersion simple = stored.get();
        simple.setId(202L);
        simple.setVersionNo(2);
        simple.setDraftVersion(0);
        simple.setStatus("SUPERSEDED");
        simple.setSnapshotHash(saved.compiledSnapshotHash());
        simple.setHashAlgorithm("V2_EMBEDDED");
        simple.setIntegrityHash(saved.compiledSnapshotHash());
        simple.setPreviousVersionId(101L);
        simple.setPublishedBy(8L);
        simple.setPublishedAt(java.time.LocalDateTime.now());

        VendorConnectorVersion legacy = activeVersion(101L, "ADVANCED_LEGACY");
        legacy.setVersionNo(1);
        legacy.setDraftVersion(0);
        legacy.setStatus("SUPERSEDED");
        legacy.setSecurityVersion(0);
        legacy.setPublishedBy(null);
        legacy.setPublishedAt(java.time.LocalDateTime.now());
        legacy.setPipelineSnapshot(mapper.writeValueAsString(List.of(
                step("legacy.builder", "REQUEST_BUILDER", "legacy-vendor", 100),
                step("legacy.transport", "TRANSPORT", "legacy-vendor", 200))));
        plugin.setStatus("DISABLED");
        ConnectorSpecFactsMapper.CatalogPluginFacts inactive =
                new ConnectorSpecFactsMapper.CatalogPluginFacts();
        inactive.setPluginId(plugin.getPluginId());
        inactive.setStatus("DISABLED");
        when(factsMapper.findCatalogPlugin(plugin.getPluginId())).thenReturn(inactive);
        when(publishMapper.findHistoryVersions(CONFIG_ID)).thenReturn(List.of(simple, legacy));

        var history = service.history(CONFIG_ID);

        assertEquals(List.of(2, 1), history.versions().stream().map(item -> item.version()).toList());
        assertEquals("history", history.versions().getFirst().connectorSpec().getConfig().get("endpoint"));
        assertNull(history.versions().get(1).connectorSpec());
        assertNull(history.versions().get(1).publishedBy());
        @SuppressWarnings("unchecked") Map<String, Object> returnedConfig =
                history.versions().getFirst().connectorSpec().getConfig();
        assertThrows(UnsupportedOperationException.class,
                () -> returnedConfig.put("mutated", true));
        assertTrue(java.util.Arrays.stream(history.getClass().getRecordComponents())
                .noneMatch(component -> "pipelineSnapshot".equals(component.getName())));
        assertTrue(java.util.Arrays.stream(history.versions().getFirst().getClass().getRecordComponents())
                .noneMatch(component -> "pipelineSnapshot".equals(component.getName())
                        || "stageConfig".equals(component.getName())));

        List<ConnectorPipelineStepDTO> steps = mapper.readValue(simple.getPipelineSnapshot(),
                mapper.getTypeFactory().constructCollectionType(List.class, ConnectorPipelineStepDTO.class));
        ConnectorPipelineStepDTO first = steps.getFirst();
        List<ConnectorPipelineStepDTO> tampered = new ArrayList<>(steps);
        tampered.set(0, new ConnectorPipelineStepDTO(first.stageKey(), first.capability(),
                first.pluginId(), first.pluginVersion(), first.order(), first.enabled(), first.config(),
                first.configHash(), "f".repeat(64), first.manifestHash(), first.schemaHash()));
        simple.setPipelineSnapshot(mapper.writeValueAsString(tampered));
        assertEquals("CONNECTOR_PLAN_PLUGIN_DIGEST_DRIFT",
                assertThrows(IllegalStateException.class, () -> service.history(CONFIG_ID)).getMessage());
    }

    @Test
    void rollbackCopiesLegacyHistoryAndStagesEveryExternalPluginBeforeWriting() throws Exception {
        VendorConnectorVersion target = activeVersion(55L, "ADVANCED_LEGACY");
        target.setVersionNo(2);
        target.setDraftVersion(0);
        target.setStatus("SUPERSEDED");
        target.setSecurityVersion(0);
        target.setPublishedBy(8L);
        target.setPublishedAt(java.time.LocalDateTime.now());
        target.setPipelineSnapshot(mapper.writeValueAsString(List.of(
                step("legacy.builder", "REQUEST_BUILDER", "legacy-vendor", 100),
                step("legacy.transport", "TRANSPORT", "legacy-http", 200),
                step("legacy.parser", "RESPONSE_PARSER", "legacy-vendor", 300))));
        VendorConnectorVersion current = activeVersion(77L, "ADVANCED_LEGACY");
        ConnectorSpecPublishMapper.ControlFacts control = control(3, 77L);
        when(publishMapper.lockControl(CONFIG_ID)).thenReturn(control);
        when(publishMapper.lockTarget(CONFIG_ID, 2)).thenReturn(target);
        when(publishMapper.lockActive(CONFIG_ID)).thenReturn(List.of(current));
        when(publishMapper.maxVersionNo(CONFIG_ID)).thenReturn(4);
        when(publishMapper.insertRollback(any())).thenAnswer(invocation -> {
            ConnectorSpecPublishMapper.RollbackWrite row = invocation.getArgument(0);
            row.setId(601L);
            return 1;
        });
        when(publishMapper.supersedeActive(any(), any(), any(), any())).thenReturn(1);
        when(publishMapper.casActivePointer(any(), any(), any(), any(), any())).thenReturn(1);

        var result = service.rollback(
                CONFIG_ID, 2, new ConnectorSpecRollbackRequestDTO(3), 99L);

        ArgumentCaptor<ConnectorSpecPublishMapper.RollbackWrite> inserted =
                ArgumentCaptor.forClass(ConnectorSpecPublishMapper.RollbackWrite.class);
        verify(publishMapper).insertRollback(inserted.capture());
        ConnectorSpecPublishMapper.RollbackWrite row = inserted.getValue();
        assertEquals(601L, result.id());
        assertEquals(5, result.version());
        assertEquals(77L, result.previousVersionId());
        assertEquals("ADVANCED_LEGACY", result.authoringMode());
        assertEquals(target.getPipelineSnapshot(), row.getPipelineSnapshot());
        assertEquals(target.getSnapshotHash(), row.getSnapshotHash());
        assertEquals(target.getHashAlgorithm(), row.getHashAlgorithm());
        assertEquals(target.getIntegrityHash(), row.getIntegrityHash());
        assertNull(row.getConnectorSpec());
        ArgumentCaptor<ConnectorPluginStageReqDTO> stage =
                ArgumentCaptor.forClass(ConnectorPluginStageReqDTO.class);
        verify(activationClient).stage(stage.capture());
        assertEquals("legacy-vendor", stage.getValue().getPluginId());
        assertEquals("2.0.0", stage.getValue().getPluginVersion());
        verify(activationClient, times(1)).stage(any());
        verify(publishMapper).supersedeActive(eq(77L), eq(CONFIG_ID), eq(99L), any());
        verify(publishMapper).casActivePointer(eq(CONFIG_ID), eq(3), eq(77L), eq(601L), any());
        verify(releaseCoordinator).reconcileAfterCommit();
    }

    @Test
    void publishFailsClosedWhenAnyAccessInstanceIsMissingOrResponseCoordinatesAreForged() {
        PublishFixture fixture = publishFixture(null, List.of(), 0);
        org.mockito.Mockito.doAnswer(invocation -> {
            ConnectorPluginStageReqDTO request = invocation.getArgument(0);
            Result<ConnectorPluginActivationSummaryDTO> ready = readyActivation(request);
            ready.getData().getInstances().get(1).setState("MISSING");
            return ready;
        }).when(activationClient).stage(any());

        assertEquals("CONNECTOR_PLUGIN_NOT_READY",
                assertThrows(ConnectorConflictException.class,
                        () -> service.publish(CONFIG_ID,
                                new ConnectorSpecPublishRequestDTO(1), 99L)).getMessage());
        verify(publishMapper, times(0)).insertPublished(any());
        verifyNoInteractions(releaseCoordinator);

        org.mockito.Mockito.clearInvocations(publishMapper, activationClient, releaseCoordinator);
        stubPublishControl(3, null, fixture.draft(), List.of(), 0);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any())).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            ConnectorPluginStageReqDTO request = invocation.getArgument(0);
            Result<ConnectorPluginActivationSummaryDTO> forged = readyActivation(request);
            forged.getData().setPluginVersion("9.9.9");
            return forged;
        }).when(activationClient).stage(any());

        assertEquals("CONNECTOR_PLUGIN_NOT_READY",
                assertThrows(ConnectorConflictException.class,
                        () -> service.publish(CONFIG_ID,
                                new ConnectorSpecPublishRequestDTO(1), 99L)).getMessage());
        verify(publishMapper, times(0)).insertPublished(any());
        verifyNoInteractions(releaseCoordinator);
    }

    @Test
    void rollbackRejectsInvalidRequestTargetAndUnreadyPluginWithoutWrites() throws Exception {
        ConnectorSpecRollbackRequestDTO unknown = new ConnectorSpecRollbackRequestDTO(3);
        unknown.captureUnknown("force", true);
        assertThrows(IllegalArgumentException.class,
                () -> service.rollback(CONFIG_ID, 2, unknown, 99L));
        assertThrows(IllegalArgumentException.class,
                () -> service.rollback(CONFIG_ID, 0, new ConnectorSpecRollbackRequestDTO(3), 99L));
        assertThrows(IllegalArgumentException.class,
                () -> service.rollback(CONFIG_ID, 2, new ConnectorSpecRollbackRequestDTO(-1), 99L));
        verifyNoInteractions(publishMapper, activationClient, releaseCoordinator);

        when(publishMapper.lockControl(CONFIG_ID)).thenReturn(control(3, 77L));
        when(publishMapper.lockTarget(CONFIG_ID, 2)).thenReturn(null);
        assertThrows(ConnectorSpecNotFoundException.class,
                () -> service.rollback(CONFIG_ID, 2, new ConnectorSpecRollbackRequestDTO(3), 99L));

        VendorConnectorVersion target = activeVersion(55L, "ADVANCED_LEGACY");
        target.setVersionNo(2);
        target.setDraftVersion(0);
        target.setStatus("SUPERSEDED");
        target.setSecurityVersion(0);
        target.setPublishedBy(8L);
        target.setPublishedAt(java.time.LocalDateTime.now());
        target.setPipelineSnapshot(mapper.writeValueAsString(List.of(
                step("legacy.transport", "TRANSPORT", "legacy-vendor", 100))));
        when(publishMapper.lockTarget(CONFIG_ID, 2)).thenReturn(target);
        when(publishMapper.lockActive(CONFIG_ID)).thenReturn(List.of(activeVersion(77L, "ADVANCED_LEGACY")));
        org.mockito.Mockito.doReturn(Result.error(503, "unsafe downstream detail"))
                .when(activationClient).stage(any());

        assertEquals("CONNECTOR_PLUGIN_NOT_READY",
                assertThrows(ConnectorConflictException.class,
                        () -> service.rollback(CONFIG_ID, 2,
                                new ConnectorSpecRollbackRequestDTO(3), 99L)).getMessage());
        verify(publishMapper, times(0)).insertRollback(any());
        verifyNoInteractions(releaseCoordinator);
    }

    @Test
    void upgradePreviewIsDeterministicRedactedAndPerformsNoWritesOrRuntimeCalls() throws Exception {
        ConnectorPluginVersion current = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        ConnectorPluginVersion target = plugin("vendor-exact", "3.0.0", "STAGING",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        addSchemaProperty(target, "region", "string", true, List.of("cn", "us"), false);
        stubPlugin(current);
        stubPlugin(target);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(current, "https://vendor.example")), 99L);
        org.mockito.Mockito.clearInvocations(draftMapper, lifecycleMapper, publishMapper,
                runtimeClient, activationClient, releaseCoordinator);

        var first = service.upgradePreview(CONFIG_ID,
                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"));
        var second = service.upgradePreview(CONFIG_ID,
                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"));

        assertFalse(first.valid());
        assertEquals("CONNECTOR_CONFIG_INVALID", first.errorCode());
        assertEquals(first, second);
        assertEquals("vendor-exact", first.currentPlugin().pluginId());
        assertEquals("2.0.0", first.currentPlugin().pluginVersion());
        assertEquals("3.0.0", first.targetPlugin().pluginVersion());
        assertEquals(List.of("/region"), first.schemaChanges().stream()
                .map(change -> change.path()).toList());
        assertEquals("ADDED", first.schemaChanges().getFirst().changeKind());
        assertEquals("REQUIRED_VALUE_MISSING", first.configChanges().stream()
                .filter(change -> "/region".equals(change.path())).findFirst().orElseThrow().changeKind());
        assertNull(first.previewSpecHash());
        assertNull(first.compiledSnapshotHash());
        assertNull(first.compileHash());
        String json = mapper.writeValueAsString(first);
        assertFalse(json.contains("pipelineSnapshot"));
        assertFalse(json.contains("https://vendor.example"));
        assertFalse(json.contains("vendor.secretKey"));
        verifyNoInteractions(draftMapper, lifecycleMapper, publishMapper,
                runtimeClient, activationClient, releaseCoordinator);
        assertNotNull(stored.get());
    }

    @Test
    void successfulUpgradePreviewReportsStableHashesAndSafePlanSummary() throws Exception {
        ConnectorPluginVersion current = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        ConnectorPluginVersion target = plugin("vendor-exact", "3.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(current);
        stubPlugin(target);
        installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(current, "endpoint")), 99L);

        var preview = service.upgradePreview(CONFIG_ID,
                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"));

        assertTrue(preview.valid());
        assertNull(preview.errorCode());
        assertTrue(preview.previewSpecHash().matches("[0-9a-f]{64}"));
        assertTrue(preview.compiledSnapshotHash().matches("[0-9a-f]{64}"));
        assertTrue(preview.compileHash().matches("[0-9a-f]{64}"));
        assertEquals(0, preview.planDiff().addedStageCount());
        assertEquals(0, preview.planDiff().removedStageCount());
        assertEquals(2, preview.planDiff().coordinateChangeCount());
        assertEquals(List.of("connector.request-builder", "connector.response-parser"),
                preview.planDiff().changedStageKeys());
    }

    @Test
    void upgradePreviewRejectsStrictCasLegacyNoopAndUnbindableTargetFacts() {
        ConnectorSpecUpgradePreviewRequestDTO unknown =
                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0");
        unknown.captureUnknown("pluginId", "forged");
        assertThrows(IllegalArgumentException.class,
                () -> service.upgradePreview(CONFIG_ID, unknown));
        verifyNoInteractions(draftMapper, lifecycleMapper, publishMapper,
                runtimeClient, activationClient, releaseCoordinator);

        VendorConnectorVersion legacy = new VendorConnectorVersion();
        legacy.setId(5L);
        legacy.setVendorConfigId(CONFIG_ID);
        legacy.setStatus("DRAFT");
        legacy.setAuthoringMode("ADVANCED_LEGACY");
        when(factsMapper.findDraft(CONFIG_ID)).thenReturn(legacy);
        assertEquals("LEGACY_PIPELINE_REQUIRES_CONVERSION",
                assertThrows(ConnectorConflictException.class,
                        () -> service.upgradePreview(CONFIG_ID,
                                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"))).getMessage());

        ConnectorPluginVersion current = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(current);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(current, "endpoint")), 99L);
        assertThrows(ConnectorConflictException.class,
                () -> service.upgradePreview(CONFIG_ID,
                        new ConnectorSpecUpgradePreviewRequestDTO(2, "3.0.0")));
        assertEquals("CONNECTOR_PLUGIN_VERSION_UNCHANGED",
                assertThrows(ConnectorConflictException.class,
                        () -> service.upgradePreview(CONFIG_ID,
                                new ConnectorSpecUpgradePreviewRequestDTO(1, "2.0.0"))).getMessage());
        ConnectorPluginVersion older = plugin("vendor-exact", "1.9.9", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(older);
        assertEquals("CONNECTOR_PLUGIN_VERSION_DOWNGRADE_FORBIDDEN",
                assertThrows(ConnectorConflictException.class,
                        () -> service.upgradePreview(CONFIG_ID,
                                new ConnectorSpecUpgradePreviewRequestDTO(1, "1.9.9"))).getMessage());

        ConnectorPluginVersion disabled = plugin("vendor-exact", "3.0.0", "DISABLED",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(disabled);
        assertEquals("TARGET_PLUGIN_STATUS_INVALID",
                assertThrows(ConnectorConflictException.class,
                        () -> service.upgradePreview(CONFIG_ID,
                                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"))).getMessage());
        disabled.setStatus("STAGING");
        ConnectorSpecFactsMapper.CatalogPluginFacts parent =
                new ConnectorSpecFactsMapper.CatalogPluginFacts();
        parent.setPluginId("vendor-exact");
        parent.setDisplayName("vendor-exact connector");
        parent.setProvider("test");
        parent.setStatus("DISABLED");
        when(factsMapper.findCatalogPlugin("vendor-exact")).thenReturn(parent);
        assertEquals("PLUGIN_CATALOG_FACTS_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> service.upgradePreview(CONFIG_ID,
                                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"))).getMessage());
        assertNotNull(stored.get());
    }

    @Test
    void upgradePreviewRejectsIncompatibleAndSignedProjectionDriftButRedactsSecretPaths() {
        ConnectorPluginVersion current = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        ConnectorPluginVersion incompatible = plugin("vendor-exact", "3.0.0", "STAGING",
                ConnectorKind.DEDICATED_VENDOR, List.of("OTHER"), List.of());
        stubPlugin(current);
        stubPlugin(incompatible);
        installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(current, "endpoint")), 99L);

        assertEquals("TARGET_PLUGIN_COMPATIBILITY_MISMATCH",
                assertThrows(ConnectorConflictException.class,
                        () -> service.upgradePreview(CONFIG_ID,
                                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"))).getMessage());

        ConnectorPluginVersion secretTarget = plugin("vendor-exact", "3.0.0", "STAGING",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        addSchemaProperty(secretTarget, "credential", "string", true, List.of(), true);
        stubPlugin(secretTarget);
        var redacted = service.upgradePreview(CONFIG_ID,
                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"));
        assertFalse(redacted.valid());
        assertTrue(redacted.schemaChanges().stream()
                .anyMatch(change -> "/credential".equals(change.path()) && change.secretRef()));
        assertFalse(mapper.valueToTree(redacted).toString().contains("vendor.secretKey"));

        secretTarget.setOutputMode("PLUGIN_NORMALIZED");
        assertEquals("PLUGIN_SIGNED_PROJECTION_DRIFT",
                assertThrows(IllegalStateException.class,
                        () -> service.upgradePreview(CONFIG_ID,
                                new ConnectorSpecUpgradePreviewRequestDTO(1, "3.0.0"))).getMessage());
    }

    private VendorConnectorTestRespDTO validResponse(boolean success) {
        VendorConnectorTestRespDTO response = new VendorConnectorTestRespDTO();
        response.setSuccess(success);
        response.setErrorCategory(success ? null : "HTTP_ERROR");
        response.setErrorCode(success ? null : "UPSTREAM_REJECTED");
        response.setSafeMessage(success ? "ok" : "upstream rejected");
        response.setNormalizedData(Map.of("company", "payload"));
        response.setStageTimings(List.of());
        return response;
    }

    private void stubPublishControl(
            int connectorVersion, Long activeId, VendorConnectorVersion draft,
            List<VendorConnectorVersion> active, int maxVersion) {
        ConnectorSpecPublishMapper.ControlFacts control = control(connectorVersion, activeId);
        when(publishMapper.lockControl(CONFIG_ID)).thenReturn(control);
        when(publishMapper.lockDraft(CONFIG_ID)).thenReturn(draft);
        when(publishMapper.lockActive(CONFIG_ID)).thenReturn(active);
        when(publishMapper.maxVersionNo(CONFIG_ID)).thenReturn(maxVersion);
    }

    private ConnectorSpecPublishMapper.ControlFacts control(int connectorVersion, Long activeId) {
        ConnectorSpecPublishMapper.ControlFacts control = new ConnectorSpecPublishMapper.ControlFacts();
        control.setId(CONFIG_ID);
        control.setConnectorVersion(connectorVersion);
        control.setActiveConnectorVersionId(activeId);
        control.setRuntimeMode("PLUGIN");
        return control;
    }

    private PublishFixture publishFixture(
            Long activeId, List<VendorConnectorVersion> active, int maxVersion) {
        ConnectorPluginVersion plugin = plugin("vendor-exact", "2.0.0", "ACTIVE",
                ConnectorKind.DEDICATED_VENDOR, List.of("ACME"), List.of());
        stubPlugin(plugin);
        AtomicReference<VendorConnectorVersion> stored = installDraftPersistence();
        service.saveDraft(CONFIG_ID,
                new ConnectorSpecSaveRequestDTO(0, spec(plugin, "failure")), 99L);
        stubPublishControl(3, activeId, stored.get(), active, maxVersion);
        when(publishMapper.hasSuccessfulTestFact(any(), any(), any(), any(), any())).thenReturn(true);
        return new PublishFixture(stored.get());
    }

    private VendorConnectorVersion activeVersion(Long id, String authoringMode) {
        VendorConnectorVersion active = new VendorConnectorVersion();
        active.setId(id);
        active.setVendorConfigId(CONFIG_ID);
        active.setVersionNo(1);
        active.setDraftVersion(0);
        active.setStatus("ACTIVE");
        active.setAuthoringMode(authoringMode);
        active.setSnapshotHash("f".repeat(64));
        active.setHashAlgorithm("V1_DERIVED");
        active.setIntegrityHash("e".repeat(64));
        return active;
    }

    private List<String> readStrings(String json) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private AtomicReference<VendorConnectorVersion> installDraftPersistence() {
        AtomicReference<VendorConnectorVersion> stored = new AtomicReference<>();
        when(factsMapper.findDraft(CONFIG_ID)).thenAnswer(ignored -> stored.get());
        when(draftMapper.insertDraft(any())).thenAnswer(invocation -> {
            ConnectorSpecDraftMapper.DraftWrite row = invocation.getArgument(0);
            row.setId(100L);
            stored.set(fromWrite(row));
            return 1;
        });
        when(draftMapper.updateDraft(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any())).thenAnswer(invocation -> {
            VendorConnectorVersion value = stored.get();
            value.setDraftVersion(invocation.getArgument(3));
            value.setPipelineSnapshot(invocation.getArgument(4));
            value.setConnectorSpec(invocation.getArgument(5));
            value.setSpecHash(invocation.getArgument(6));
            value.setCompilerVersion(invocation.getArgument(7));
            value.setCompileHash(invocation.getArgument(8));
            value.setSecurityVersion(invocation.getArgument(9));
            return 1;
        });
        return stored;
    }

    private VendorConnectorVersion fromWrite(ConnectorSpecDraftMapper.DraftWrite row) {
        VendorConnectorVersion value = new VendorConnectorVersion();
        value.setId(row.getId());
        value.setVendorConfigId(row.getVendorConfigId());
        value.setDraftVersion(1);
        value.setPipelineSnapshot(row.getPipelineSnapshot());
        value.setAuthoringMode("SIMPLE_CONNECTOR");
        value.setConnectorSpec(row.getConnectorSpec());
        value.setSpecHash(row.getSpecHash());
        value.setCompilerVersion(row.getCompilerVersion());
        value.setCompileHash(row.getCompileHash());
        value.setSecurityVersion(row.getSecurityVersion());
        value.setStatus("DRAFT");
        return value;
    }

    private void stubPlugin(ConnectorPluginVersion plugin) {
        when(factsMapper.findPluginVersion(plugin.getPluginId(), plugin.getVersion()))
                .thenReturn(plugin);
        stubCatalogFacts(plugin);
    }

    private ConnectorSpecDTO spec(ConnectorPluginVersion plugin, String endpoint) {
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("endpoint", endpoint);
        return new ConnectorSpecDTO("1",
                new ConnectorSpecDTO.PluginRef(plugin.getPluginId(), plugin.getVersion()),
                config, null);
    }

    private ConnectorPipelineStepDTO step(String key, String capability, String pluginId, int order) {
        return new ConnectorPipelineStepDTO(key, capability, pluginId,
                "platform-core".equals(pluginId) || "legacy-http".equals(pluginId)
                        ? "1.0.0" : "2.0.0", order, true,
                Map.of("secretRef", "top-secret"), "c".repeat(64), "a".repeat(64),
                "b".repeat(64), "d".repeat(64));
    }

    private VendorConnectorVersion legacyDraft(List<ConnectorPipelineStepDTO> pipeline) throws Exception {
        VendorConnectorVersion legacy = new VendorConnectorVersion();
        legacy.setId(99L);
        legacy.setVendorConfigId(CONFIG_ID);
        legacy.setDraftVersion(4);
        legacy.setStatus("DRAFT");
        legacy.setAuthoringMode("ADVANCED_LEGACY");
        legacy.setSecurityVersion(0);
        legacy.setPipelineSnapshot(mapper.writeValueAsString(pipeline));
        return legacy;
    }

    private List<ConnectorPipelineStepDTO> replaceLegacyConfig(
            List<ConnectorPipelineStepDTO> pipeline, int index, Map<String, Object> overrides) {
        List<ConnectorPipelineStepDTO> result = new ArrayList<>(pipeline);
        ConnectorPipelineStepDTO source = result.get(index);
        Map<String, Object> config = new LinkedHashMap<>(source.config());
        config.putAll(overrides);
        result.set(index, new ConnectorPipelineStepDTO(source.stageKey(), source.capability(),
                source.pluginId(), source.pluginVersion(), source.order(), source.enabled(), config,
                source.configHash(), source.artifactSha256(), source.manifestHash(), source.schemaHash()));
        return result;
    }

    private ConnectorPluginVersion genericPlugin() {
        ConnectorPluginVersion entity = new ConnectorPluginVersion();
        entity.setPluginId(GenericHttpConnectorMetadata.PLUGIN_ID);
        entity.setVersion(GenericHttpConnectorMetadata.VERSION);
        entity.setSpiVersion(GenericHttpConnectorMetadata.SPI_VERSION);
        entity.setEntryClass(GenericHttpConnectorMetadata.ENTRY_CLASS);
        entity.setArtifactUri(GenericHttpConnectorMetadata.ARTIFACT_URI);
        entity.setArtifactSha256(GenericHttpConnectorMetadata.artifactSha256());
        entity.setDetachedSignature(GenericHttpConnectorMetadata.BUILTIN_SIGNATURE);
        entity.setSigningKeyId(GenericHttpConnectorMetadata.BUILTIN_SIGNING_KEY);
        entity.setManifestJson(GenericHttpConnectorMetadata.canonicalManifestJson());
        entity.setConfigSchemaJson(GenericHttpConnectorMetadata.canonicalSchemaJson());
        entity.setCapabilities(write(GenericHttpConnectorMetadata.CAPABILITY_NAMES));
        entity.setPermissionManifest(GenericHttpConnectorMetadata.canonicalPermissionsJson());
        entity.setMinHostVersion(GenericHttpConnectorMetadata.MIN_HOST_VERSION);
        entity.setManifestVersion("2");
        entity.setAuthoringModel("SIMPLE_CONNECTOR");
        entity.setConnectorKind("GENERIC_HTTP");
        entity.setTransportMode("HOST_SINGLE_HTTP");
        entity.setOutputMode("HOST_MAPPING");
        entity.setCompatibilityManifest(GenericHttpConnectorMetadata.canonicalCompatibilityJson());
        entity.setStatus("ACTIVE");
        return entity;
    }

    private void stubGenericPlugin(ConnectorPluginVersion entity) {
        when(factsMapper.findPluginVersion(GenericHttpConnectorMetadata.PLUGIN_ID,
                GenericHttpConnectorMetadata.VERSION)).thenReturn(entity);
        ConnectorSpecFactsMapper.CatalogPluginFacts parent = new ConnectorSpecFactsMapper.CatalogPluginFacts();
        parent.setPluginId(GenericHttpConnectorMetadata.PLUGIN_ID);
        parent.setDisplayName(GenericHttpConnectorMetadata.DISPLAY_NAME);
        parent.setProvider(GenericHttpConnectorMetadata.PROVIDER);
        parent.setDescription(GenericHttpConnectorMetadata.DESCRIPTION);
        parent.setStatus("ACTIVE");
        when(factsMapper.findCatalogPlugin(GenericHttpConnectorMetadata.PLUGIN_ID)).thenReturn(parent);
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private ConnectorSpecFactsMapper.VendorFacts vendorFacts() {
        ConnectorSpecFactsMapper.VendorFacts facts = new ConnectorSpecFactsMapper.VendorFacts();
        facts.setVendorConfigId(CONFIG_ID);
        facts.setVendorId(VENDOR_ID);
        facts.setVendorCode("ACME");
        facts.setDataTypeCode("COMPANY");
        facts.setSecurityVersion(0);
        facts.setTimeout(10_000);
        return facts;
    }

    private void stubCatalogFacts(ConnectorPluginVersion plugin) {
        ConnectorSpecFactsMapper.CatalogPluginFacts facts = new ConnectorSpecFactsMapper.CatalogPluginFacts();
        facts.setPluginId(plugin.getPluginId());
        facts.setDisplayName(plugin.getPluginId() + " connector");
        facts.setProvider("test");
        facts.setStatus("ACTIVE");
        when(factsMapper.findCatalogPlugin(plugin.getPluginId())).thenReturn(facts);
    }

    private ConnectorPluginVersion plugin(String pluginId, String version, String status,
                                          ConnectorKind kind, List<String> vendorCodes,
                                          List<String> dataTypeCodes) {
        try {
            ObjectNode schema = mapper.createObjectNode();
            schema.put("type", "object");
            schema.putObject("properties").putObject("endpoint").put("type", "string");
            schema.put("additionalProperties", false);
            ObjectNode compatibility = mapper.createObjectNode();
            if (!vendorCodes.isEmpty()) compatibility.set("vendorCodes", mapper.valueToTree(vendorCodes));
            if (!dataTypeCodes.isEmpty()) compatibility.set("dataTypeCodes", mapper.valueToTree(dataTypeCodes));
            ArrayNode capabilities = mapper.createArrayNode()
                    .add("REQUEST_BUILDER").add("RESPONSE_PARSER");
            ObjectNode permissions = mapper.createObjectNode();
            permissions.putArray("networkProtocols").add("https");
            permissions.putArray("networkHosts").add("vendor.example");
            ObjectNode manifest = mapper.createObjectNode();
            manifest.put("manifestVersion", "2");
            manifest.put("pluginId", pluginId);
            manifest.put("version", version);
            manifest.put("spiVersion", "1.1");
            manifest.put("displayName", pluginId + " connector");
            manifest.put("provider", "test");
            manifest.put("entryClass", "example.FixtureVendorPlugin");
            manifest.put("authoringModel", "SIMPLE_CONNECTOR");
            manifest.put("connectorKind", kind.name());
            manifest.put("transportMode", "HOST_SINGLE_HTTP");
            manifest.put("outputMode", "HOST_MAPPING");
            manifest.set("capabilities", capabilities);
            manifest.set("compatibility", compatibility);
            manifest.put("minHostVersion", "2.1.0");
            manifest.set("configSchema", schema);
            manifest.set("permissions", permissions);

            ConnectorPluginVersion entity = new ConnectorPluginVersion();
            entity.setPluginId(pluginId);
            entity.setVersion(version);
            entity.setSpiVersion("1.1");
            entity.setEntryClass("example.FixtureVendorPlugin");
            entity.setArtifactUri("https://repo.example/" + pluginId + ".jar");
            entity.setArtifactSha256("a".repeat(64));
            entity.setDetachedSignature("signature");
            entity.setSigningKeyId("key-1");
            entity.setManifestJson(mapper.writeValueAsString(manifest));
            entity.setConfigSchemaJson(mapper.writeValueAsString(schema));
            entity.setCapabilities(mapper.writeValueAsString(capabilities));
            entity.setPermissionManifest(mapper.writeValueAsString(permissions));
            entity.setMinHostVersion("2.1.0");
            entity.setManifestVersion("2");
            entity.setAuthoringModel("SIMPLE_CONNECTOR");
            entity.setConnectorKind(kind.name());
            entity.setTransportMode("HOST_SINGLE_HTTP");
            entity.setOutputMode("HOST_MAPPING");
            entity.setCompatibilityManifest(mapper.writeValueAsString(compatibility));
            entity.setStatus(status);
            return entity;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void addSchemaProperty(ConnectorPluginVersion plugin, String name, String type,
                                   boolean required, List<String> enumValues, boolean secretRef) {
        try {
            ObjectNode manifest = (ObjectNode) mapper.readTree(plugin.getManifestJson());
            ObjectNode schema = (ObjectNode) manifest.path("configSchema");
            ObjectNode property = schema.withObject("properties").putObject(name).put("type", type);
            if (!enumValues.isEmpty()) property.set("enum", mapper.valueToTree(enumValues));
            if (secretRef) {
                property.put("x-secret-ref", true);
                property.putArray("x-stage-scope").add("REQUEST_BUILDER");
            }
            if (required) schema.withArray("required").add(name);
            plugin.setManifestJson(mapper.writeValueAsString(manifest));
            plugin.setConfigSchemaJson(mapper.writeValueAsString(schema));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private record ActiveBindingCase(Long pointer, List<VendorConnectorVersion> active) { }
    private record PublishFixture(VendorConnectorVersion draft) { }
}
