package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.BillingDaily;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 计费域计费计算的 Billing Daily Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface BillingDailyMapper extends BaseMapper<BillingDaily> {
    @Insert("""
        WITH new_event AS (
            INSERT INTO billing_daily_event (request_id, created_at)
            VALUES (#{requestId}, CURRENT_TIMESTAMP)
            ON CONFLICT (request_id) DO NOTHING
            RETURNING request_id
        )
        INSERT INTO billing_daily (
            tenant_id, caller_id, vendor_id, data_type, call_count,
            success_count, fail_count, total_cost, avg_latency, billing_date,
            created_at, updated_at
        )
        SELECT
            #{tenantId}, #{callerId}, #{vendorId}, #{dataType}, 1,
            #{successCount}, #{failCount}, #{totalCost}, #{latency}, #{billingDate},
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        FROM new_event
        ON CONFLICT (tenant_id, caller_id, vendor_id, data_type, billing_date)
        DO UPDATE SET
            call_count = billing_daily.call_count + 1,
            success_count = billing_daily.success_count + EXCLUDED.success_count,
            fail_count = billing_daily.fail_count + EXCLUDED.fail_count,
            total_cost = billing_daily.total_cost + EXCLUDED.total_cost,
            avg_latency = CASE
                WHEN billing_daily.avg_latency IS NULL THEN EXCLUDED.avg_latency
                WHEN EXCLUDED.avg_latency IS NULL THEN billing_daily.avg_latency
                ELSE ((billing_daily.avg_latency * billing_daily.call_count + EXCLUDED.avg_latency)
                    / (billing_daily.call_count + 1))
            END,
            updated_at = CURRENT_TIMESTAMP
        """)
    int upsertDailyFromCallRecord(@Param("requestId") String requestId,
                                  @Param("tenantId") Long tenantId,
                                  @Param("callerId") Long callerId,
                                  @Param("vendorId") Long vendorId,
                                  @Param("dataType") String dataType,
                                  @Param("billingDate") LocalDate billingDate,
                                  @Param("successCount") Long successCount,
                                  @Param("failCount") Long failCount,
                                  @Param("totalCost") BigDecimal totalCost,
                                  @Param("latency") Integer latency);
}
