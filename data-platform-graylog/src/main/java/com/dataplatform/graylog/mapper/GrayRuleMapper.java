package com.dataplatform.graylog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.graylog.entity.GrayRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GrayRuleMapper extends BaseMapper<GrayRule> {
}
