package com.dataplatform.interface_.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface InterfaceStatsMapper {

    @Select("""
        SELECT dt.data_type_code FROM data_type dt
        JOIN api_interface ai ON dt.id = ai.data_type_id
        WHERE ai.id = #{interfaceId}
        """)
    String getDataTypeCodeByInterfaceId(@Param("interfaceId") Long interfaceId);

    @Select("""
        SELECT
            COUNT(*) as total_calls,
            SUM(CASE WHEN cr.success = true THEN 1 ELSE 0 END) as success_calls,
            AVG(cr.latency) as avg_latency,
            SUM(CASE WHEN cr.latency > #{slaThreshold} THEN 1 ELSE 0 END) as slow_calls
        FROM call_record cr
        JOIN api_interface ai ON cr.data_type = (SELECT dt.data_type_code FROM data_type dt WHERE dt.id = ai.data_type_id)
        WHERE ai.id = #{interfaceId}
          AND cr.call_time >= #{startTime}
          AND cr.call_time <= #{endTime}
        """)
    Map<String, Object> getStatsByInterfaceId(
        @Param("interfaceId") Long interfaceId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("slaThreshold") int slaThreshold
    );

    @Select("""
        SELECT
            DATE(cr.call_time) as date,
            COUNT(*) as total_calls,
            SUM(CASE WHEN cr.success = true THEN 1 ELSE 0 END) as success_calls,
            AVG(cr.latency) as avg_latency
        FROM call_record cr
        WHERE cr.data_type = (
            SELECT dt.data_type_code FROM data_type dt
            JOIN api_interface ai ON dt.id = ai.data_type_id
            WHERE ai.id = #{interfaceId}
        )
          AND cr.call_time >= #{startTime}
          AND cr.call_time <= #{endTime}
        GROUP BY DATE(cr.call_time)
        ORDER BY DATE(cr.call_time) DESC
        """)
    List<Map<String, Object>> getDailyStatsByInterfaceId(
        @Param("interfaceId") Long interfaceId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}
