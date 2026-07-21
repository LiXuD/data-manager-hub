package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.BillingRuleTier;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计费规则阶梯数据访问接口。
 */
@Mapper
public interface BillingRuleTierMapper extends BaseMapper<BillingRuleTier> {
}
