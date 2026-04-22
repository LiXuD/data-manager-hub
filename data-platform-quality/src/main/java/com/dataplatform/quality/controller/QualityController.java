package com.dataplatform.quality.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.quality.entity.QualityRule;
import com.dataplatform.quality.entity.QualityScore;
import com.dataplatform.quality.service.QualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quality")
public class QualityController {

    @Autowired
    private QualityService qualityService;

    @PostMapping("/rules")
    public Result<Boolean> addRule(@RequestBody QualityRule rule) {
        boolean result = qualityService.addRule(rule);
        return Result.success(result);
    }

    @GetMapping("/rules")
    public Result<List<QualityRule>> getRules(@RequestParam(required = false) String dataType) {
        List<QualityRule> rules = qualityService.getActiveRules(dataType);
        return Result.success(rules);
    }

    @PostMapping("/check")
    public Result<QualityScore> checkQuality(@RequestParam String dataType,
                                              @RequestParam Long dataId) {
        QualityScore score = qualityService.checkQuality(dataType, dataId);
        return Result.success(score);
    }

    @GetMapping("/history")
    public Result<List<QualityScore>> getScoreHistory(@RequestParam String dataType,
                                                       @RequestParam Long dataId) {
        List<QualityScore> history = qualityService.getScoreHistory(dataType, dataId);
        return Result.success(history);
    }
}