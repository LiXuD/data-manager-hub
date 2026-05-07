package com.dataplatform.call.controller;

import com.dataplatform.api.Result;
import com.dataplatform.call.service.DataQueryService;
import com.dataplatform.call.service.RateLimitService;
import com.dataplatform.call.vo.ApiQueryReqVO;
import com.dataplatform.call.vo.BatchQueryReqVO;
import com.dataplatform.caller.api.dto.ApiKeyDTO;
import com.dataplatform.caller.api.feign.CallerFeignClient;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.interface_.api.feign.ApiInterfaceFeignClient;
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
    private CallerFeignClient callerFeignClient;

    @Autowired
    private ApiInterfaceFeignClient apiInterfaceFeignClient;

    @PostMapping("/query")
    public Result<Map<String, Object>> query(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestBody ApiQueryReqVO request) {

        ApiKeyDTO apiKeyEntity = validateApiKey(apiKey);
        if (apiKeyEntity == null) {
            return Result.error(401, "无效的API Key");
        }

        Long callerId = apiKeyEntity.getCallerId();

        // 验证API Key是否有该接口的权限
        if (!validateInterfacePermission(apiKeyEntity.getId(), request.getInterfaceCode())) {
            return Result.error(403, "API Key没有访问该接口的权限");
        }

        if (!rateLimitService.checkRateLimit(apiKey, apiKeyEntity.getRateLimit() != null ? apiKeyEntity.getRateLimit() : 100)) {
            return Result.error(429, "请求过于频繁，请稍后再试");
        }

        Map<String, Object> result = dataQueryService.queryData(
                request.getVendorCode(),
                request.getDataType(),
                request.getInterfaceCode(),
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

        ApiKeyDTO apiKeyEntity = validateApiKey(apiKey);
        if (apiKeyEntity == null) {
            return Result.error(401, "无效的API Key");
        }

        Long callerId = apiKeyEntity.getCallerId();

        // 验证API Key是否有该接口的权限
        if (!validateInterfacePermission(apiKeyEntity.getId(), request.getInterfaceCode())) {
            return Result.error(403, "API Key没有访问该接口的权限");
        }

        if (!rateLimitService.checkRateLimit(apiKey, apiKeyEntity.getRateLimit() != null ? apiKeyEntity.getRateLimit() : 100)) {
            return Result.error(429, "请求过于频繁，请稍后再试");
        }

        Map<String, Object> result = dataQueryService.batchQuery(
                request.getVendorCode(),
                request.getDataType(),
                request.getInterfaceCode(),
                request.getParamsList(),
                callerId,
                apiKey
        );

        return Result.success(result);
    }

    private ApiKeyDTO validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }

        Result<ApiKeyDTO> result = callerFeignClient.validateApiKey(apiKey);
        ApiKeyDTO apiKeyEntity = result != null ? result.getData() : null;
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

    private boolean validateInterfacePermission(Long apiKeyId, String interfaceCode) {
        if (interfaceCode == null || interfaceCode.trim().isEmpty()) {
            return true; // 没有指定接口code，可能是旧版调用，暂时允许
        }

        try {
            // 获取接口信息
            Result<ApiInterfaceDTO> interfaceResult = apiInterfaceFeignClient.getByInterfaceCode(interfaceCode);
            if (interfaceResult == null || interfaceResult.getData() == null) {
                return true; // 接口不存在，暂时允许
            }

            Long interfaceId = interfaceResult.getData().getId();

            // 检查该API Key是否有该接口权限
            Result<Boolean> permissionResult = callerFeignClient.hasInterfacePermission(apiKeyId, interfaceId);
            if (permissionResult == null) {
                return false;
            }

            Boolean hasPermission = permissionResult.getData();
            return hasPermission == null || hasPermission; // 如果没有返回或返回true则允许（兼容未配置的情况）
        } catch (Exception e) {
            log.warn("检查接口权限失败，默认允许: {}", e.getMessage());
            return true;
        }
    }

    @OperationLog(module = "数据查询", operation = "清除缓存")
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
