package com.dataplatform.masterdata.connector.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.feign.ConnectorMigrationObservationInternalFeignClient;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.ConnectorBillingObservationDTO;
import com.dataplatform.billing.api.feign.ConnectorBillingObservationInternalFeignClient;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationObserveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationStartRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDraftViewDTO;
import com.dataplatform.masterdata.connector.entity.VendorConnectorMigration;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorMigrationMapper;
import com.dataplatform.masterdata.connector.spec.ConnectorSpecService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VendorConnectorMigrationServiceImplTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                VendorConnectorMigration.class);
    }

    private final VendorConnectorMigrationMapper mapper = mock(VendorConnectorMigrationMapper.class);
    private final ConnectorMigrationObservationInternalFeignClient access =
            mock(ConnectorMigrationObservationInternalFeignClient.class);
    private final ConnectorBillingObservationInternalFeignClient billing =
            mock(ConnectorBillingObservationInternalFeignClient.class);
    private final ConnectorPluginActivationInternalFeignClient activation =
            mock(ConnectorPluginActivationInternalFeignClient.class);
    private final ConnectorSpecService spec = mock(ConnectorSpecService.class);
    private final VendorConnectorMigrationServiceImpl service =
            new VendorConnectorMigrationServiceImpl(mapper, access, billing, activation, spec);

    @Test
    void exposesMigrationFactsAsReadOnlyHistory() {
        when(mapper.selectList(any())).thenReturn(List.of());

        assertThat(service.list("stable")).isEmpty();
        verify(mapper).selectList(any());
    }

    @Test
    void preparesOnlyLegacySourceAndCapturesImmutableSourceHash() {
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = facts("ADVANCED_LEGACY", "PLUGIN", 11L);
        when(mapper.lockRuntimeFacts(42L)).thenReturn(facts);
        when(mapper.selectOne(any())).thenReturn(null, prepared(42L, 0));
        when(mapper.insert(any(VendorConnectorMigration.class))).thenAnswer(invocation -> {
            VendorConnectorMigration row = invocation.getArgument(0);
            row.setId(7L);
            return 1;
        });

        VendorConnectorMigrationDTO result = service.prepare(42L, 9L);

        assertThat(result.vendorConfigId()).isEqualTo(42L);
        assertThat(result.state()).isEqualTo("PREPARED");
        assertThat(result.sourceConfigHash()).isEqualTo("a".repeat(64));
        verify(mapper).insert(any(VendorConnectorMigration.class));
    }

    @Test
    void startsObservationOnlyAfterBothAccessInstancesAreReady() {
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = facts("SIMPLE_CONNECTOR", "PLUGIN", 12L);
        VendorConnectorMigration row = prepared(42L, 0);
        when(mapper.lockRuntimeFacts(42L)).thenReturn(facts);
        when(mapper.selectOne(any())).thenReturn(row);
        when(spec.draft(42L)).thenReturn(simpleDraft(88L, 4));
        when(activation.activation("generic-http", "2.0.0"))
                .thenReturn(Result.success(activationSummary(true, "READY", "READY")));
        when(mapper.update(any(), any())).thenReturn(1);

        VendorConnectorMigrationDTO result = service.startObservation(42L,
                new VendorConnectorMigrationStartRequestDTO(0, 0, 1L, 0.1D, 1000L, 1D), 9L);

        assertThat(result.vendorConfigId()).isEqualTo(42L);
        verify(spec).draft(42L);
        verify(activation).activation("generic-http", "2.0.0");
        verify(mapper).update(any(), any());
    }

    @Test
    void rejectsSimpleTargetWhenDraftDoesNotMatchPublishedSnapshot() {
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = facts("SIMPLE_CONNECTOR", "PLUGIN", 12L);
        VendorConnectorMigration row = prepared(42L, 0);
        when(mapper.lockRuntimeFacts(42L)).thenReturn(facts);
        when(mapper.selectOne(any())).thenReturn(row);
        when(spec.draft(42L)).thenReturn(simpleDraft(88L, 4, "b".repeat(64)));

        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                com.dataplatform.masterdata.connector.service.ConnectorConflictException.class,
                () -> service.startObservation(42L,
                        new VendorConnectorMigrationStartRequestDTO(0, 0, 1L, 0.1D, 1000L, 1D), 9L));

        assertThat(exception).hasMessage("CONNECTOR_MIGRATION_TARGET_DRAFT_INVALID");
        org.mockito.Mockito.verifyNoInteractions(activation);
    }

    @Test
    void observesRequestResponseCacheAndBillingFactsAndOpensReadyGate() {
        LocalDateTime started = LocalDateTime.now().minusMinutes(1);
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts = facts("SIMPLE_CONNECTOR", "PLUGIN", 12L);
        VendorConnectorMigration row = observing(42L, 1, started);
        when(mapper.lockRuntimeFacts(42L)).thenReturn(facts);
        when(mapper.selectOne(any())).thenAnswer(invocation -> row);
        when(activation.activation("generic-http", "2.0.0"))
                .thenReturn(Result.success(activationSummary(true, "READY", "READY")));
        when(access.observation(any())).thenReturn(Result.success(
                new ConnectorMigrationObservationDTO(2, 2, 0, 0D, 80, 1, 1)));
        when(billing.observation(any())).thenReturn(Result.success(
                new ConnectorBillingObservationDTO(2, 2, 0, 0, 2, new BigDecimal("0.20"))));
        when(mapper.update(any(), any())).thenAnswer(invocation -> {
            row.setState("READY");
            row.setRecordVersion(2);
            row.setObservedCalls(2L);
            row.setObservedCacheHits(1L);
            row.setObservedRealtimeCalls(1L);
            row.setObservedBillingCoverageRate(1D);
            row.setObservationGatePassed(true);
            return 1;
        });

        VendorConnectorMigrationDTO result = service.observe(42L,
                new VendorConnectorMigrationObserveRequestDTO(1, LocalDateTime.now()), 9L);

        assertThat(result.state()).isEqualTo("READY");
        assertThat(result.observedCalls()).isEqualTo(2L);
        assertThat(result.observedCacheHits()).isEqualTo(1L);
        assertThat(result.observedBillingCoverageRate()).isEqualTo(1D);
        verify(access).observation(any());
        verify(billing).observation(any());
    }

    private VendorConnectorMigrationMapper.MigrationRuntimeFacts facts(
            String authoringMode, String runtimeMode, long activeId) {
        VendorConnectorMigrationMapper.MigrationRuntimeFacts facts =
                new VendorConnectorMigrationMapper.MigrationRuntimeFacts();
        facts.setVendorConfigId(42L);
        facts.setVendorId(100L);
        facts.setInterfaceId(200L);
        facts.setRuntimeMode(runtimeMode);
        facts.setConnectorVersion(3);
        facts.setActiveConnectorVersionId(activeId);
        facts.setActiveVersionNo(2);
        facts.setActiveAuthoringMode(authoringMode);
        facts.setActiveSnapshotHash("a".repeat(64));
        facts.setPluginId("generic-http");
        facts.setPluginVersion("2.0.0");
        return facts;
    }

    private VendorConnectorMigration prepared(long configId, int recordVersion) {
        VendorConnectorMigration row = new VendorConnectorMigration();
        row.setId(7L);
        row.setVendorConfigId(configId);
        row.setVendorId(100L);
        row.setInterfaceId(200L);
        row.setState("PREPARED");
        row.setRecordVersion(recordVersion);
        row.setSourceConfigHash("a".repeat(64));
        row.setPreviousActiveConnectorVersionId(11L);
        row.setPreviousConnectorVersion(1);
        return row;
    }

    private VendorConnectorMigration observing(long configId, int recordVersion, LocalDateTime started) {
        VendorConnectorMigration row = prepared(configId, recordVersion);
        row.setState("OBSERVING");
        row.setDraftId(88L);
        row.setDraftVersion(4);
        row.setDraftSnapshotHash("a".repeat(64));
        row.setPublishedConnectorVersionId(12L);
        row.setPublishedVersionNo(2);
        row.setMinimumObservationMinutes(0);
        row.setMinimumCalls(1L);
        row.setMaximumErrorRate(0.1D);
        row.setMaximumP95DurationMs(1000L);
        row.setMinimumBillingCoverageRate(1D);
        row.setObservationStartedAt(started);
        row.setObservationEligibleAt(started);
        row.setObservationGatePassed(false);
        return row;
    }

    private ConnectorSpecDraftViewDTO simpleDraft(long id, int draftVersion) {
        return simpleDraft(id, draftVersion, "a".repeat(64));
    }

    private ConnectorSpecDraftViewDTO simpleDraft(long id, int draftVersion, String snapshotHash) {
        return new ConnectorSpecDraftViewDTO(true, id, 42L, draftVersion, "SIMPLE_CONNECTOR", 3,
                null, "b".repeat(64), "connector-spec-compiler/2", "c".repeat(64), snapshotHash);
    }

    private ConnectorPluginActivationSummaryDTO activationSummary(
            boolean ready, String firstState, String secondState) {
        ConnectorPluginActivationSummaryDTO summary = new ConnectorPluginActivationSummaryDTO();
        summary.setPluginId("generic-http");
        summary.setPluginVersion("2.0.0");
        summary.setReady(ready);
        summary.setInstances(List.of(activation(firstState), activation(secondState)));
        return summary;
    }

    private ConnectorPluginActivationDTO activation(String state) {
        ConnectorPluginActivationDTO activation = new ConnectorPluginActivationDTO();
        activation.setState(state);
        return activation;
    }
}
