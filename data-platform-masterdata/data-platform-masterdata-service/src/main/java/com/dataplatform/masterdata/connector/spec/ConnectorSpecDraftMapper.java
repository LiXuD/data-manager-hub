package com.dataplatform.masterdata.connector.spec;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Atomic SIMPLE draft writes isolated from the legacy advanced-pipeline mapper. */
@Mapper
public interface ConnectorSpecDraftMapper {

    @Insert("""
            INSERT INTO vendor_connector_version (
                vendor_config_id, version_no, draft_version, pipeline_snapshot,
                snapshot_hash, hash_algorithm, integrity_hash, authoring_mode,
                connector_spec, spec_hash, compiler_version, compile_hash,
                security_version, status, created_by, updated_by)
            VALUES (
                #{row.vendorConfigId}, NULL, 1, CAST(#{row.pipelineSnapshot} AS jsonb),
                NULL, NULL, NULL, 'SIMPLE_CONNECTOR',
                CAST(#{row.connectorSpec} AS jsonb), #{row.specHash},
                #{row.compilerVersion}, #{row.compileHash}, #{row.securityVersion},
                'DRAFT', #{row.actorId}, #{row.actorId})
            ON CONFLICT (vendor_config_id) WHERE status = 'DRAFT' DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertDraft(@Param("row") DraftWrite row);

    @Update("""
            UPDATE vendor_connector_version
            SET draft_version = #{nextDraftVersion},
                pipeline_snapshot = CAST(#{pipelineSnapshot} AS jsonb),
                connector_spec = CAST(#{connectorSpec} AS jsonb),
                spec_hash = #{specHash}, compiler_version = #{compilerVersion},
                compile_hash = #{compileHash}, security_version = #{securityVersion},
                snapshot_hash = NULL, hash_algorithm = NULL, integrity_hash = NULL,
                updated_by = #{actorId}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND vendor_config_id = #{vendorConfigId}
              AND status = 'DRAFT' AND authoring_mode = 'SIMPLE_CONNECTOR'
              AND draft_version = #{expectedDraftVersion}
            """)
    int updateDraft(@Param("id") Long id,
                    @Param("vendorConfigId") Long vendorConfigId,
                    @Param("expectedDraftVersion") Integer expectedDraftVersion,
                    @Param("nextDraftVersion") Integer nextDraftVersion,
                    @Param("pipelineSnapshot") String pipelineSnapshot,
                    @Param("connectorSpec") String connectorSpec,
                    @Param("specHash") String specHash,
                    @Param("compilerVersion") String compilerVersion,
                    @Param("compileHash") String compileHash,
                    @Param("securityVersion") Integer securityVersion,
                    @Param("actorId") Long actorId);

    @Update("""
            UPDATE vendor_connector_version
            SET draft_version = #{nextDraftVersion},
                pipeline_snapshot = CAST(#{pipelineSnapshot} AS jsonb),
                authoring_mode = 'SIMPLE_CONNECTOR',
                connector_spec = CAST(#{connectorSpec} AS jsonb),
                spec_hash = #{specHash}, compiler_version = #{compilerVersion},
                compile_hash = #{compileHash}, security_version = #{securityVersion},
                snapshot_hash = NULL, hash_algorithm = NULL, integrity_hash = NULL,
                updated_by = #{actorId}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND vendor_config_id = #{vendorConfigId}
              AND status = 'DRAFT' AND authoring_mode = 'ADVANCED_LEGACY'
              AND draft_version = #{expectedDraftVersion}
            """)
    int convertLegacyDraft(@Param("id") Long id,
                           @Param("vendorConfigId") Long vendorConfigId,
                           @Param("expectedDraftVersion") Integer expectedDraftVersion,
                           @Param("nextDraftVersion") Integer nextDraftVersion,
                           @Param("pipelineSnapshot") String pipelineSnapshot,
                           @Param("connectorSpec") String connectorSpec,
                           @Param("specHash") String specHash,
                           @Param("compilerVersion") String compilerVersion,
                           @Param("compileHash") String compileHash,
                           @Param("securityVersion") Integer securityVersion,
                           @Param("actorId") Long actorId);

    final class DraftWrite {
        private Long id;
        private Long vendorConfigId;
        private String pipelineSnapshot;
        private String connectorSpec;
        private String specHash;
        private String compilerVersion;
        private String compileHash;
        private Integer securityVersion;
        private Long actorId;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getVendorConfigId() { return vendorConfigId; }
        public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
        public String getPipelineSnapshot() { return pipelineSnapshot; }
        public void setPipelineSnapshot(String pipelineSnapshot) { this.pipelineSnapshot = pipelineSnapshot; }
        public String getConnectorSpec() { return connectorSpec; }
        public void setConnectorSpec(String connectorSpec) { this.connectorSpec = connectorSpec; }
        public String getSpecHash() { return specHash; }
        public void setSpecHash(String specHash) { this.specHash = specHash; }
        public String getCompilerVersion() { return compilerVersion; }
        public void setCompilerVersion(String compilerVersion) { this.compilerVersion = compilerVersion; }
        public String getCompileHash() { return compileHash; }
        public void setCompileHash(String compileHash) { this.compileHash = compileHash; }
        public Integer getSecurityVersion() { return securityVersion; }
        public void setSecurityVersion(Integer securityVersion) { this.securityVersion = securityVersion; }
        public Long getActorId() { return actorId; }
        public void setActorId(Long actorId) { this.actorId = actorId; }
    }
}
