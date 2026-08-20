package com.dataplatform.masterdata.connector.spec.inventory;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Read-only, paged facts for current Legacy draft and active connector versions. */
@Mapper
public interface ConnectorLegacyInventoryMapper {

    @Select("""
            SELECT COUNT(DISTINCT vc.id)
            FROM vendor_config vc
            JOIN vendor_info vi
              ON vi.id = vc.vendor_id
             AND COALESCE(vi.deleted, FALSE) = FALSE
            JOIN data_type dt
              ON dt.id = vc.data_type_id
             AND COALESCE(dt.deleted, FALSE) = FALSE
            LEFT JOIN vendor_connector_version draft
              ON draft.vendor_config_id = vc.id
             AND draft.status = 'DRAFT'
            LEFT JOIN vendor_connector_version active
              ON active.id = vc.active_connector_version_id
             AND active.vendor_config_id = vc.id
             AND active.status = 'ACTIVE'
            WHERE COALESCE(vc.deleted, FALSE) = FALSE
              AND (draft.authoring_mode = 'ADVANCED_LEGACY'
                   OR active.authoring_mode = 'ADVANCED_LEGACY')
            """)
    long countLegacyConfigs();

    @Select("""
            WITH inventory AS (
                SELECT vc.id AS vendor_config_id,
                       vc.timeout,
                       vi.vendor_code,
                       dt.data_type_code,
                       draft.id AS draft_connector_version_id,
                       draft.version_no AS draft_version_no,
                       draft.draft_version AS draft_draft_version,
                       draft.authoring_mode AS draft_authoring_mode,
                       draft.pipeline_snapshot::text AS draft_pipeline_snapshot,
                       active.id AS active_connector_version_id,
                       active.version_no AS active_version_no,
                       active.draft_version AS active_draft_version,
                       active.authoring_mode AS active_authoring_mode,
                       active.pipeline_snapshot::text AS active_pipeline_snapshot
                FROM vendor_config vc
                JOIN vendor_info vi
                  ON vi.id = vc.vendor_id
                 AND COALESCE(vi.deleted, FALSE) = FALSE
                JOIN data_type dt
                  ON dt.id = vc.data_type_id
                 AND COALESCE(dt.deleted, FALSE) = FALSE
                LEFT JOIN vendor_connector_version draft
                  ON draft.vendor_config_id = vc.id
                 AND draft.status = 'DRAFT'
                LEFT JOIN vendor_connector_version active
                  ON active.id = vc.active_connector_version_id
                 AND active.vendor_config_id = vc.id
                 AND active.status = 'ACTIVE'
                WHERE COALESCE(vc.deleted, FALSE) = FALSE
                  AND (draft.authoring_mode = 'ADVANCED_LEGACY'
                       OR active.authoring_mode = 'ADVANCED_LEGACY')
            )
            SELECT inventory.*
            FROM inventory
            ORDER BY CASE WHEN active_authoring_mode = 'ADVANCED_LEGACY' THEN 0 ELSE 1 END,
                     vendor_config_id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<InventoryFact> findPage(@Param("limit") int limit, @Param("offset") long offset);

    final class InventoryFact {
        private Long vendorConfigId;
        private Integer timeout;
        private String vendorCode;
        private String dataTypeCode;
        private Long draftConnectorVersionId;
        private Integer draftVersionNo;
        private Integer draftDraftVersion;
        private String draftAuthoringMode;
        private String draftPipelineSnapshot;
        private Long activeConnectorVersionId;
        private Integer activeVersionNo;
        private Integer activeDraftVersion;
        private String activeAuthoringMode;
        private String activePipelineSnapshot;

        public Long getVendorConfigId() { return vendorConfigId; }
        public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
        public Integer getTimeout() { return timeout; }
        public void setTimeout(Integer timeout) { this.timeout = timeout; }
        public String getVendorCode() { return vendorCode; }
        public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
        public String getDataTypeCode() { return dataTypeCode; }
        public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
        public Long getDraftConnectorVersionId() { return draftConnectorVersionId; }
        public void setDraftConnectorVersionId(Long draftConnectorVersionId) {
            this.draftConnectorVersionId = draftConnectorVersionId;
        }
        public Integer getDraftVersionNo() { return draftVersionNo; }
        public void setDraftVersionNo(Integer draftVersionNo) { this.draftVersionNo = draftVersionNo; }
        public Integer getDraftDraftVersion() { return draftDraftVersion; }
        public void setDraftDraftVersion(Integer draftDraftVersion) {
            this.draftDraftVersion = draftDraftVersion;
        }
        public String getDraftAuthoringMode() { return draftAuthoringMode; }
        public void setDraftAuthoringMode(String draftAuthoringMode) {
            this.draftAuthoringMode = draftAuthoringMode;
        }
        public String getDraftPipelineSnapshot() { return draftPipelineSnapshot; }
        public void setDraftPipelineSnapshot(String draftPipelineSnapshot) {
            this.draftPipelineSnapshot = draftPipelineSnapshot;
        }
        public Long getActiveConnectorVersionId() { return activeConnectorVersionId; }
        public void setActiveConnectorVersionId(Long activeConnectorVersionId) {
            this.activeConnectorVersionId = activeConnectorVersionId;
        }
        public Integer getActiveVersionNo() { return activeVersionNo; }
        public void setActiveVersionNo(Integer activeVersionNo) { this.activeVersionNo = activeVersionNo; }
        public Integer getActiveDraftVersion() { return activeDraftVersion; }
        public void setActiveDraftVersion(Integer activeDraftVersion) {
            this.activeDraftVersion = activeDraftVersion;
        }
        public String getActiveAuthoringMode() { return activeAuthoringMode; }
        public void setActiveAuthoringMode(String activeAuthoringMode) {
            this.activeAuthoringMode = activeAuthoringMode;
        }
        public String getActivePipelineSnapshot() { return activePipelineSnapshot; }
        public void setActivePipelineSnapshot(String activePipelineSnapshot) {
            this.activePipelineSnapshot = activePipelineSnapshot;
        }
    }
}
