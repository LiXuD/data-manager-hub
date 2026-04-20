package com.dataplatform.monitor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.monitor.entity.AlertRule;
import com.dataplatform.monitor.entity.AlertRecord;

import java.util.List;

public interface AlertService {
    
    // 告警规则
    List<AlertRule> listActiveRules();
    
    List<AlertRule> listRules();
    
    AlertRule getRuleById(Long id);
    
    AlertRule createRule(AlertRule rule);
    
    boolean triggerAlert(Long ruleId, String content);
    
    boolean resolveAlert(Long recordId);
    
    // 告警记录
    Page<AlertRecord> listRecords(Long vendorId, String status, Integer page, Integer pageSize);
    
    long countUnresolved();
}
