package com.dataplatform.governance.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.enums.AlertStatus;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.governance.monitor.entity.AlertRule;
import com.dataplatform.governance.monitor.entity.AlertRecord;
import com.dataplatform.governance.monitor.mapper.AlertRecordMapper;
import com.dataplatform.governance.monitor.mapper.AlertRuleMapper;
import com.dataplatform.governance.monitor.service.AlertService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 观测治理域监控告警的 Alert Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
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
    public void saveRecord(AlertRecord record) {
        if (record.getRuleId() == null) {
            record.setRuleId(ensureSystemRuleId());
        }
        if (record.getStatus() == null) {
            record.setStatus("pending");
        }
        if (record.getAlertTime() == null) {
            record.setAlertTime(LocalDateTime.now());
        }
        alertRecordMapper.insert(record);
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
        response.setCode(200);
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
        response.setCode(200);
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
        record.setResolvedAt(LocalDateTime.now());
        record.setResolvedBy(UserContext.getCurrentUserId());
        record.setResolution(resolution);
        alertRecordMapper.updateById(record);
    }

    private Long ensureSystemRuleId() {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getRuleName, "计费对账差异告警");
        wrapper.last("LIMIT 1");
        AlertRule existing = getOne(wrapper);
        if (existing != null) {
            return existing.getId();
        }

        AlertRule rule = new AlertRule();
        rule.setRuleName("计费对账差异告警");
        rule.setRuleType("RECONCILIATION");
        rule.setTargetType("billing_reconciliation");
        rule.setConditionType("diff_rate_gt");
        rule.setThresholdValue(new BigDecimal("0.01"));
        rule.setStatus(AlertStatus.ACTIVE);
        rule.setSeverity("warning");
        rule.setCreatedAt(LocalDateTime.now());
        save(rule);
        return rule.getId();
    }
}
