package com.dataplatform.access.call.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.common.entity.CallRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问域数据调用的 Call Record Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecord> {
}
