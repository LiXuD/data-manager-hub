package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.CallRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecord> {
    
    /**
     * 按数据类型统计指定日期的调用次数
     */
    @Select("SELECT tenant_id, data_type_code, COUNT(*) as call_count " +
            "FROM call_record " +
            "WHERE DATE(call_time) = #{billingDate} " +
            "AND deleted = false " +
            "GROUP BY tenant_id, data_type_code")
    List<Map<String, Object>> selectDailyStatistics(@Param("billingDate") LocalDate billingDate);
    
    /**
     * 按租户、数据类型和日期统计调用次数
     */
    @Select("SELECT tenant_id, data_type_code, COUNT(*) as call_count " +
            "FROM call_record " +
            "WHERE tenant_id = #{tenantId} " +
            "AND DATE(call_time) = #{billingDate} " +
            "AND deleted = false " +
            "GROUP BY tenant_id, data_type_code")
    List<Map<String, Object>> selectStatisticsByTenantAndDate(
            @Param("tenantId") Long tenantId, 
            @Param("billingDate") LocalDate billingDate);
}