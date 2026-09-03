package com.dataplatform.masterdata.connector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.connector.entity.VendorConnectorMigration;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VendorConnectorMigrationMapper extends BaseMapper<VendorConnectorMigration> {

    /** Locks the control-plane facts used to bind a migration observation to one active snapshot. */
    @Select("""
            SELECT vc.id AS vendor_config_id,
                   vc.vendor_id,
                   vc.interface_id,
                   vc.runtime_mode,
                   vc.connector_version,
                   vc.active_connector_version_id,
                   active.version_no AS active_version_no,
                   active.authoring_mode AS active_authoring_mode,
                   active.snapshot_hash AS active_snapshot_hash,
                   active.pipeline_snapshot::text AS active_pipeline_snapshot,
                   vc.timeout,
                   active.connector_spec -> 'plugin' ->> 'pluginId' AS plugin_id,
                   active.connector_spec -> 'plugin' ->> 'pluginVersion' AS plugin_version
            FROM vendor_config vc
            LEFT JOIN vendor_connector_version active
              ON active.id = vc.active_connector_version_id
             AND active.vendor_config_id = vc.id
             AND active.status = 'ACTIVE'
            WHERE vc.id = #{vendorConfigId}
              AND COALESCE(vc.deleted, FALSE) = FALSE
            FOR UPDATE OF vc
            """)
    MigrationRuntimeFacts lockRuntimeFacts(@Param("vendorConfigId") Long vendorConfigId);

    /** Same facts for dry-run reporting; deliberately does not acquire a row lock. */
    @Select("""
            SELECT vc.id AS vendor_config_id,
                   vc.vendor_id,
                   vc.interface_id,
                   vc.runtime_mode,
                   vc.connector_version,
                   vc.active_connector_version_id,
                   active.version_no AS active_version_no,
                   active.authoring_mode AS active_authoring_mode,
                   active.snapshot_hash AS active_snapshot_hash,
                   active.pipeline_snapshot::text AS active_pipeline_snapshot,
                   vc.timeout,
                   active.connector_spec -> 'plugin' ->> 'pluginId' AS plugin_id,
                   active.connector_spec -> 'plugin' ->> 'pluginVersion' AS plugin_version
            FROM vendor_config vc
            LEFT JOIN vendor_connector_version active
              ON active.id = vc.active_connector_version_id
             AND active.vendor_config_id = vc.id
             AND active.status = 'ACTIVE'
            WHERE vc.id = #{vendorConfigId}
              AND COALESCE(vc.deleted, FALSE) = FALSE
            """)
    MigrationRuntimeFacts readRuntimeFacts(@Param("vendorConfigId") Long vendorConfigId);

    class MigrationRuntimeFacts {
        private Long vendorConfigId;
        private Long vendorId;
        private Long interfaceId;
        private String runtimeMode;
        private Integer connectorVersion;
        private Long activeConnectorVersionId;
        private Integer activeVersionNo;
        private String activeAuthoringMode;
        private String activeSnapshotHash;
        private String activePipelineSnapshot;
        private Integer timeout;
        private String pluginId;
        private String pluginVersion;

        public Long getVendorConfigId() { return vendorConfigId; }
        public void setVendorConfigId(Long value) { this.vendorConfigId = value; }
        public Long getVendorId() { return vendorId; }
        public void setVendorId(Long value) { this.vendorId = value; }
        public Long getInterfaceId() { return interfaceId; }
        public void setInterfaceId(Long value) { this.interfaceId = value; }
        public String getRuntimeMode() { return runtimeMode; }
        public void setRuntimeMode(String value) { this.runtimeMode = value; }
        public Integer getConnectorVersion() { return connectorVersion; }
        public void setConnectorVersion(Integer value) { this.connectorVersion = value; }
        public Long getActiveConnectorVersionId() { return activeConnectorVersionId; }
        public void setActiveConnectorVersionId(Long value) { this.activeConnectorVersionId = value; }
        public Integer getActiveVersionNo() { return activeVersionNo; }
        public void setActiveVersionNo(Integer value) { this.activeVersionNo = value; }
        public String getActiveAuthoringMode() { return activeAuthoringMode; }
        public void setActiveAuthoringMode(String value) { this.activeAuthoringMode = value; }
        public String getActiveSnapshotHash() { return activeSnapshotHash; }
        public void setActiveSnapshotHash(String value) { this.activeSnapshotHash = value; }
        public String getActivePipelineSnapshot() { return activePipelineSnapshot; }
        public void setActivePipelineSnapshot(String value) { this.activePipelineSnapshot = value; }
        public Integer getTimeout() { return timeout; }
        public void setTimeout(Integer value) { this.timeout = value; }
        public String getPluginId() { return pluginId; }
        public void setPluginId(String value) { this.pluginId = value; }
        public String getPluginVersion() { return pluginVersion; }
        public void setPluginVersion(String value) { this.pluginVersion = value; }
    }
}
