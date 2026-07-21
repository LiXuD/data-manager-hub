package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.BillingPlan;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BillingPlanMapper extends BaseMapper<BillingPlan> {

    @Select("""
        SELECT * FROM billing_plan
        WHERE vendor_code = #{vendorCode}
          AND interface_code = #{interfaceCode}
          AND accounting_purpose = #{accountingPurpose}
          AND status IN ('PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW')
          AND effective_from <= #{callTime}
          AND (effective_to IS NULL OR effective_to > #{callTime})
        ORDER BY version DESC
        LIMIT 1
        """)
    BillingPlan selectEffective(@Param("vendorCode") String vendorCode,
                                @Param("interfaceCode") String interfaceCode,
                                @Param("accountingPurpose") String accountingPurpose,
                                @Param("callTime") LocalDateTime callTime);

    @Select("SELECT COALESCE(MAX(version), 0) FROM billing_plan WHERE plan_code = #{planCode}")
    Integer selectMaxVersion(@Param("planCode") String planCode);

    @Select("""
        SELECT * FROM billing_plan
        WHERE status IN ('PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW')
          AND effective_from <= #{at}
          AND (effective_to IS NULL OR effective_to > #{at})
          AND template_code IN ('PACKAGE_COUNT', 'FLAT_PERIOD')
        """)
    List<BillingPlan> selectRecurringPlans(@Param("at") LocalDateTime at);
}
