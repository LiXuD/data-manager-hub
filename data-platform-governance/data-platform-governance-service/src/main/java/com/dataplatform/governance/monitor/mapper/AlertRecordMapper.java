package com.dataplatform.governance.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.governance.monitor.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 观测治理域监控告警的 Alert Record Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {
}
