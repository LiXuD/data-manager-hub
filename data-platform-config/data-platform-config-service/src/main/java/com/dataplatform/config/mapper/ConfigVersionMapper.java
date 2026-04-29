package com.dataplatform.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.config.entity.ConfigVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigVersionMapper extends BaseMapper<ConfigVersion> {
}