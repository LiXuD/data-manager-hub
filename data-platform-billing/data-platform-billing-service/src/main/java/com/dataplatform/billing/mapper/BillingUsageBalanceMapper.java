package com.dataplatform.billing.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BillingUsageBalanceMapper {

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{lockKey}, 0))) locked")
    Integer lockBalance(@Param("lockKey") String lockKey);

    @Select("""
        SELECT used_quantity FROM billing_usage_balance
        WHERE plan_id = #{planId} AND billing_period = #{period} AND scope_key = #{scopeKey}
        """)
    BigDecimal selectUsedQuantity(@Param("planId") Long planId,
                                  @Param("period") LocalDate period,
                                  @Param("scopeKey") String scopeKey);

    @Insert("""
        INSERT INTO billing_usage_balance(plan_id, billing_period, scope_key, used_quantity, updated_at)
        VALUES (#{planId}, #{period}, #{scopeKey}, #{quantity}, CURRENT_TIMESTAMP)
        ON CONFLICT (plan_id, billing_period, scope_key)
        DO UPDATE SET used_quantity = billing_usage_balance.used_quantity + EXCLUDED.used_quantity,
                      updated_at = CURRENT_TIMESTAMP
        """)
    int increment(@Param("planId") Long planId,
                  @Param("period") LocalDate period,
                  @Param("scopeKey") String scopeKey,
                  @Param("quantity") BigDecimal quantity);

    @Insert("""
        INSERT INTO billing_usage_balance(plan_id, billing_period, scope_key, used_quantity, updated_at)
        VALUES (#{planId}, #{period}, #{scopeKey}, 0, CURRENT_TIMESTAMP)
        ON CONFLICT (plan_id, billing_period, scope_key)
        DO UPDATE SET used_quantity = GREATEST(
                          billing_usage_balance.used_quantity - #{quantity}, 0),
                      updated_at = CURRENT_TIMESTAMP
        """)
    int decrement(@Param("planId") Long planId,
                  @Param("period") LocalDate period,
                  @Param("scopeKey") String scopeKey,
                  @Param("quantity") BigDecimal quantity);
}
