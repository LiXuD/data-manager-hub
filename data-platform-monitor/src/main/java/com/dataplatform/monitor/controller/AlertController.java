package com.dataplatform.monitor.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.monitor.entity.AlertRule;
import com.dataplatform.monitor.service.AlertService;
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/monitor/alert")
public class AlertController {
    
    @Autowired
    private AlertService alertService;
    
    @GetMapping("/rule/list")
    public Result<List<AlertRule>> listRules() {
        return Result.success(alertService.listRules());
    }
    
    @GetMapping("/rule/{id}")
    public Result<AlertRule> getRuleById(@PathVariable Long id) {
        return Result.success(alertService.getRuleById(id));
    }
}