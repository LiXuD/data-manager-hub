package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.BillingReconciliation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计费域计费计算的 Billing Reconciliation Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface BillingReconciliationMapper extends BaseMapper<BillingReconciliation> {
}