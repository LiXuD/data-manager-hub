package com.dataplatform.monitor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.monitor.entity.AlertRule;
import com.dataplatform.monitor.entity.AlertRecord;

import java.util.List;

public interface AlertService extends IService<AlertRule> {

    // 告警规则
    PageResult<AlertRule> listRules(String keyword, String status, int page, int pageSize);

    AlertRule getRuleById(Long id);

    void saveRule(AlertRule rule);

    void updateRule(AlertRule rule);

    void deleteRule(Long id);

    // 告警记录
    PageResult<AlertRecord> listRecords(String status, String level, int page, int pageSize);

    void resolveRecord(Long id, String resolution);
}