package com.dataplatform.masterdata.connector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConnectorPluginVersionMapper extends BaseMapper<ConnectorPluginVersion> {
}
