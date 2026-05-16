package com.dataplatform.identity.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.util.DataMaskingUtil;
import com.dataplatform.identity.tenant.entity.MaskingRule;
import com.dataplatform.identity.tenant.mapper.MaskingRuleMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MaskingService extends ServiceImpl<MaskingRuleMapper, MaskingRule> {

    public Map<String, String> getFieldTypeMap(Long tenantId, String tableName) {
        LambdaQueryWrapper<MaskingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaskingRule::getTenantId, tenantId);
        wrapper.eq(MaskingRule::getTableName, tableName);
        wrapper.eq(MaskingRule::getEnabled, true);

        List<MaskingRule> rules = this.list(wrapper);

        return rules.stream()
            .collect(Collectors.toMap(
                MaskingRule::getFieldName,
                MaskingRule::getFieldType,
                (v1, v2) -> v1
            ));
    }

    public Map<String, Object> maskData(Long tenantId, String tableName, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        Map<String, String> fieldTypes = getFieldTypeMap(tenantId, tableName);
        if (fieldTypes.isEmpty()) {
            return data;
        }

        return DataMaskingUtil.maskMap(data, fieldTypes);
    }

    public List<Map<String, Object>> maskDataList(Long tenantId, String tableName,
                                                   List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return dataList;
        }

        return dataList.stream()
            .map(data -> maskData(tenantId, tableName, data))
            .collect(Collectors.toList());
    }

    public boolean saveRule(MaskingRule rule) {
        LambdaQueryWrapper<MaskingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaskingRule::getTenantId, rule.getTenantId());
        wrapper.eq(MaskingRule::getTableName, rule.getTableName());
        wrapper.eq(MaskingRule::getFieldName, rule.getFieldName());

        MaskingRule existing = this.getOne(wrapper);
        if (existing != null) {
            rule.setId(existing.getId());
            return this.updateById(rule);
        }
        return this.save(rule);
    }

    public boolean deleteRule(Long id) {
        return this.removeById(id);
    }

    public List<MaskingRule> getRules(Long tenantId, String tableName) {
        LambdaQueryWrapper<MaskingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaskingRule::getTenantId, tenantId);
        if (tableName != null) {
            wrapper.eq(MaskingRule::getTableName, tableName);
        }
        return this.list(wrapper);
    }

    public String maskForLog(String content) {
        return DataMaskingUtil.maskInLog(content);
    }
}