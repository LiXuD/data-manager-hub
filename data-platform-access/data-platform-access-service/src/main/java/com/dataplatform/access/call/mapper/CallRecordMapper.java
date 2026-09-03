package com.dataplatform.access.call.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.common.entity.CallRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 访问域数据调用的 Call Record Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecord> {

    @Select("""
            SELECT cache_response_data::text
            FROM call_record
            WHERE id = #{id}
              AND call_time = #{callTime}
            LIMIT 1
            """)
    String selectCacheResponseData(@Param("id") Long id, @Param("callTime") LocalDateTime callTime);
}
