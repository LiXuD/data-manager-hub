package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

/** Redacted, paged inventory of current Legacy draft/active connector versions. */
public record ConnectorLegacyInventoryDTO(
        long total,
        int page,
        int pageSize,
        PageSummary pageSummary,
        List<Entry> items) implements Serializable {

    public ConnectorLegacyInventoryDTO {
        if (total < 0 || page <= 0 || pageSize <= 0 || pageSummary == null) {
            throw new IllegalArgumentException("Legacy inventory metadata is invalid");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record PageSummary(
            int vendorConfigCount,
            int legacyDraftCount,
            int legacyActiveCount,
            int losslessConvertibleCount,
            int requiresDedicatedPluginCount,
            int mustRemainLegacyCount) implements Serializable { }

    public record Entry(
            Long vendorConfigId,
            String vendorCode,
            String dataTypeCode,
            Candidate active,
            Candidate draft) implements Serializable { }

    public record Candidate(
            Long connectorVersionId,
            String versionRole,
            Integer versionNo,
            Integer draftVersion,
            String authoringMode,
            String classification,
            List<Reason> reasons) implements Serializable {

        public Candidate {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record Reason(
            String code,
            Integer stepIndex,
            String stageKey,
            String safeMessage) implements Serializable { }
}
