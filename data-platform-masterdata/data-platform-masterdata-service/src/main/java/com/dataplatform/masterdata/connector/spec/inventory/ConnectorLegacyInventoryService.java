package com.dataplatform.masterdata.connector.spec.inventory;

import com.dataplatform.masterdata.connector.api.dto.ConnectorLegacyInventoryDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.service.LegacyHttpConversionClassification;
import com.dataplatform.masterdata.connector.service.LegacyHttpConversionPolicy;
import com.dataplatform.masterdata.connector.service.LegacyHttpConversionPreflightResult;
import com.dataplatform.masterdata.connector.service.LegacyHttpConversionReason;
import com.dataplatform.masterdata.connector.service.LegacyHttpSpecConverter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only classification of current Legacy connector versions, one entry per vendor config. */
@Service
public class ConnectorLegacyInventoryService {
    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SAFE_MESSAGE = 256;
    private static final int MAX_STAGE_KEY = 128;
    private static final String LEGACY = "ADVANCED_LEGACY";
    private static final TypeReference<List<ConnectorPipelineStepDTO>> PIPELINE_TYPE =
            new TypeReference<>() { };

    private final ConnectorLegacyInventoryMapper mapper;
    private final LegacyHttpSpecConverter converter;
    private final ObjectMapper objectMapper;
    private final ConnectorLegacyInventoryMetrics metrics;

    public ConnectorLegacyInventoryService(
            ConnectorLegacyInventoryMapper mapper,
            LegacyHttpSpecConverter converter,
            ObjectMapper objectMapper,
            ConnectorLegacyInventoryMetrics metrics) {
        this.mapper = mapper;
        this.converter = converter;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConnectorLegacyInventoryDTO inventory(Integer page, Integer pageSize) {
        int safePage = requirePage(page);
        int safePageSize = requirePageSize(pageSize);
        long offset;
        try {
            offset = Math.multiplyExact((long) safePage - 1L, safePageSize);
        } catch (ArithmeticException exception) {
            metrics.failed("INVALID_REQUEST");
            throw new IllegalArgumentException("LEGACY_INVENTORY_PAGE_INVALID");
        }

        long total;
        List<ConnectorLegacyInventoryMapper.InventoryFact> facts;
        try {
            total = mapper.countLegacyConfigs();
            facts = mapper.findPage(safePageSize, offset);
        } catch (RuntimeException exception) {
            metrics.failed("QUERY_FAILED");
            throw exception;
        }
        if (total < 0 || facts == null) {
            metrics.failed("FACT_INVALID");
            throw new IllegalStateException("LEGACY_INVENTORY_FACTS_INVALID");
        }

        List<ConnectorLegacyInventoryDTO.Entry> items = new ArrayList<>(facts.size());
        Set<Long> configIds = new HashSet<>();
        MutableSummary summary = new MutableSummary();
        for (ConnectorLegacyInventoryMapper.InventoryFact fact : facts) {
            validateFact(fact, configIds);
            ConnectorLegacyInventoryDTO.Candidate active = candidate(
                    "ACTIVE", fact.getActiveConnectorVersionId(), fact.getActiveVersionNo(),
                    fact.getActiveDraftVersion(), fact.getActiveAuthoringMode(),
                    fact.getActivePipelineSnapshot(), fact.getTimeout(), summary);
            ConnectorLegacyInventoryDTO.Candidate draft = candidate(
                    "DRAFT", fact.getDraftConnectorVersionId(), fact.getDraftVersionNo(),
                    fact.getDraftDraftVersion(), fact.getDraftAuthoringMode(),
                    fact.getDraftPipelineSnapshot(), fact.getTimeout(), summary);
            if (active == null && draft == null) {
                metrics.failed("FACT_INVALID");
                throw new IllegalStateException("LEGACY_INVENTORY_FACTS_INVALID");
            }
            if (active != null) summary.legacyActive++;
            if (draft != null) summary.legacyDraft++;
            items.add(new ConnectorLegacyInventoryDTO.Entry(
                    fact.getVendorConfigId(), fact.getVendorCode(), fact.getDataTypeCode(),
                    active, draft));
        }
        items.sort(Comparator
                .comparing((ConnectorLegacyInventoryDTO.Entry entry) -> entry.active() == null)
                .thenComparing(ConnectorLegacyInventoryDTO.Entry::vendorConfigId));
        ConnectorLegacyInventoryDTO.PageSummary pageSummary =
                new ConnectorLegacyInventoryDTO.PageSummary(
                        items.size(), summary.legacyDraft, summary.legacyActive,
                        summary.lossless, summary.dedicated, summary.legacy);
        return new ConnectorLegacyInventoryDTO(total, safePage, safePageSize, pageSummary, items);
    }

    private ConnectorLegacyInventoryDTO.Candidate candidate(
            String role, Long id, Integer versionNo, Integer draftVersion,
            String authoringMode, String pipelineJson, Integer timeout,
            MutableSummary summary) {
        if (!LEGACY.equals(authoringMode)) return null;
        boolean versionFactsValid = "ACTIVE".equals(role)
                ? versionNo != null && versionNo > 0 && draftVersion != null && draftVersion == 0
                : "DRAFT".equals(role) && versionNo == null
                    && draftVersion != null && draftVersion > 0;
        if (id == null || id <= 0 || !versionFactsValid || pipelineJson == null) {
            metrics.failed("FACT_INVALID");
            throw new IllegalStateException("LEGACY_INVENTORY_FACTS_INVALID");
        }

        LegacyHttpConversionPreflightResult result;
        try {
            List<ConnectorPipelineStepDTO> pipeline = objectMapper.readValue(pipelineJson, PIPELINE_TYPE);
            LegacyHttpConversionPolicy policy = timeout == null || timeout <= 0
                    ? null : new LegacyHttpConversionPolicy(timeout);
            result = converter.preflight(pipeline, policy);
        } catch (RuntimeException | java.io.IOException exception) {
            metrics.failed("PIPELINE_INVALID");
            LegacyHttpConversionClassification classification =
                    LegacyHttpConversionClassification.MUST_REMAIN_LEGACY;
            metrics.classified(classification);
            increment(classification, summary);
            return new ConnectorLegacyInventoryDTO.Candidate(
                    id, role, versionNo, draftVersion, LEGACY, classification.name(),
                    List.of(new ConnectorLegacyInventoryDTO.Reason(
                            "PIPELINE_SNAPSHOT_INVALID", null, null, "流水线快照无法安全解析")));
        }
        metrics.classified(result.classification());
        increment(result.classification(), summary);
        List<ConnectorLegacyInventoryDTO.Reason> reasons = result.reasons().stream()
                .map(this::reason)
                .toList();
        return new ConnectorLegacyInventoryDTO.Candidate(
                id, role, versionNo, draftVersion, LEGACY,
                result.classification().name(), reasons);
    }

    private ConnectorLegacyInventoryDTO.Reason reason(LegacyHttpConversionReason reason) {
        return new ConnectorLegacyInventoryDTO.Reason(
                reason.code().name(), reason.stepIndex(),
                sanitize(reason.stageKey(), MAX_STAGE_KEY),
                sanitize(reason.detail(), MAX_SAFE_MESSAGE));
    }

    private void validateFact(ConnectorLegacyInventoryMapper.InventoryFact fact, Set<Long> configIds) {
        if (fact == null || fact.getVendorConfigId() == null || fact.getVendorConfigId() <= 0
                || !configIds.add(fact.getVendorConfigId())
                || fact.getVendorCode() == null || fact.getVendorCode().isBlank()
                || fact.getDataTypeCode() == null || fact.getDataTypeCode().isBlank()) {
            metrics.failed("FACT_INVALID");
            throw new IllegalStateException("LEGACY_INVENTORY_FACTS_INVALID");
        }
    }

    private int requirePage(Integer page) {
        if (page == null || page <= 0) {
            metrics.failed("INVALID_REQUEST");
            throw new IllegalArgumentException("LEGACY_INVENTORY_PAGE_INVALID");
        }
        return page;
    }

    private int requirePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            metrics.failed("INVALID_REQUEST");
            throw new IllegalArgumentException("LEGACY_INVENTORY_PAGE_SIZE_INVALID");
        }
        return pageSize;
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return null;
        StringBuilder safe = new StringBuilder(Math.min(value.length(), maxLength));
        for (int index = 0; index < value.length() && safe.length() < maxLength; index++) {
            char character = value.charAt(index);
            if (character == '\r' || character == '\n' || Character.isISOControl(character)) {
                safe.append(' ');
            } else {
                safe.append(character);
            }
        }
        return safe.toString();
    }

    private static void increment(
            LegacyHttpConversionClassification classification, MutableSummary summary) {
        switch (classification) {
            case LOSSLESS_CONVERTIBLE -> summary.lossless++;
            case REQUIRES_DEDICATED_PLUGIN -> summary.dedicated++;
            case MUST_REMAIN_LEGACY -> summary.legacy++;
        }
    }

    private static final class MutableSummary {
        private int legacyDraft;
        private int legacyActive;
        private int lossless;
        private int dedicated;
        private int legacy;
    }
}
