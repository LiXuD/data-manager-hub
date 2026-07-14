package com.dataplatform.access.call.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CallStatsMapper {

    @Select("""
        SELECT COUNT(*) AS total_calls,
               COALESCE(SUM(CASE WHEN success = true THEN 1 ELSE 0 END), 0) AS success_calls,
               COALESCE(AVG(latency), 0) AS avg_latency,
               COALESCE(SUM(CASE WHEN latency > #{slaThreshold} THEN 1 ELSE 0 END), 0) AS slow_calls
        FROM call_record
        WHERE api_code = #{apiCode}
          AND call_time BETWEEN #{startTime} AND #{endTime}
        """)
    Map<String, Object> selectInterfaceStats(
            @Param("apiCode") String apiCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("slaThreshold") Integer slaThreshold);

    @Select("""
        SELECT DATE(call_time) AS date,
               COUNT(*) AS total_calls,
               COALESCE(SUM(CASE WHEN success = true THEN 1 ELSE 0 END), 0) AS success_calls,
               COALESCE(AVG(latency), 0) AS avg_latency
        FROM call_record
        WHERE api_code = #{apiCode}
          AND call_time BETWEEN #{startTime} AND #{endTime}
        GROUP BY DATE(call_time)
        ORDER BY DATE(call_time) DESC
        """)
    List<Map<String, Object>> selectDailyInterfaceStats(
            @Param("apiCode") String apiCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
        SELECT COUNT(*) AS call_count, COALESCE(SUM(cost), 0) AS total_amount
        FROM call_record
        WHERE vendor_id = #{vendorId}
          AND DATE(call_time) = #{billingDate}
        """)
    Map<String, Object> selectVendorDailySummary(
            @Param("vendorId") Long vendorId,
            @Param("billingDate") LocalDate billingDate);
}
