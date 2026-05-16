package com.dataplatform.identity.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.identity.tenant.entity.MaskingRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaskingRuleMapper extends BaseMapper<MaskingRule> {
}