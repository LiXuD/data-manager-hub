package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.BillingEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BillingEventMapper extends BaseMapper<BillingEvent> {

    @Select("SELECT * FROM billing_event WHERE request_id = #{requestId} LIMIT 1")
    BillingEvent selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM billing_event WHERE original_event_id = #{eventId} AND event_type = 'REVERSAL' LIMIT 1")
    BillingEvent selectReversalByOriginalEventId(@Param("eventId") Long eventId);

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{requestId}, 0))) locked")
    Integer lockRequest(@Param("requestId") String requestId);
}
