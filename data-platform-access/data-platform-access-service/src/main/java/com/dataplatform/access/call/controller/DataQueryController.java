package com.dataplatform.access.call.controller;

import com.dataplatform.api.Result;
import com.dataplatform.access.call.service.DataQueryService;
import com.dataplatform.access.call.service.RateLimitService;
import com.dataplatform.access.call.vo.ApiQueryReqVO;
import com.dataplatform.access.call.vo.BatchQueryReqVO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 访问域数据调用的 Data Query Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/data")
public class DataQueryController {

    private static final Logger log = LoggerFactory.getLogger(DataQueryController.class);

    @Autowired
    private DataQueryService dataQueryService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyInterfaceService apiKeyInterfaceService;

    @Autowired
    private ApiInterfaceFeignClient apiInterfaceFeignClient;

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

        ApiKey apiKeyEntity = validateApiKey(apiKey);
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

    private ApiKey validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }

        ApiKey apiKeyEntity = apiKeyService.getByKey(apiKey);
        if (apiKeyEntity == null) {
            return null;
        }
        if (apiKeyEntity.getStatus() != ApiKeyStatus.ACTIVE) {
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
            return false;
        }

        try {
            Result<ApiInterfaceDTO> interfaceResult = apiInterfaceFeignClient.getByInterfaceCode(interfaceCode);
            if (interfaceResult == null || interfaceResult.getData() == null) {
                return false;
            }

            Long interfaceId = interfaceResult.getData().getId();

            return apiKeyInterfaceService.hasInterfacePermission(apiKeyId, interfaceId);
        } catch (Exception e) {
            log.warn("检查接口权限失败，拒绝访问: {}", e.getMessage());
            return false;
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
