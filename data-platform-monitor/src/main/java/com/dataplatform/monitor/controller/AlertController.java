package com.dataplatform.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.monitor.entity.AlertRule;
import com.dataplatform.monitor.entity.AlertRecord;
import com.dataplatform.monitor.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alert")
public class AlertController {

    @Autowired
    private AlertService alertService;

    // ==================== 告警规则 ====================

    @GetMapping("/rule/list")
    public PageResult<AlertRule> listRules(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return alertService.listRules(keyword, status, page, pageSize);
    }

    @GetMapping("/rule/{id}")
    public Result<AlertRule> getRuleById(@PathVariable Long id) {
        AlertRule rule = alertService.getRuleById(id);
        if (rule == null) {
            return Result.fail(404, "告警规则不存在");
        }
        return Result.success(rule);
    }

    @PostMapping("/rule")
    public Result<AlertRule> createRule(@RequestBody AlertRule rule) {
        rule.setId(null);
        rule.setStatus("active");
        alertService.saveRule(rule);
        return Result.success(rule);
    }

    @PutMapping("/rule/{id}")
    public Result<AlertRule> updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        rule.setId(id);
        alertService.updateRule(rule);
        return Result.success(alertService.getRuleById(id));
    }

    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return Result.success(null);
    }

    // ==================== 告警记录 ====================

    @GetMapping("/record/list")
    public PageResult<AlertRecord> listRecords(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return alertService.listRecords(status, level, page, pageSize);
    }

    @PostMapping("/record/{id}/resolve")
    public Result<Void> resolveRecord(@PathVariable Long id, @RequestBody Map<String, String> body) {
        alertService.resolveRecord(id, body.get("resolution"));
        return Result.success(null);
    }
}