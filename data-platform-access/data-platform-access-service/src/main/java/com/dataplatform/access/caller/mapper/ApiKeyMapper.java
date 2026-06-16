package com.dataplatform.access.caller.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.access.caller.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {
}
