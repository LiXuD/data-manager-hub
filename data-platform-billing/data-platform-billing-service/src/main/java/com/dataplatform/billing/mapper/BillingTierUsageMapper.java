package com.dataplatform.billing.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 阶梯计费周期用量数据访问接口。
 */
@Mapper
public interface BillingTierUsageMapper {

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{lockKey}, 0))) locked")
    Integer lockUsage(@Param("lockKey") String lockKey);

    @Select("""
        SELECT usage_before
        FROM billing_tier_usage_event
        WHERE request_id = #{requestId}
        """)
    Long selectUsageBeforeByRequestId(@Param("requestId") String requestId);

    @Select("""
        SELECT call_count
        FROM billing_tier_usage
        WHERE rule_id = #{ruleId} AND billing_period = #{billingPeriod}
        """)
    Long selectCurrentUsage(@Param("ruleId") Long ruleId,
                            @Param("billingPeriod") LocalDate billingPeriod);

    @Insert("""
        INSERT INTO billing_tier_usage (rule_id, billing_period, call_count, updated_at)
        VALUES (#{ruleId}, #{billingPeriod}, #{callCount}, CURRENT_TIMESTAMP)
        ON CONFLICT (rule_id, billing_period)
        DO UPDATE SET
            call_count = billing_tier_usage.call_count + EXCLUDED.call_count,
            updated_at = CURRENT_TIMESTAMP
        """)
    int incrementUsage(@Param("ruleId") Long ruleId,
                       @Param("billingPeriod") LocalDate billingPeriod,
                       @Param("callCount") long callCount);

    @Insert("""
        INSERT INTO billing_tier_usage_event (
            request_id, rule_id, billing_period, usage_before, call_count, created_at
        ) VALUES (
            #{requestId}, #{ruleId}, #{billingPeriod}, #{usageBefore}, #{callCount}, CURRENT_TIMESTAMP
        )
        """)
    int insertUsageEvent(@Param("requestId") String requestId,
                         @Param("ruleId") Long ruleId,
                         @Param("billingPeriod") LocalDate billingPeriod,
                         @Param("usageBefore") long usageBefore,
                         @Param("callCount") long callCount);
}
