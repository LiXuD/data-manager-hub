package com.dataplatform.governance.monitor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.governance.monitor.entity.AlertRule;
import com.dataplatform.governance.monitor.entity.AlertRecord;

import java.util.List;

/**
 * 观测治理域监控告警的 Alert Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
public interface AlertService extends IService<AlertRule> {

    // 告警规则
    PageResult<AlertRule> listRules(String keyword, String status, int page, int pageSize);

    AlertRule getRuleById(Long id);

    void saveRule(AlertRule rule);

    void updateRule(AlertRule rule);

    void deleteRule(Long id);

    // 告警记录
    PageResult<AlertRecord> listRecords(String status, String level, int page, int pageSize);

    AlertRecord getRecordById(Long id);

    void saveRecord(AlertRecord record);

    void resolveRecord(Long id, String resolution);
}
