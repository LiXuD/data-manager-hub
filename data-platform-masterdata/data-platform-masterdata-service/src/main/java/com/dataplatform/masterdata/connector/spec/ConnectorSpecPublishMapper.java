package com.dataplatform.masterdata.connector.spec;

import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Atomic persistence operations for publishing a compiled SIMPLE connector draft. */
@Mapper
public interface ConnectorSpecPublishMapper {

    @Select("""
            SELECT id, connector_version, active_connector_version_id, runtime_mode
            FROM vendor_config
            WHERE id = #{configId} AND COALESCE(deleted, FALSE) = FALSE
            FOR UPDATE
            """)
    ControlFacts lockControl(@Param("configId") Long configId);

    @Select("""
            SELECT *
            FROM vendor_connector_version
            WHERE vendor_config_id = #{configId} AND status = 'DRAFT'
            FOR UPDATE
            """)
    VendorConnectorVersion lockDraft(@Param("configId") Long configId);

    @Select("""
            SELECT *
            FROM vendor_connector_version
            WHERE vendor_config_id = #{configId} AND status <> 'DRAFT'
            ORDER BY version_no DESC, id DESC
            """)
    List<VendorConnectorVersion> findHistoryVersions(@Param("configId") Long configId);

    @Select("""
            SELECT *
            FROM vendor_connector_version
            WHERE vendor_config_id = #{configId}
              AND version_no = #{versionNo}
              AND status IN ('ACTIVE', 'SUPERSEDED')
            FOR UPDATE
            """)
    VendorConnectorVersion lockTarget(@Param("configId") Long configId,
                                      @Param("versionNo") Integer versionNo);

    @Select("""
            SELECT *
            FROM vendor_connector_version
            WHERE vendor_config_id = #{configId} AND status = 'ACTIVE'
            ORDER BY id
            FOR UPDATE
            """)
    List<VendorConnectorVersion> lockActive(@Param("configId") Long configId);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM vendor_connector_version
            WHERE vendor_config_id = #{configId}
            """)
    Integer maxVersionNo(@Param("configId") Long configId);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM vendor_connector_test_fact
                WHERE vendor_config_id = #{vendorConfigId}
                  AND draft_version = #{draftVersion}
                  AND spec_hash = #{specHash}
                  AND snapshot_hash = #{snapshotHash}
                  AND compile_hash = #{compileHash}
                  AND authoring_mode = 'SIMPLE_CONNECTOR'
                  AND test_succeeded = TRUE
            )
            """)
    boolean hasSuccessfulTestFact(@Param("vendorConfigId") Long vendorConfigId,
                                  @Param("draftVersion") Integer draftVersion,
                                  @Param("specHash") String specHash,
                                  @Param("snapshotHash") String snapshotHash,
                                  @Param("compileHash") String compileHash);

    @Insert("""
            WITH matched_draft AS MATERIALIZED (
                SELECT draft.id
                FROM vendor_connector_version draft
                WHERE draft.id = #{row.draftId}
                  AND draft.vendor_config_id = #{row.vendorConfigId}
                  AND draft.status = 'DRAFT'
                  AND draft.authoring_mode = 'SIMPLE_CONNECTOR'
                  AND draft.draft_version = #{row.expectedDraftVersion}
                  AND draft.security_version = #{row.securityVersion}
                  AND draft.spec_hash = #{row.specHash}
                  AND draft.compiler_version = #{row.compilerVersion}
                  AND draft.compile_hash = #{row.compileHash}
                  AND draft.connector_spec = CAST(#{row.connectorSpec} AS jsonb)
                  AND draft.pipeline_snapshot = CAST(#{row.pipelineSnapshot} AS jsonb)
                  AND draft.snapshot_hash IS NULL
                  AND draft.hash_algorithm IS NULL
                  AND draft.integrity_hash IS NULL
                FOR KEY SHARE
            )
            INSERT INTO vendor_connector_version (
                vendor_config_id, version_no, draft_version, pipeline_snapshot,
                snapshot_hash, hash_algorithm, integrity_hash, authoring_mode,
                connector_spec, spec_hash, compiler_version, compile_hash,
                security_version, status, previous_version_id, published_at,
                published_by, created_by, created_at, updated_by, updated_at)
            SELECT #{row.vendorConfigId}, #{row.versionNo}, 0,
                   CAST(#{row.pipelineSnapshot} AS jsonb), #{row.snapshotHash},
                   'V2_EMBEDDED', #{row.snapshotHash}, 'SIMPLE_CONNECTOR',
                   CAST(#{row.connectorSpec} AS jsonb), #{row.specHash},
                   #{row.compilerVersion}, #{row.compileHash}, #{row.securityVersion},
                   'ACTIVE', #{row.previousVersionId}, #{row.publishedAt},
                   #{row.actorId}, #{row.actorId}, #{row.publishedAt},
                   #{row.actorId}, #{row.publishedAt}
            FROM matched_draft
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertPublished(@Param("row") PublishedWrite row);

    @Insert("""
            WITH matched_target AS MATERIALIZED (
                SELECT target.id, target.pipeline_snapshot, target.snapshot_hash,
                       target.hash_algorithm, target.integrity_hash, target.authoring_mode,
                       target.connector_spec, target.spec_hash, target.compiler_version,
                       target.compile_hash, target.security_version
                FROM vendor_connector_version target
                WHERE target.id = #{row.targetId}
                  AND target.vendor_config_id = #{row.vendorConfigId}
                  AND target.version_no = #{row.targetVersionNo}
                  AND target.status IN ('ACTIVE', 'SUPERSEDED')
                  AND target.authoring_mode = #{row.authoringMode}
                  AND target.security_version IS NOT DISTINCT FROM #{row.securityVersion}
                  AND target.snapshot_hash IS NOT DISTINCT FROM #{row.snapshotHash}
                  AND target.hash_algorithm IS NOT DISTINCT FROM #{row.hashAlgorithm}
                  AND target.integrity_hash IS NOT DISTINCT FROM #{row.integrityHash}
                  AND target.spec_hash IS NOT DISTINCT FROM #{row.specHash}
                  AND target.compiler_version IS NOT DISTINCT FROM #{row.compilerVersion}
                  AND target.compile_hash IS NOT DISTINCT FROM #{row.compileHash}
                  AND (target.connector_spec IS NULL AND CAST(#{row.connectorSpec} AS jsonb) IS NULL
                       OR target.connector_spec = CAST(#{row.connectorSpec} AS jsonb))
                  AND target.pipeline_snapshot = CAST(#{row.pipelineSnapshot} AS jsonb)
                FOR KEY SHARE
            )
            INSERT INTO vendor_connector_version (
                vendor_config_id, version_no, draft_version, pipeline_snapshot,
                snapshot_hash, hash_algorithm, integrity_hash, authoring_mode,
                connector_spec, spec_hash, compiler_version, compile_hash,
                security_version, status, previous_version_id, published_at,
                published_by, created_by, created_at, updated_by, updated_at)
            SELECT #{row.vendorConfigId}, #{row.versionNo}, 0,
                   matched_target.pipeline_snapshot, matched_target.snapshot_hash,
                   matched_target.hash_algorithm, matched_target.integrity_hash,
                   matched_target.authoring_mode, matched_target.connector_spec,
                   matched_target.spec_hash, matched_target.compiler_version,
                   matched_target.compile_hash, matched_target.security_version,
                   'ACTIVE', #{row.previousVersionId}, #{row.publishedAt},
                   #{row.actorId}, #{row.actorId}, #{row.publishedAt},
                   #{row.actorId}, #{row.publishedAt}
            FROM matched_target
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertRollback(@Param("row") RollbackWrite row);

    @Update("""
            UPDATE vendor_connector_version
            SET status = 'SUPERSEDED', updated_by = #{actorId}, updated_at = #{updatedAt}
            WHERE id = #{id} AND vendor_config_id = #{vendorConfigId} AND status = 'ACTIVE'
            """)
    int supersedeActive(@Param("id") Long id,
                        @Param("vendorConfigId") Long vendorConfigId,
                        @Param("actorId") Long actorId,
                        @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE vendor_config
            SET active_connector_version_id = #{newActiveId},
                connector_version = #{expectedConnectorVersion} + 1,
                updated_at = #{updatedAt}
            WHERE id = #{configId}
              AND COALESCE(deleted, FALSE) = FALSE
              AND runtime_mode = 'PLUGIN'
              AND connector_version = #{expectedConnectorVersion}
              AND active_connector_version_id IS NOT DISTINCT FROM #{expectedActiveId}
            """)
    int casActivePointer(@Param("configId") Long configId,
                         @Param("expectedConnectorVersion") Integer expectedConnectorVersion,
                         @Param("expectedActiveId") Long expectedActiveId,
                         @Param("newActiveId") Long newActiveId,
                         @Param("updatedAt") LocalDateTime updatedAt);

    final class ControlFacts {
        private Long id;
        private Integer connectorVersion;
        private Long activeConnectorVersionId;
        private String runtimeMode;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getConnectorVersion() { return connectorVersion; }
        public void setConnectorVersion(Integer connectorVersion) { this.connectorVersion = connectorVersion; }
        public Long getActiveConnectorVersionId() { return activeConnectorVersionId; }
        public void setActiveConnectorVersionId(Long activeConnectorVersionId) {
            this.activeConnectorVersionId = activeConnectorVersionId;
        }
        public String getRuntimeMode() { return runtimeMode; }
        public void setRuntimeMode(String runtimeMode) { this.runtimeMode = runtimeMode; }
    }

    final class PublishedWrite {
        private Long id;
        private Long draftId;
        private Long vendorConfigId;
        private Integer expectedDraftVersion;
        private Integer versionNo;
        private String pipelineSnapshot;
        private String snapshotHash;
        private String connectorSpec;
        private String specHash;
        private String compilerVersion;
        private String compileHash;
        private Integer securityVersion;
        private Long previousVersionId;
        private LocalDateTime publishedAt;
        private Long actorId;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getDraftId() { return draftId; }
        public void setDraftId(Long draftId) { this.draftId = draftId; }
        public Long getVendorConfigId() { return vendorConfigId; }
        public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
        public Integer getExpectedDraftVersion() { return expectedDraftVersion; }
        public void setExpectedDraftVersion(Integer expectedDraftVersion) {
            this.expectedDraftVersion = expectedDraftVersion;
        }
        public Integer getVersionNo() { return versionNo; }
        public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
        public String getPipelineSnapshot() { return pipelineSnapshot; }
        public void setPipelineSnapshot(String pipelineSnapshot) { this.pipelineSnapshot = pipelineSnapshot; }
        public String getSnapshotHash() { return snapshotHash; }
        public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
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
        public Long getPreviousVersionId() { return previousVersionId; }
        public void setPreviousVersionId(Long previousVersionId) { this.previousVersionId = previousVersionId; }
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public Long getActorId() { return actorId; }
        public void setActorId(Long actorId) { this.actorId = actorId; }
    }

    final class RollbackWrite {
        private Long id;
        private Long targetId;
        private Long vendorConfigId;
        private Integer targetVersionNo;
        private Integer versionNo;
        private String pipelineSnapshot;
        private String snapshotHash;
        private String hashAlgorithm;
        private String integrityHash;
        private String authoringMode;
        private String connectorSpec;
        private String specHash;
        private String compilerVersion;
        private String compileHash;
        private Integer securityVersion;
        private Long previousVersionId;
        private LocalDateTime publishedAt;
        private Long actorId;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTargetId() { return targetId; }
        public void setTargetId(Long targetId) { this.targetId = targetId; }
        public Long getVendorConfigId() { return vendorConfigId; }
        public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
        public Integer getTargetVersionNo() { return targetVersionNo; }
        public void setTargetVersionNo(Integer targetVersionNo) { this.targetVersionNo = targetVersionNo; }
        public Integer getVersionNo() { return versionNo; }
        public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
        public String getPipelineSnapshot() { return pipelineSnapshot; }
        public void setPipelineSnapshot(String pipelineSnapshot) { this.pipelineSnapshot = pipelineSnapshot; }
        public String getSnapshotHash() { return snapshotHash; }
        public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
        public String getHashAlgorithm() { return hashAlgorithm; }
        public void setHashAlgorithm(String hashAlgorithm) { this.hashAlgorithm = hashAlgorithm; }
        public String getIntegrityHash() { return integrityHash; }
        public void setIntegrityHash(String integrityHash) { this.integrityHash = integrityHash; }
        public String getAuthoringMode() { return authoringMode; }
        public void setAuthoringMode(String authoringMode) { this.authoringMode = authoringMode; }
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
        public Long getPreviousVersionId() { return previousVersionId; }
        public void setPreviousVersionId(Long previousVersionId) { this.previousVersionId = previousVersionId; }
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public Long getActorId() { return actorId; }
        public void setActorId(Long actorId) { this.actorId = actorId; }
    }
}
