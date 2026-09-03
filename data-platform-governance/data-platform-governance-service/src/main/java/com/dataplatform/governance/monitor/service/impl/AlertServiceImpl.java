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
        if (id == null) {
            return null;
        }
        if (isPlatformAdmin()) {
            return getById(id);
        }
        Long tenantId = currentTenantId();
        if (tenantId == null) {
            return null;
        }
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getId, id).eq(AlertRule::getTenantId, tenantId);
        return getOne(wrapper);
    }

    @Override
    public AlertRecord getRecordById(Long id) {
        if (id == null) {
            return null;
        }
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getId, id);
        if (!isPlatformAdmin()) {
            Long tenantId = currentTenantId();
            if (tenantId == null) {
                return null;
            }
            wrapper.eq(AlertRecord::getTenantId, tenantId);
        }
        return alertRecordMapper.selectOne(wrapper);
    }

    @Override
    public void saveRecord(AlertRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("告警记录不能为空");
        }
        validateRecord(record);
        if (record.getRuleId() == null) {
            record.setRuleId(ensureSystemRuleId());
        }
        if (record.getStatus() == null) {
            record.setStatus("pending");
        }
        if (record.getAlertTime() == null) {
            record.setAlertTime(LocalDateTime.now());
        }
        if (alertRecordMapper.insert(record) != 1) {
            throw new IllegalStateException("告警记录落库失败，请重试");
        }
    }

    private void validateRecord(AlertRecord record) {
        if (!hasText(record.getAlertType()) || record.getAlertType().length() > 50) {
            throw new IllegalArgumentException("告警类型不能为空且长度不能超过50");
        }
        if (!hasText(record.getAlertTitle()) || record.getAlertTitle().length() > 200) {
            throw new IllegalArgumentException("告警标题不能为空且长度不能超过200");
        }
        if (!hasText(record.getLevel()) || record.getLevel().length() > 20) {
            throw new IllegalArgumentException("告警级别不能为空且长度不能超过20");
        }
        if (record.getRuleId() != null && record.getRuleId() <= 0) {
            throw new IllegalArgumentException("告警规则ID无效");
        }
        if (record.getTenantId() != null && record.getTenantId() <= 0) {
            throw new IllegalArgumentException("租户ID无效");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
        applyRuleScope(wrapper);
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
        if (rule == null) {
            throw new IllegalArgumentException("告警规则不能为空");
        }
        applyCreateScope(rule);
        if (!save(rule)) {
            throw new IllegalStateException("告警规则新增失败，请重试");
        }
    }

    @Override
    public void updateRule(AlertRule rule) {
        if (rule == null || rule.getId() == null) {
            throw new IllegalArgumentException("告警规则ID不能为空");
        }
        AlertRule existing = getRuleById(rule.getId());
        if (existing == null) {
            throw new IllegalArgumentException("告警规则不存在");
        }
        rule.setTenantId(existing.getTenantId());
        rule.setCreatedBy(existing.getCreatedBy());
        rule.setCreatedAt(existing.getCreatedAt());
        rule.setDeleted(existing.getDeleted());
        if (!updateById(rule)) {
            throw new IllegalStateException("告警规则更新失败，请重试");
        }
    }

    @Override
    public void deleteRule(Long id) {
        if (id == null || getRuleById(id) == null) {
            throw new IllegalArgumentException("告警规则不存在");
        }
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getId, id);
        if (!isPlatformAdmin()) {
            wrapper.eq(AlertRule::getTenantId, currentTenantId());
        }
        if (!remove(wrapper)) {
            throw new IllegalStateException("告警规则删除失败，请重试");
        }
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
        if (!isPlatformAdmin()) {
            Long tenantId = currentTenantId();
            if (tenantId == null) {
                return emptyRecords(page, pageSize);
            }
            wrapper.eq(AlertRecord::getTenantId, tenantId);
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
        if (id == null || getRecordById(id) == null) {
            throw new IllegalArgumentException("告警记录不存在");
        }
        AlertRecord update = new AlertRecord();
        update.setStatus("resolved");
        update.setResolvedAt(LocalDateTime.now());
        update.setResolvedBy(UserContext.getCurrentUserId());
        update.setResolution(resolution);
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getId, id);
        if (!isPlatformAdmin()) {
            wrapper.eq(AlertRecord::getTenantId, currentTenantId());
        }
        if (alertRecordMapper.update(update, wrapper) != 1) {
            throw new IllegalStateException("告警记录处理失败，请重试");
        }
    }

    private void applyRuleScope(LambdaQueryWrapper<AlertRule> wrapper) {
        if (!isPlatformAdmin()) {
            Long tenantId = currentTenantId();
            if (tenantId == null) {
                wrapper.eq(AlertRule::getTenantId, -1L);
            } else {
                wrapper.eq(AlertRule::getTenantId, tenantId);
            }
        }
    }

    private void applyCreateScope(AlertRule rule) {
        if (!isPlatformAdmin()) {
            Long tenantId = currentTenantId();
            if (tenantId == null) {
                throw new IllegalStateException("当前用户没有租户作用域");
            }
            rule.setTenantId(tenantId);
            rule.setCreatedBy(UserContext.getCurrentUserId());
        }
    }

    private boolean isPlatformAdmin() {
        try {
            return UserContext.hasPermission("system:admin");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Long currentTenantId() {
        try {
            return UserContext.getCurrentTenantId();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private PageResult<AlertRecord> emptyRecords(int page, int pageSize) {
        PageResult<AlertRecord> response = new PageResult<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(java.util.List.of());
        response.setTotal(0L);
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
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
        if (!save(rule)) {
            throw new IllegalStateException("系统告警规则初始化失败，请重试");
        }
        return rule.getId();
    }
}
