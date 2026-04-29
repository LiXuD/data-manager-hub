package com.dataplatform.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.tenant.entity.TenantInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<TenantInfo> {
}