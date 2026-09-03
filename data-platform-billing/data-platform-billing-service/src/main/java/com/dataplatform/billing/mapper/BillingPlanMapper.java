package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.BillingPlan;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
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
        """)
    List<BillingPlan> selectEffective(@Param("vendorCode") String vendorCode,
                                      @Param("interfaceCode") String interfaceCode,
                                      @Param("accountingPurpose") String accountingPurpose,
                                      @Param("callTime") LocalDateTime callTime);

    @Select("SELECT COALESCE(MAX(version), 0) FROM billing_plan WHERE plan_code = #{planCode}")
    Integer selectMaxVersion(@Param("planCode") String planCode);

    @Select("SELECT * FROM billing_plan WHERE id = #{id} FOR UPDATE")
    BillingPlan selectByIdForUpdate(@Param("id") Long id);

    /**
     * Creates and locks the business-key row used to serialize publishes. The
     * row exists independently of plans, so two new plans cannot both pass an
     * empty-table overlap check at the same time.
     */
    @Insert("""
        INSERT INTO billing_plan_publish_lock(vendor_id, interface_id, accounting_purpose, created_at)
        VALUES (#{vendorId}, #{interfaceId}, #{accountingPurpose}, CURRENT_TIMESTAMP)
        ON CONFLICT (vendor_id, interface_id, accounting_purpose) DO NOTHING
        """)
    int ensurePublishLock(@Param("vendorId") Long vendorId,
                          @Param("interfaceId") Long interfaceId,
                          @Param("accountingPurpose") String accountingPurpose);

    @Select("""
        SELECT vendor_id
        FROM billing_plan_publish_lock
        WHERE vendor_id = #{vendorId}
          AND interface_id = #{interfaceId}
          AND accounting_purpose = #{accountingPurpose}
        FOR UPDATE
        """)
    Long lockPublishKey(@Param("vendorId") Long vendorId,
                        @Param("interfaceId") Long interfaceId,
                        @Param("accountingPurpose") String accountingPurpose);

    @Select("""
        SELECT * FROM billing_plan
        WHERE vendor_id = #{vendorId}
          AND interface_id = #{interfaceId}
          AND accounting_purpose = #{accountingPurpose}
          AND status IN ('PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW')
        ORDER BY effective_from ASC, version ASC
        FOR UPDATE
        """)
    List<BillingPlan> selectPublishableForUpdate(@Param("vendorId") Long vendorId,
                                                 @Param("interfaceId") Long interfaceId,
                                                 @Param("accountingPurpose") String accountingPurpose);

    @Select("""
        SELECT * FROM billing_plan
        WHERE vendor_id = #{vendorId}
          AND interface_id = #{interfaceId}
          AND accounting_purpose = #{accountingPurpose}
          AND status IN ('PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW')
        ORDER BY effective_from ASC, version ASC
        """)
    List<BillingPlan> selectPublishable(@Param("vendorId") Long vendorId,
                                        @Param("interfaceId") Long interfaceId,
                                        @Param("accountingPurpose") String accountingPurpose);

    @Select("""
        SELECT * FROM billing_plan
        WHERE status IN ('PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW')
          AND effective_from <= #{at}
          AND (effective_to IS NULL OR effective_to > #{at})
          AND template_code IN ('PACKAGE_COUNT', 'FLAT_PERIOD')
        """)
    List<BillingPlan> selectRecurringPlans(@Param("at") LocalDateTime at);
}
