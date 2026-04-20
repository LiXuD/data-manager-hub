package com.dataplatform.graylog.controller;

import com.dataplatform.common.pojo.ApiResponse;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.graylog.entity.GrayRule;
import com.dataplatform.graylog.service.GraylogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/graylog")
public class GraylogController {
    @Autowired
    private GraylogService graylogService;

    @GetMapping("/list")
    public ApiResponse<PageResponse<GrayRule>> list(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<GrayRule> result = graylogService.list(serviceName, status, page, pageSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<GrayRule> get(@PathVariable Long id) {
        return ApiResponse.success(graylogService.getById(id));
    }

    @PostMapping
    public ApiResponse<GrayRule> create(@RequestBody GrayRule rule) {
        return ApiResponse.success(graylogService.create(rule));
    }

    @PutMapping("/{id}")
    public ApiResponse<GrayRule> update(@PathVariable Long id, @RequestBody GrayRule rule) {
        rule.setId(id);
        return ApiResponse.success(graylogService.update(rule));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        graylogService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/active/{serviceName}")
    public ApiResponse<GrayRule> getActiveRule(@PathVariable String serviceName) {
        return ApiResponse.success(graylogService.getActiveRule(serviceName));
    }
}
