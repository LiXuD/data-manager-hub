package com.dataplatform.masterdata.graylog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.graylog.entity.GrayRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 主数据域灰度规则的 Gray Rule Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface GrayRuleMapper extends BaseMapper<GrayRule> {
}
