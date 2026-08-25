package com.dataplatform.access.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApiPermissionApplicationMapper extends BaseMapper<ApiPermissionApplication> {

    @Select("SELECT * FROM api_permission_application WHERE id = #{id} FOR UPDATE")
    ApiPermissionApplication selectByIdForUpdate(Long id);
}
