package com.dataplatform.masterdata.connector.spec.inventory;

import static com.dataplatform.masterdata.connector.fixture.ConnectorProductModelFixtures.singleHttpLegacyPipeline;
import static com.dataplatform.masterdata.connector.fixture.ConnectorProductModelFixtures.tokenThenBusinessPipeline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.masterdata.connector.api.dto.ConnectorLegacyInventoryDTO;
import com.dataplatform.masterdata.connector.service.LegacyHttpConversionClassification;
import com.dataplatform.masterdata.connector.service.LegacyHttpSpecConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectorLegacyInventoryServiceTest {
    private final ConnectorLegacyInventoryMapper mapper = mock(ConnectorLegacyInventoryMapper.class);
    private final ConnectorLegacyInventoryMetrics metrics = mock(ConnectorLegacyInventoryMetrics.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ConnectorLegacyInventoryService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorLegacyInventoryService(
                mapper, new LegacyHttpSpecConverter(), objectMapper, metrics);
    }

    @Test
    void classifiesDraftOnlyAndActiveFirstWithoutExposingPipelineOrSecrets() throws Exception {
        ConnectorLegacyInventoryMapper.InventoryFact draftOnly = baseFact(20L, "VENDOR_B", "TYPE_B");
        draftOnly.setDraftConnectorVersionId(201L);
        draftOnly.setDraftVersionNo(null);
        draftOnly.setDraftDraftVersion(3);
        draftOnly.setDraftAuthoringMode("ADVANCED_LEGACY");
        draftOnly.setDraftPipelineSnapshot(objectMapper.writeValueAsString(singleHttpLegacyPipeline()));

        ConnectorLegacyInventoryMapper.InventoryFact activeAndDraft = baseFact(10L, "VENDOR_A", "TYPE_A");
        activeAndDraft.setActiveConnectorVersionId(101L);
        activeAndDraft.setActiveVersionNo(4);
        activeAndDraft.setActiveDraftVersion(0);
        activeAndDraft.setActiveAuthoringMode("ADVANCED_LEGACY");
        activeAndDraft.setActivePipelineSnapshot(objectMapper.writeValueAsString(tokenThenBusinessPipeline()));
        activeAndDraft.setDraftConnectorVersionId(102L);
        activeAndDraft.setDraftVersionNo(null);
        activeAndDraft.setDraftDraftVersion(2);
        activeAndDraft.setDraftAuthoringMode("ADVANCED_LEGACY");
        activeAndDraft.setDraftPipelineSnapshot("{not-json");

        when(mapper.countLegacyConfigs()).thenReturn(2L);
        when(mapper.findPage(50, 0L)).thenReturn(List.of(draftOnly, activeAndDraft));

        ConnectorLegacyInventoryDTO result = service.inventory(1, 50);

        assertEquals(2L, result.total());
        assertEquals(2, result.items().size());
        assertEquals(10L, result.items().getFirst().vendorConfigId());
        assertEquals("REQUIRES_DEDICATED_PLUGIN",
                result.items().getFirst().active().classification());
        assertEquals("MUST_REMAIN_LEGACY",
                result.items().getFirst().draft().classification());
        assertEquals("PIPELINE_SNAPSHOT_INVALID",
                result.items().getFirst().draft().reasons().getFirst().code());
        assertNull(result.items().getLast().active());
        assertNull(result.items().getLast().draft().versionNo());
        assertEquals("LOSSLESS_CONVERTIBLE", result.items().getLast().draft().classification());
        assertEquals(new ConnectorLegacyInventoryDTO.PageSummary(2, 2, 1, 1, 1, 1),
                result.pageSummary());
        verify(metrics).classified(LegacyHttpConversionClassification.LOSSLESS_CONVERTIBLE);
        verify(metrics).classified(LegacyHttpConversionClassification.REQUIRES_DEDICATED_PLUGIN);
        verify(metrics).classified(LegacyHttpConversionClassification.MUST_REMAIN_LEGACY);
        verify(metrics).failed("PIPELINE_INVALID");

        String json = objectMapper.writeValueAsString(result);
        assertFalse(json.contains("pipelineSnapshot"));
        assertFalse(json.contains("requestMapping"));
        assertFalse(json.contains("https://fixture.example.test"));
        assertFalse(json.contains("vendor.fixture.token"));
        assertFalse(json.contains("secretRef"));
        assertTrue(json.contains("LOSSLESS_CONVERTIBLE"));
    }

    @Test
    void validatesPaginationBeforeReadingAndFailsClosedOnInvalidDraftFacts() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.inventory(0, 50));
        assertThrows(IllegalArgumentException.class, () -> service.inventory(1, 101));
        verifyNoInteractions(mapper);

        ConnectorLegacyInventoryMapper.InventoryFact invalid = baseFact(1L, "V", "T");
        invalid.setDraftConnectorVersionId(2L);
        invalid.setDraftVersionNo(1);
        invalid.setDraftDraftVersion(1);
        invalid.setDraftAuthoringMode("ADVANCED_LEGACY");
        invalid.setDraftPipelineSnapshot(objectMapper.writeValueAsString(singleHttpLegacyPipeline()));
        when(mapper.countLegacyConfigs()).thenReturn(1L);
        when(mapper.findPage(50, 0L)).thenReturn(List.of(invalid));

        assertThrows(IllegalStateException.class, () -> service.inventory(1, 50));
    }

    private ConnectorLegacyInventoryMapper.InventoryFact baseFact(
            Long configId, String vendorCode, String dataTypeCode) {
        ConnectorLegacyInventoryMapper.InventoryFact fact =
                new ConnectorLegacyInventoryMapper.InventoryFact();
        fact.setVendorConfigId(configId);
        fact.setVendorCode(vendorCode);
        fact.setDataTypeCode(dataTypeCode);
        fact.setTimeout(10_000);
        return fact;
    }
}
