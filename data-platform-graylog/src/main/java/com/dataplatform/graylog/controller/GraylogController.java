package com.dataplatform.graylog.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.graylog.entity.GrayRule;
import com.dataplatform.graylog.service.GraylogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/graylog")
public class GraylogController {
    @Autowired
    private GraylogService graylogService;

    @GetMapping("/list")
    public PageResult<GrayRule> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return graylogService.list(keyword, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<GrayRule> get(@PathVariable Long id) {
        GrayRule rule = graylogService.getById(id);
        if (rule == null) {
            return Result.fail(404, "灰度规则不存在");
        }
        return Result.success(rule);
    }

    @PostMapping
    public Result<GrayRule> create(@RequestBody GrayRule rule) {
        rule.setId(null);
        rule.setStatus("active");
        graylogService.save(rule);
        return Result.success(rule);
    }

    @PutMapping("/{id}")
    public Result<GrayRule> update(@PathVariable Long id, @RequestBody GrayRule rule) {
        rule.setId(id);
        graylogService.updateById(rule);
        return Result.success(graylogService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        graylogService.removeById(id);
        return Result.success(null);
    }

    @GetMapping("/active/{serviceName}")
    public Result<GrayRule> getActiveRule(@PathVariable String serviceName) {
        return Result.success(graylogService.getActiveRule(serviceName));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        GrayRule rule = new GrayRule();
        rule.setId(id);
        rule.setStatus(status);
        graylogService.updateById(rule);
        return Result.success(null);
    }
}