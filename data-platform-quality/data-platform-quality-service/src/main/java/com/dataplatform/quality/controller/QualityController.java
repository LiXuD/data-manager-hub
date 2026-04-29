package com.dataplatform.quality.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.quality.entity.QualityRule;
import com.dataplatform.quality.entity.QualityScore;
import com.dataplatform.quality.service.QualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quality")
public class QualityController {

    @Autowired
    private QualityService qualityService;

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

        qualityService.addRule(rule);
        return ResponseEntity.ok(Result.success(rule));
    }

    @GetMapping("/rules")
    public ResponseEntity<Result<List<QualityRule>>> getRules(@RequestParam(required = false) String dataType) {
        List<QualityRule> rules = qualityService.getActiveRules(dataType);
        return ResponseEntity.ok(Result.success(rules));
    }

    @PostMapping("/check")
    public ResponseEntity<Result<QualityScore>> checkQuality(
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) Long dataId) {
        // 参数验证
        if (dataType == null || dataType.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "dataType不能为空"));
        }
        if (dataId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "dataId不能为空"));
        }

        QualityScore score = qualityService.checkQuality(dataType, dataId);
        return ResponseEntity.ok(Result.success(score));
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