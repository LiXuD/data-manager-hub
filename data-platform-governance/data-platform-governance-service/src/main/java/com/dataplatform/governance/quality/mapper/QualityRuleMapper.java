package com.dataplatform.governance.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.governance.quality.entity.QualityRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 观测治理域数据质量的 Quality Rule Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface QualityRuleMapper extends BaseMapper<QualityRule> {
}