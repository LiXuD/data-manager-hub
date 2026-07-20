package com.dataplatform.governance.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.governance.monitor.entity.ServiceHealthCheck;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceHealthCheckMapper extends BaseMapper<ServiceHealthCheck> {
}
