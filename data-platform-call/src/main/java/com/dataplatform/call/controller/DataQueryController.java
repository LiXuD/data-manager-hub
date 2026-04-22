package com.dataplatform.call.controller;

import com.dataplatform.call.service.DataQueryService;
import com.dataplatform.call.service.RateLimitService;
import com.dataplatform.call.vo.ApiQueryReqVO;
import com.dataplatform.call.vo.BatchQueryReqVO;
import com.dataplatform.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/data")
public class DataQueryController {

    private static final Logger log = LoggerFactory.getLogger(DataQueryController.class);

    @Autowired
    private DataQueryService dataQueryService;

    @Autowired
    private RateLimitService rateLimitService;

    /**
     * 单条数据查询
     */
    @PostMapping("/query")
    public Result<Map<String, Object>> query(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestBody ApiQueryReqVO request) {

        // 1. API Key 认证 (后续实现)
        // Long callerId = authenticateApiKey(apiKey);
        // if (callerId == null) {
        //     return Result.fail(401, "无效的API Key");
        // }

        // 使用模拟调用方ID
        Long callerId = 1L;

        // 2. 限流检查
        if (!rateLimitService.checkRateLimit(apiKey, 1000)) {
            return Result.fail(429, "请求过于频繁，请稍后再试");
        }

        // 3. 执行查询
        Map<String, Object> result = dataQueryService.queryData(
            request.getVendorCode(),
            request.getDataType(),
            request.getParams(),
            callerId,
            apiKey
        );

        return Result.success(result);
    }

    /**
     * 批量数据查询
     */
    @PostMapping("/batch-query")
    public Result<Map<String, Object>> batchQuery(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody BatchQueryReqVO request) {

        Long callerId = 1L;

        // 限流检查
        if (!rateLimitService.checkRateLimit(apiKey, 1000)) {
            return Result.fail(429, "请求过于频繁，请稍后再试");
        }

        Map<String, Object> result = dataQueryService.batchQuery(
            request.getVendorCode(),
            request.getDataType(),
            request.getParamsList(),
            callerId,
            apiKey
        );

        return Result.success(result);
    }

    /**
     * 清除缓存
     */
    @PostMapping("/cache/clear")
    public Result<Void> clearCache(@RequestBody ApiQueryReqVO request) {
        dataQueryService.clearCache(
            request.getVendorCode(),
            request.getDataType(),
            request.getParams()
        );
        return Result.success(null);
    }

    /**
     * 获取缓存统计
     */
    @GetMapping("/cache/stats")
    public Result<Map<String, Object>> getCacheStats() {
        return Result.success(dataQueryService.getCacheStats());
    }
}