package com.dataplatform.governance.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.governance.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 观测治理域操作日志的 Operation Log Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
