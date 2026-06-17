package com.dataplatform.governance.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.enums.AlertStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.governance.monitor.entity.AlertRule;
import com.dataplatform.governance.monitor.entity.AlertRecord;
import com.dataplatform.governance.monitor.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 观测治理域监控告警的 Alert Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
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
    public ResponseEntity<Result<AlertRule>> getRuleById(@PathVariable Long id) {
        AlertRule rule = alertService.getRuleById(id);
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "告警规则不存在"));
        }
        return ResponseEntity.ok(Result.success(rule));
    }

    @OperationLog(module = "告警规则管理", operation = "新增告警规则")
    @PostMapping("/rule")
    public ResponseEntity<Result<AlertRule>> createRule(@RequestBody AlertRule rule) {
        // 兼容测试用例格式: 使用 metric 作为 targetType, condition 作为 conditionType
        if (rule.getTargetType() == null && rule.getMetric() != null) {
            rule.setTargetType(rule.getMetric());
        }
        if (rule.getConditionType() == null && rule.getCondition() != null) {
            rule.setConditionType(rule.getCondition());
        }
        if (rule.getRuleType() == null) {
            rule.setRuleType("THRESHOLD");
        }
        if (rule.getThreshold() != null && rule.getThresholdValue() == null) {
            Object threshold = rule.getThreshold();
            if (threshold instanceof Number) {
                rule.setThresholdValue(new java.math.BigDecimal(threshold.toString()));
            }
        }

        // 校验必填字段
        if (rule.getRuleName() == null || rule.getRuleName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "规则名称不能为空"));
        }
        if (rule.getTargetType() == null || rule.getTargetType().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "目标类型不能为空"));
        }

        rule.setId(null);
        rule.setStatus(AlertStatus.ACTIVE);
        alertService.saveRule(rule);
        return ResponseEntity.ok(Result.success(rule));
    }

    @OperationLog(module = "告警规则管理", operation = "更新告警规则")
    @PutMapping("/rule/{id}")
    public ResponseEntity<Result<AlertRule>> updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        // 检查是否存在
        AlertRule existing = alertService.getRuleById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "告警规则不存在"));
        }
        rule.setId(id);
        alertService.updateRule(rule);
        return ResponseEntity.ok(Result.success(alertService.getRuleById(id)));
    }

    @OperationLog(module = "告警规则管理", operation = "删除告警规则")
    @DeleteMapping("/rule/{id}")
    public ResponseEntity<Result<Void>> deleteRule(@PathVariable Long id) {
        // 检查是否存在
        AlertRule existing = alertService.getRuleById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "告警规则不存在"));
        }
        alertService.deleteRule(id);
        return ResponseEntity.ok(Result.success(null));
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

    @OperationLog(module = "告警记录管理", operation = "处理告警")
    @PostMapping("/record/{id}/resolve")
    public ResponseEntity<Result<Void>> resolveRecord(@PathVariable Long id, @RequestBody Map<String, String> body) {
        // 检查是否存在
        AlertRecord record = alertService.getRecordById(id);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "告警记录不存在"));
        }

        String resolution = body.get("resolution");
        if (resolution == null || resolution.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "解决方案不能为空"));
        }
        alertService.resolveRecord(id, resolution);
        return ResponseEntity.ok(Result.success(null));
    }
}