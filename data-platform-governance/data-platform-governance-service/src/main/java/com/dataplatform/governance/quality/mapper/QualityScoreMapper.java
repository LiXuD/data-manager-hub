package com.dataplatform.governance.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.governance.quality.entity.QualityScore;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QualityScoreMapper extends BaseMapper<QualityScore> {
}