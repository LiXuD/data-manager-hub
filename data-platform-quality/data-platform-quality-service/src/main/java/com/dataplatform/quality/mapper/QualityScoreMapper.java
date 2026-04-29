package com.dataplatform.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.quality.entity.QualityScore;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QualityScoreMapper extends BaseMapper<QualityScore> {
}