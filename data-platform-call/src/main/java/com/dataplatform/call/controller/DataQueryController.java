package com.dataplatform.call.controller;

import com.dataplatform.call.service.DataQueryService;
import com.dataplatform.call.service.RateLimitService;
import com.dataplatform.call.vo.ApiQueryReqVO;
import com.dataplatform.call.vo.BatchQueryReqVO;
import com.dataplatform.caller.entity.ApiKey;
import com.dataplatform.caller.service.ApiKeyService;
import com.dataplatform.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/data")
public class DataQueryController {

    private static final Logger log = LoggerFactory.getLogger(DataQueryController.class);
    private static final String API_KEY_STATUS_ACTIVE = "active";

    @Autowired
    private DataQueryService dataQueryService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private ApiKeyService apiKeyService;

    @PostMapping("/query")
    public Result<Map<String, Object>> query(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestBody ApiQueryReqVO request) {

        ApiKey apiKeyEntity = validateApiKey(apiKey);
        if (apiKeyEntity == null) {
            return Result.error(401, "无效的API Key");
        }

        Long callerId = apiKeyEntity.getCallerId();

        if (!rateLimitService.checkRateLimit(apiKey, apiKeyEntity.getRateLimit())) {
            return Result.error(429, "请求过于频繁，请稍后再试");
        }

        Map<String, Object> result = dataQueryService.queryData(
            request.getVendorCode(),
            request.getDataType(),
            request.getParams(),
            callerId,
            apiKey
        );

        return Result.success(result);
    }

    @PostMapping("/batch-query")
    public Result<Map<String, Object>> batchQuery(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody BatchQueryReqVO request) {

        ApiKey apiKeyEntity = validateApiKey(apiKey);
        if (apiKeyEntity == null) {
            return Result.error(401, "无效的API Key");
        }

        Long callerId = apiKeyEntity.getCallerId();

        if (!rateLimitService.checkRateLimit(apiKey, apiKeyEntity.getRateLimit())) {
            return Result.error(429, "请求过于频繁，请稍后再试");
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

    private ApiKey validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }

        ApiKey apiKeyEntity = apiKeyService.getByKey(apiKey);
        if (apiKeyEntity == null) {
            return null;
        }
        if (!API_KEY_STATUS_ACTIVE.equals(apiKeyEntity.getStatus())) {
            return null;
        }
        if (apiKeyEntity.getExpireTime() != null &&
            apiKeyEntity.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        return apiKeyEntity;
    }

    @PostMapping("/cache/clear")
    public Result<Void> clearCache(@RequestBody ApiQueryReqVO request) {
        dataQueryService.clearCache(
            request.getVendorCode(),
            request.getDataType(),
            request.getParams()
        );
        return Result.success(null);
    }

    @GetMapping("/cache/stats")
    public Result<Map<String, Object>> getCacheStats() {
        return Result.success(dataQueryService.getCacheStats());
    }
}