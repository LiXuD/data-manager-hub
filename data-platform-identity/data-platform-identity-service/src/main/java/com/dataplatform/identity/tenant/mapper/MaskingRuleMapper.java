package com.dataplatform.identity.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.identity.tenant.entity.MaskingRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 身份租户域租户的 Masking Rule Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface MaskingRuleMapper extends BaseMapper<MaskingRule> {
}