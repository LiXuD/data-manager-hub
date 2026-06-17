package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.BillingRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计费域计费计算的 Billing Rule Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface BillingRuleMapper extends BaseMapper<BillingRule> {
}