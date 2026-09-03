package com.dataplatform.masterdata.connector.spec;

import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Read-only control-plane facts. No query selects secret material or artifact bytes. */
@Mapper
public interface ConnectorSpecFactsMapper {

    @Select("""
            SELECT vc.id AS vendor_config_id, vc.vendor_id, vc.security_version, vc.timeout,
                   vc.active_connector_version_id,
                   vi.vendor_code, dt.data_type_code
            FROM vendor_config vc
            JOIN vendor_info vi ON vi.id = vc.vendor_id AND COALESCE(vi.deleted, FALSE) = FALSE
            JOIN data_type dt ON dt.id = vc.data_type_id AND COALESCE(dt.deleted, FALSE) = FALSE
            WHERE vc.id = #{configId} AND COALESCE(vc.deleted, FALSE) = FALSE
            """)
    VendorFacts findVendorFacts(@Param("configId") Long configId);

    @Select("""
            SELECT cpv.*
            FROM connector_plugin_version cpv
            JOIN connector_plugin cp ON cp.plugin_id = cpv.plugin_id
                 AND COALESCE(cp.deleted, FALSE) = FALSE
            WHERE cpv.manifest_version = '2'
              AND cpv.authoring_model = 'SIMPLE_CONNECTOR'
              AND cpv.status IN ('STAGING', 'ACTIVE')
              AND cp.status = 'ACTIVE'
            ORDER BY cpv.plugin_id ASC, cpv.version ASC
            """)
    List<ConnectorPluginVersion> findSimpleCatalogVersions();

    @Select("""
            SELECT plugin_id, display_name, provider, description, status
            FROM connector_plugin
            WHERE plugin_id = #{pluginId} AND COALESCE(deleted, FALSE) = FALSE
            """)
    CatalogPluginFacts findCatalogPlugin(@Param("pluginId") String pluginId);

    @Select("""
            SELECT cpv.*
            FROM connector_plugin_version cpv
            WHERE cpv.plugin_id = #{pluginId} AND cpv.version = #{pluginVersion}
            """)
    ConnectorPluginVersion findPluginVersion(@Param("pluginId") String pluginId,
                                             @Param("pluginVersion") String pluginVersion);

    @Select("""
            SELECT * FROM vendor_connector_version
            WHERE vendor_config_id = #{configId} AND status = 'DRAFT'
            LIMIT 1
            """)
    VendorConnectorVersion findDraft(@Param("configId") Long configId);

    @Select("""
            SELECT * FROM vendor_connector_version
            WHERE id = #{id} AND vendor_config_id = #{configId}
            """)
    VendorConnectorVersion findConnectorById(@Param("configId") Long configId,
                                             @Param("id") Long id);

    @Select("""
            SELECT * FROM vendor_connector_version
            WHERE vendor_config_id = #{configId} AND version_no = #{version}
              AND status <> 'DRAFT'
            LIMIT 1
            """)
    VendorConnectorVersion findHistory(@Param("configId") Long configId,
                                       @Param("version") Integer version);

    @Select("""
            SELECT config_snapshot FROM vendor_interface_security_version
            WHERE vendor_config_id = #{configId} AND version_no = #{version}
            """)
    String findSecuritySnapshot(@Param("configId") Long configId,
                                @Param("version") Integer version);

    @Select("""
            SELECT ref FROM (
                SELECT 'vendor.secretKey'::TEXT AS ref
                FROM vendor_info
                WHERE id = #{vendorId} AND COALESCE(deleted, FALSE) = FALSE
                  AND secret_key IS NOT NULL AND btrim(secret_key) <> ''
                UNION ALL
                SELECT config_key AS ref
                FROM vendor_config_extended
                WHERE vendor_id = #{vendorId}
                  AND is_active = TRUE
                  AND is_encrypted = TRUE
                  AND config_value IS NOT NULL AND btrim(config_value) <> ''
            ) owned
            ORDER BY ref
            """)
    List<String> findOwnedSecretRefs(@Param("vendorId") Long vendorId);

    final class VendorFacts {
        private Long vendorConfigId;
        private Long vendorId;
        private Integer securityVersion;
        private Integer timeout;
        private Long activeConnectorVersionId;
        private String vendorCode;
        private String dataTypeCode;

        public Long getVendorConfigId() { return vendorConfigId; }
        public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
        public Long getVendorId() { return vendorId; }
        public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
        public Integer getSecurityVersion() { return securityVersion; }
        public void setSecurityVersion(Integer securityVersion) { this.securityVersion = securityVersion; }
        public Integer getTimeout() { return timeout; }
        public void setTimeout(Integer timeout) { this.timeout = timeout; }
        public Long getActiveConnectorVersionId() { return activeConnectorVersionId; }
        public void setActiveConnectorVersionId(Long activeConnectorVersionId) {
            this.activeConnectorVersionId = activeConnectorVersionId;
        }
        public String getVendorCode() { return vendorCode; }
        public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
        public String getDataTypeCode() { return dataTypeCode; }
        public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    }

    final class CatalogPluginFacts {
        private String pluginId;
        private String displayName;
        private String provider;
        private String description;
        private String status;

        public String getPluginId() { return pluginId; }
        public void setPluginId(String pluginId) { this.pluginId = pluginId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
