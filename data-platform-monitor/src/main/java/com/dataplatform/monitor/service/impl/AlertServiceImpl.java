package com.dataplatform.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.monitor.entity.AlertRule;
import com.dataplatform.monitor.entity.AlertRecord;
import com.dataplatform.monitor.mapper.AlertRecordMapper;
import com.dataplatform.monitor.mapper.AlertRuleMapper;
import com.dataplatform.monitor.service.AlertService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule> 
    implements AlertService {
    
    private final AlertRecordMapper alertRecordMapper;

    public AlertServiceImpl(AlertRecordMapper alertRecordMapper) {
        this.alertRecordMapper = alertRecordMapper;
    }

    @Override
    public List<AlertRule> listRules() {
        return list();
    }

    @Override
    public AlertRule getRuleById(Long id) {
        return getById(id);
    }
    public List<AlertRule> listActiveRules() {
        return list(new LambdaQueryWrapper<AlertRule>()
            .eq(AlertRule::getStatus, "active")
            .orderByAsc(AlertRule::getId));
    }

    @Override
    public AlertRule createRule(AlertRule rule) {
        rule.setStatus("active");
        save(rule);
        return rule;
    }

    @Override
    public boolean triggerAlert(Long ruleId, String content) {
        AlertRule rule = getById(ruleId);
        if (rule == null) return false;
        
        AlertRecord record = new AlertRecord();
        record.setRuleId(ruleId);
        record.setRuleName(rule.getRuleName());
        record.setVendorId(rule.getVendorId());
        record.setMessage(rule.getRuleType());
        record.setLevel(rule.getLevel());
        record.setMessage(content);
        record.setStatus("triggered");
        record.setTriggerTime(LocalDateTime.now());
        
        return alertRecordMapper.insert(record) > 0;
    }

    @Override
    public boolean resolveAlert(Long recordId) {
        AlertRecord record = new AlertRecord();
        record.setId(recordId);
        record.setStatus("resolved");
        record.setResolvedTime(LocalDateTime.now());
        return alertRecordMapper.updateById(record) > 0;
    }

    @Override
    public Page<AlertRecord> listRecords(Long vendorId, String status, Integer page, Integer pageSize) {
        Page<AlertRecord> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        
        if (vendorId != null) {
            wrapper.eq(AlertRecord::getVendorId, vendorId);
        }
        if (status != null) {
            wrapper.eq(AlertRecord::getStatus, status);
        }
        
        wrapper.orderByDesc(AlertRecord::getTriggerTime);
        return alertRecordMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public long countUnresolved() {
        return alertRecordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
            .eq(AlertRecord::getStatus, "triggered"));
    }
}
