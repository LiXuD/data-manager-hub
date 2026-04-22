package com.dataplatform.trace.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.trace.entity.DataLineage;
import com.dataplatform.trace.service.DataLineageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trace/lineage")
public class DataLineageController {

    @Autowired
    private DataLineageService dataLineageService;

    @PostMapping
    public Result<Boolean> recordLineage(@RequestParam String sourceType,
                                         @RequestParam Long sourceId,
                                         @RequestParam String sourceName,
                                         @RequestParam String targetType,
                                         @RequestParam Long targetId,
                                         @RequestParam String targetName,
                                         @RequestParam(required = false) String relationType,
                                         @RequestParam(required = false) String transformRule) {
        boolean result = dataLineageService.recordLineage(
            sourceType, sourceId, sourceName,
            targetType, targetId, targetName,
            relationType, transformRule
        );
        return Result.success(result);
    }

    @GetMapping("/upstream")
    public Result<List<DataLineage>> getUpstream(@RequestParam String type, @RequestParam Long id) {
        List<DataLineage> result = dataLineageService.getUpstream(type, id);
        return Result.success(result);
    }

    @GetMapping("/downstream")
    public Result<List<DataLineage>> getDownstream(@RequestParam String type, @RequestParam Long id) {
        List<DataLineage> result = dataLineageService.getDownstream(type, id);
        return Result.success(result);
    }

    @GetMapping("/full")
    public Result<List<DataLineage>> getFullLineage(@RequestParam String type, @RequestParam Long id) {
        List<DataLineage> result = dataLineageService.getFullLineage(type, id);
        return Result.success(result);
    }
}