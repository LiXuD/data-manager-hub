package com.dataplatform.masterdata.connector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VendorConnectorVersionMapper extends BaseMapper<VendorConnectorVersion> {
    /**
     * Returns the database facts required before the legacy compatibility write
     * surface can be switched to HTTP 410.
     */
    @Select("""
            SELECT
                (SELECT COUNT(*)
                 FROM vendor_config config
                 JOIN vendor_connector_version active
                   ON active.id = config.active_connector_version_id
                  AND active.vendor_config_id = config.id
                  AND active.status = 'ACTIVE'
                 WHERE config.status = 'active'
                   AND COALESCE(config.deleted, FALSE) = FALSE
                   AND active.authoring_mode = 'ADVANCED_LEGACY') AS active_legacy_bindings,
                (SELECT COUNT(*)
                 FROM vendor_connector_version draft
                 JOIN vendor_config config ON config.id = draft.vendor_config_id
                 WHERE draft.status = 'DRAFT'
                   AND draft.authoring_mode = 'ADVANCED_LEGACY'
                   AND COALESCE(config.deleted, FALSE) = FALSE) AS legacy_drafts,
                (SELECT COUNT(*)
                 FROM vendor_connector_migration migration
                 WHERE migration.state NOT IN ('STABLE', 'FAILED', 'ROLLED_BACK')) AS open_migrations
            """)
    LegacyWriteRetirementFacts legacyWriteRetirementFacts();

    @Update("""
            UPDATE vendor_connector_version
            SET pipeline_snapshot = CAST(#{pipelineSnapshot} AS jsonb),
                authoring_mode = 'ADVANCED_LEGACY',
                connector_spec = NULL,
                spec_hash = NULL,
                compiler_version = NULL,
                compile_hash = NULL,
                security_version = #{securityVersion},
                draft_version = #{nextDraftVersion},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND draft_version = #{expectedDraftVersion}
              AND status = 'DRAFT'
              AND authoring_mode = 'ADVANCED_LEGACY'
            """)
    int updateDraft(@Param("id") Long id,
                    @Param("expectedDraftVersion") Integer expectedDraftVersion,
                    @Param("pipelineSnapshot") String pipelineSnapshot,
                    @Param("securityVersion") Integer securityVersion,
                    @Param("nextDraftVersion") Integer nextDraftVersion,
                    @Param("updatedBy") Long updatedBy);

    class LegacyWriteRetirementFacts {
        private Long activeLegacyBindings;
        private Long legacyDrafts;
        private Long openMigrations;

        public Long getActiveLegacyBindings() { return activeLegacyBindings; }
        public void setActiveLegacyBindings(Long value) { this.activeLegacyBindings = value; }
        public Long getLegacyDrafts() { return legacyDrafts; }
        public void setLegacyDrafts(Long value) { this.legacyDrafts = value; }
        public Long getOpenMigrations() { return openMigrations; }
        public void setOpenMigrations(Long value) { this.openMigrations = value; }
    }
}
