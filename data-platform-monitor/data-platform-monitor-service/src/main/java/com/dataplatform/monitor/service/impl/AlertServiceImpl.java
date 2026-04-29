package com.dataplatform.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.monitor.entity.AlertRule;
import com.dataplatform.monitor.entity.AlertRecord;
import com.dataplatform.monitor.mapper.AlertRecordMapper;
import com.dataplatform.monitor.mapper.AlertRuleMapper;
import com.dataplatform.monitor.service.AlertService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule>
    implements AlertService {

    private final AlertRecordMapper alertRecordMapper;

    public AlertServiceImpl(AlertRecordMapper alertRecordMapper) {
        this.alertRecordMapper = alertRecordMapper;
    }

    @Override
    public AlertRule getRuleById(Long id) {
        return getById(id);
    }

    @Override
    public AlertRecord getRecordById(Long id) {
        return alertRecordMapper.selectById(id);
    }

    @Override
    public PageResult<AlertRule> listRules(String keyword, String status, int page, int pageSize) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AlertRule::getRuleName, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AlertRule::getStatus, status);
        }
        wrapper.orderByDesc(AlertRule::getCreatedAt);

        Page<AlertRule> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<AlertRule> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    @Override
    public void saveRule(AlertRule rule) {
        save(rule);
    }

    @Override
    public void updateRule(AlertRule rule) {
        updateById(rule);
    }

    @Override
    public void deleteRule(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<AlertRecord> listRecords(String status, String level, int page, int pageSize) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(AlertRecord::getStatus, status);
        }
        if (StringUtils.hasText(level)) {
            wrapper.eq(AlertRecord::getLevel, level);
        }
        wrapper.orderByDesc(AlertRecord::getAlertTime);

        Page<AlertRecord> result = alertRecordMapper.selectPage(new Page<>(page, pageSize), wrapper);

        PageResult<AlertRecord> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    @Override
    public void resolveRecord(Long id, String resolution) {
        AlertRecord record = new AlertRecord();
        record.setId(id);
        record.setStatus("resolved");
        record.setResolvedTime(LocalDateTime.now());
        alertRecordMapper.updateById(record);
    }
}