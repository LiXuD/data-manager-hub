package com.dataplatform.trace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.trace.entity.DataLineage;
import com.dataplatform.trace.mapper.DataLineageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DataLineageService extends ServiceImpl<DataLineageMapper, DataLineage> {

    public boolean recordLineage(String sourceType, Long sourceId, String sourceName,
                                String targetType, Long targetId, String targetName,
                                String relationType, String transformRule) {
        DataLineage lineage = new DataLineage();
        lineage.setSourceType(sourceType);
        lineage.setSourceId(sourceId);
        lineage.setSourceName(sourceName);
        lineage.setTargetType(targetType);
        lineage.setTargetId(targetId);
        lineage.setTargetName(targetName);
        lineage.setRelationType(relationType);
        lineage.setTransformRule(transformRule);
        lineage.setCreatedAt(LocalDateTime.now());
        return this.save(lineage);
    }

    public List<DataLineage> getUpstream(String type, Long id) {
        LambdaQueryWrapper<DataLineage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataLineage::getTargetType, type)
               .eq(DataLineage::getTargetId, id);
        return this.list(wrapper);
    }

    public List<DataLineage> getDownstream(String type, Long id) {
        LambdaQueryWrapper<DataLineage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataLineage::getSourceType, type)
               .eq(DataLineage::getSourceId, id);
        return this.list(wrapper);
    }

    public List<DataLineage> getFullLineage(String type, Long id) {
        List<DataLineage> result = new ArrayList<>();
        collectUpstream(type, id, result);
        return result;
    }

    private void collectUpstream(String type, Long id, List<DataLineage> result) {
        List<DataLineage> upstreams = getUpstream(type, id);
        for (DataLineage lineage : upstreams) {
            if (!result.contains(lineage)) {
                result.add(lineage);
                collectUpstream(lineage.getSourceType(), lineage.getSourceId(), result);
            }
        }
    }
}