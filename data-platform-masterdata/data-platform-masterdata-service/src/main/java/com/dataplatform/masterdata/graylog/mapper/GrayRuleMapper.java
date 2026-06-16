package com.dataplatform.masterdata.graylog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.graylog.entity.GrayRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GrayRuleMapper extends BaseMapper<GrayRule> {
}
