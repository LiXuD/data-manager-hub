package com.dataplatform.call.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.call.entity.CallRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecord> {
}
