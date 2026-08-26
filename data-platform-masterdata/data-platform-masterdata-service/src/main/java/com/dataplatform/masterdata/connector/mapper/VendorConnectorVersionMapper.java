package com.dataplatform.masterdata.connector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VendorConnectorVersionMapper extends BaseMapper<VendorConnectorVersion> {
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
}
