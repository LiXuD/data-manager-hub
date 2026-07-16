package com.dataplatform.governance.quality.controller;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.governance.quality.entity.QualityRule;
import com.dataplatform.governance.quality.entity.QualityScore;
import com.dataplatform.governance.quality.service.QualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 观测治理域数据质量的 Quality Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/quality")
public class QualityController {

    @Autowired
    private QualityService qualityService;

    @OperationLog(module = "数据质量管理", operation = "新增质量规则")
    @PostMapping("/rules")
    public ResponseEntity<Result<QualityRule>> addRule(@RequestBody QualityRule rule) {
        // 参数验证
        if (rule.getRuleName() == null || rule.getRuleName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "规则名称不能为空"));
        }
        if (rule.getRuleType() == null || rule.getRuleType().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "规则类型不能为空"));
        }
        if (rule.getDataType() == null || rule.getDataType().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "数据类型不能为空"));
        }

        try {
            qualityService.addRule(rule);
            return ResponseEntity.ok(Result.success(rule));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(400, e.getMessage()));
        }
    }

    @GetMapping("/rules")
    public ResponseEntity<Result<List<QualityRule>>> getRules(@RequestParam(required = false) String dataType) {
        List<QualityRule> rules = qualityService.getActiveRules(dataType);
        return ResponseEntity.ok(Result.success(rules));
    }

    @OperationLog(module = "数据质量管理", operation = "质量检查")
    @PostMapping("/check")
    public ResponseEntity<Result<QualityScore>> checkQuality(
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) Long dataId,
            @RequestBody(required = false) Map<String, Object> data) {
        // 参数验证
        if (dataType == null || dataType.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "dataType不能为空"));
        }
        if (dataId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "dataId不能为空"));
        }
        if (data == null || data.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "待检查数据不能为空"));
        }

        try {
            QualityScore score = qualityService.checkQuality(dataType, dataId, data);
            return ResponseEntity.ok(Result.success(score));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(400, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Result.error(422, e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<Result<List<QualityScore>>> getScoreHistory(
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) Long dataId) {
        if (dataType == null || dataType.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "dataType不能为空"));
        }
        if (dataId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "dataId不能为空"));
        }

        List<QualityScore> history = qualityService.getScoreHistory(dataType, dataId);
        return ResponseEntity.ok(Result.success(history));
    }
}
