package com.dataplatform.governance.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.governance.monitor.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {
}
