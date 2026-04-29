package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.billing.entity.TenantBudget;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantBudgetMapper extends BaseMapper<TenantBudget> {
}