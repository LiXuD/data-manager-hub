package com.dataplatform.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
