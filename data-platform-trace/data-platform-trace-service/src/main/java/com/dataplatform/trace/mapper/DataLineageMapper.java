package com.dataplatform.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.trace.entity.DataLineage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataLineageMapper extends BaseMapper<DataLineage> {
}