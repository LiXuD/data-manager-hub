package com.dataplatform.masterdata.connector.spec;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Atomic lifecycle writes for SIMPLE connector controlled tests and publishing. */
@Mapper
public interface ConnectorSpecLifecycleMapper {

    /**
     * The locking CTE is the linearization point between the remote test and a
     * concurrent draft change: the insert either binds the exact draft facts or
     * observes no matching row.
     */
    @Insert("""
            WITH matched_draft AS MATERIALIZED (
                SELECT draft.vendor_config_id, draft.draft_version
                FROM vendor_connector_version draft
                WHERE draft.id = #{fact.draftId}
                  AND draft.vendor_config_id = #{fact.vendorConfigId}
                  AND draft.status = 'DRAFT'
                  AND draft.authoring_mode = 'SIMPLE_CONNECTOR'
                  AND draft.draft_version = #{fact.draftVersion}
                  AND draft.spec_hash = #{fact.specHash}
                  AND draft.compiler_version = #{fact.compilerVersion}
                  AND draft.compile_hash = #{fact.compileHash}
                  AND draft.security_version = #{fact.securityVersion}
                  AND draft.connector_spec = CAST(#{fact.connectorSpec} AS jsonb)
                  AND draft.pipeline_snapshot = CAST(#{fact.pipelineSnapshot} AS jsonb)
                FOR KEY SHARE
            )
            INSERT INTO vendor_connector_test_fact (
                vendor_config_id, draft_version, snapshot_hash, authoring_mode,
                spec_hash, compile_hash, plugin_bindings, test_succeeded,
                safe_error_category, safe_error_code, result_digest, tested_by)
            SELECT matched.vendor_config_id, matched.draft_version, #{fact.snapshotHash},
                   'SIMPLE_CONNECTOR', #{fact.specHash}, #{fact.compileHash},
                   CAST(#{fact.pluginBindings} AS jsonb), #{fact.testSucceeded},
                   #{fact.safeErrorCategory}, #{fact.safeErrorCode}, #{fact.resultDigest}, #{fact.actorId}
            FROM matched_draft matched
            """)
    int insertTestFact(@Param("fact") TestFactWrite fact);

    final class TestFactWrite {
        private Long draftId;
        private Long vendorConfigId;
        private Integer draftVersion;
        private Integer securityVersion;
        private String connectorSpec;
        private String pipelineSnapshot;
        private String specHash;
        private String compilerVersion;
        private String compileHash;
        private String snapshotHash;
        private String pluginBindings;
        private Boolean testSucceeded;
        private String safeErrorCategory;
        private String safeErrorCode;
        private String resultDigest;
        private Long actorId;

        public Long getDraftId() { return draftId; }
        public void setDraftId(Long draftId) { this.draftId = draftId; }
        public Long getVendorConfigId() { return vendorConfigId; }
        public void setVendorConfigId(Long vendorConfigId) { this.vendorConfigId = vendorConfigId; }
        public Integer getDraftVersion() { return draftVersion; }
        public void setDraftVersion(Integer draftVersion) { this.draftVersion = draftVersion; }
        public Integer getSecurityVersion() { return securityVersion; }
        public void setSecurityVersion(Integer securityVersion) { this.securityVersion = securityVersion; }
        public String getConnectorSpec() { return connectorSpec; }
        public void setConnectorSpec(String connectorSpec) { this.connectorSpec = connectorSpec; }
        public String getPipelineSnapshot() { return pipelineSnapshot; }
        public void setPipelineSnapshot(String pipelineSnapshot) { this.pipelineSnapshot = pipelineSnapshot; }
        public String getSpecHash() { return specHash; }
        public void setSpecHash(String specHash) { this.specHash = specHash; }
        public String getCompilerVersion() { return compilerVersion; }
        public void setCompilerVersion(String compilerVersion) { this.compilerVersion = compilerVersion; }
        public String getCompileHash() { return compileHash; }
        public void setCompileHash(String compileHash) { this.compileHash = compileHash; }
        public String getSnapshotHash() { return snapshotHash; }
        public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
        public String getPluginBindings() { return pluginBindings; }
        public void setPluginBindings(String pluginBindings) { this.pluginBindings = pluginBindings; }
        public Boolean getTestSucceeded() { return testSucceeded; }
        public void setTestSucceeded(Boolean testSucceeded) { this.testSucceeded = testSucceeded; }
        public String getSafeErrorCategory() { return safeErrorCategory; }
        public void setSafeErrorCategory(String safeErrorCategory) { this.safeErrorCategory = safeErrorCategory; }
        public String getSafeErrorCode() { return safeErrorCode; }
        public void setSafeErrorCode(String safeErrorCode) { this.safeErrorCode = safeErrorCode; }
        public String getResultDigest() { return resultDigest; }
        public void setResultDigest(String resultDigest) { this.resultDigest = resultDigest; }
        public Long getActorId() { return actorId; }
        public void setActorId(Long actorId) { this.actorId = actorId; }
    }
}
