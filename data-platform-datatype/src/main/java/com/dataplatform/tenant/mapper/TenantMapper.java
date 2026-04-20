package com.dataplatform.datatype.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.datatype.entity.DataTypeInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataTypeMapper extends BaseMapper<DataTypeInfo> {
}