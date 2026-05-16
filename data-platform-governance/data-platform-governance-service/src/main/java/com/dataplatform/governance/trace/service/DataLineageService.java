package com.dataplatform.governance.trace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.governance.trace.entity.DataLineage;
import com.dataplatform.governance.trace.mapper.DataLineageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DataLineageService extends ServiceImpl<DataLineageMapper, DataLineage> {

    private static final int MAX_LINEAGE_DEPTH = 20;

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
        Set<String> visited = new HashSet<>();
        collectUpstream(type, id, result, visited, 0);
        return result;
    }

    private void collectUpstream(String type, Long id, List<DataLineage> result,
                                 Set<String> visited, int depth) {
        if (depth > MAX_LINEAGE_DEPTH) {
            return;
        }
        String key = type + ":" + id;
        if (visited.contains(key)) {
            return;
        }
        visited.add(key);

        List<DataLineage> upstreams = getUpstream(type, id);
        for (DataLineage lineage : upstreams) {
            result.add(lineage);
            collectUpstream(lineage.getSourceType(), lineage.getSourceId(),
                result, visited, depth + 1);
        }
    }
}