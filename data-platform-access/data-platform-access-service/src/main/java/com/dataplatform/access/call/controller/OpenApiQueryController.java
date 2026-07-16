package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.access.call.service.GrayVendorResolver;
import com.dataplatform.access.call.service.OpenApiQueryService;
import com.dataplatform.access.call.service.OpenApiQueryService.OpenApiCallContext;
import com.dataplatform.access.call.service.RateLimitService;
import com.dataplatform.access.call.vo.OpenApiBatchQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiBatchQueryRespVO;
import com.dataplatform.access.call.vo.OpenApiQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyProductService;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.api.Result;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问域数据调用的 Open Api Query Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/openapi/v1")
public class OpenApiQueryController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_API_VERSION = "v1";
    private static final int DEFAULT_RATE_LIMIT = 100;

    private final OpenApiQueryService openApiQueryService;
    private final RateLimitService rateLimitService;
    private final ApiKeyService apiKeyService;
    private final ApiKeyInterfaceService apiKeyInterfaceService;
    private final ApiKeyProductService apiKeyProductService;
    private final CallerProductService callerProductService;
    private final CallerService callerService;
    private final CallSceneService callSceneService;
    private final ApiInterfaceFeignClient apiInterfaceFeignClient;
    private final VendorConfigInternalFeignClient vendorConfigFeignClient;
    private final VendorInternalFeignClient vendorFeignClient;
    private final GrayVendorResolver grayVendorResolver;

    public OpenApiQueryController(OpenApiQueryService openApiQueryService,
                                  RateLimitService rateLimitService,
                                  ApiKeyService apiKeyService,
                                  ApiKeyInterfaceService apiKeyInterfaceService,
                                  ApiKeyProductService apiKeyProductService,
                                  CallerProductService callerProductService,
                                  CallerService callerService,
                                  CallSceneService callSceneService,
                                  ApiInterfaceFeignClient apiInterfaceFeignClient,
                                  VendorConfigInternalFeignClient vendorConfigFeignClient,
                                  VendorInternalFeignClient vendorFeignClient,
                                  GrayVendorResolver grayVendorResolver) {
        this.openApiQueryService = openApiQueryService;
        this.rateLimitService = rateLimitService;
        this.apiKeyService = apiKeyService;
        this.apiKeyInterfaceService = apiKeyInterfaceService;
        this.apiKeyProductService = apiKeyProductService;
        this.callerProductService = callerProductService;
        this.callerService = callerService;
        this.callSceneService = callSceneService;
        this.apiInterfaceFeignClient = apiInterfaceFeignClient;
        this.vendorConfigFeignClient = vendorConfigFeignClient;
        this.vendorFeignClient = vendorFeignClient;
        this.grayVendorResolver = grayVendorResolver;
    }

    @PostMapping("/query")
    public Result<OpenApiQueryRespVO> query(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody OpenApiQueryReqVO request,
            HttpServletRequest httpRequest) {

        String apiCode = normalize(request != null ? request.getApiCode() : null);
        if (apiCode == null) {
            return Result.error(400, "apiCode不能为空");
        }
        String productCode = normalize(request != null ? request.getProductCode() : null);
        if (productCode == null) {
            return Result.error(400, "productCode不能为空");
        }
        String sceneCode = normalize(request != null ? request.getSceneCode() : null);
        if (sceneCode == null) {
            return Result.error(400, "sceneCode不能为空");
        }
        if (!validateCacheRequest(request.getUseCache(), request.getCacheDays())) {
            return Result.error(400, "useCache=true时cacheDays必须大于0");
        }

        ApiKey apiKeyEntity = validateApiKey(extractApiKey(apiKeyHeader, authorization));
        if (apiKeyEntity == null) {
            return Result.error(401, "无效的API Key");
        }
        CallerInfo caller = callerService.getById(apiKeyEntity.getCallerId());
        if (caller == null) {
            return Result.error(403, "调用方不存在");
        }
        CallerProduct product = callerProductService.getActiveProduct(apiKeyEntity.getCallerId(), productCode);
        if (product == null) {
            return Result.error(403, "调用方未配置该产品");
        }
        if (!apiKeyProductService.hasProductPermission(apiKeyEntity.getId(), product.getId())) {
            return Result.error(403, "API Key没有访问该产品的权限");
        }
        CallScene scene = callSceneService.getActiveScene(sceneCode);
        if (scene == null) {
            return Result.error(403, "调用场景不存在或未启用");
        }

        GrayVendorResolver.GrayRequestContext grayCtx = GrayVendorResolver.fromRequest(httpRequest,
                apiKeyEntity.getCallerId(), caller.getCallerCode());
        ApiRoute route = resolveApiRoute(apiCode, grayCtx);
        if (route == null) {
            return Result.error(404, "接口配置不存在");
        }
        if (!validateInterfacePermission(apiKeyEntity.getId(), route.interfaceId())) {
            return Result.error(403, "API Key没有访问该接口的权限");
        }
        String paramError = validateParams(route.interfaceId(),
                request.getParams() != null ? request.getParams() : Collections.emptyMap());
        if (paramError != null) {
            return Result.error(400, paramError);
        }
        if (!checkRateLimit(apiKeyEntity)) {
            return Result.error(429, "请求过于频繁，请稍后再试");
        }
        if (!apiKeyService.validateAndConsumeQuota(apiKeyEntity.getApiKey(), 1)) {
            return Result.error(429, "API Key配额不足");
        }

        OpenApiCallContext context = buildContext(request, apiKeyEntity, caller, product, scene, route,
                request.getParams() != null ? request.getParams() : Collections.emptyMap());
        context.setTraceId(traceId);
        return Result.success(openApiQueryService.query(context));
    }

    @PostMapping("/batch-query")
    public Result<OpenApiBatchQueryRespVO> batchQuery(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody OpenApiBatchQueryReqVO request,
            HttpServletRequest httpRequest) {

        String apiCode = normalize(request != null ? request.getApiCode() : null);
        if (apiCode == null) {
            return Result.error(400, "apiCode不能为空");
        }
        String productCode = normalize(request != null ? request.getProductCode() : null);
        if (productCode == null) {
            return Result.error(400, "productCode不能为空");
        }
        String sceneCode = normalize(request != null ? request.getSceneCode() : null);
        if (sceneCode == null) {
            return Result.error(400, "sceneCode不能为空");
        }
        if (!validateCacheRequest(request.getUseCache(), request.getCacheDays())) {
            return Result.error(400, "useCache=true时cacheDays必须大于0");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return Result.error(400, "items不能为空");
        }

        ApiKey apiKeyEntity = validateApiKey(extractApiKey(apiKeyHeader, authorization));
        if (apiKeyEntity == null) {
            return Result.error(401, "无效的API Key");
        }
        CallerInfo caller = callerService.getById(apiKeyEntity.getCallerId());
        if (caller == null) {
            return Result.error(403, "调用方不存在");
        }
        CallerProduct product = callerProductService.getActiveProduct(apiKeyEntity.getCallerId(), productCode);
        if (product == null) {
            return Result.error(403, "调用方未配置该产品");
        }
        if (!apiKeyProductService.hasProductPermission(apiKeyEntity.getId(), product.getId())) {
            return Result.error(403, "API Key没有访问该产品的权限");
        }
        CallScene scene = callSceneService.getActiveScene(sceneCode);
        if (scene == null) {
            return Result.error(403, "调用场景不存在或未启用");
        }

        GrayVendorResolver.GrayRequestContext batchGrayCtx = GrayVendorResolver.fromRequest(httpRequest,
                apiKeyEntity.getCallerId(), caller.getCallerCode());
        ApiRoute route = resolveApiRoute(apiCode, batchGrayCtx);
        if (route == null) {
            return Result.error(404, "接口配置不存在");
        }
        if (!validateInterfacePermission(apiKeyEntity.getId(), route.interfaceId())) {
            return Result.error(403, "API Key没有访问该接口的权限");
        }
        for (OpenApiBatchQueryReqVO.QueryItem item : request.getItems()) {
            String paramError = validateParams(route.interfaceId(),
                    item.getParams() != null ? item.getParams() : Collections.emptyMap());
            if (paramError != null) {
                return Result.error(400, paramError);
            }
        }
        if (!checkRateLimit(apiKeyEntity)) {
            return Result.error(429, "请求过于频繁，请稍后再试");
        }
        if (!apiKeyService.validateAndConsumeQuota(apiKeyEntity.getApiKey(), request.getItems().size())) {
            return Result.error(429, "API Key配额不足");
        }

        return Result.success(buildBatchResp(request, apiKeyEntity, caller, product, scene, route, traceId));
    }

    private boolean validateCacheRequest(Boolean useCache, Integer cacheDays) {
        return !Boolean.TRUE.equals(useCache) || (cacheDays != null && cacheDays > 0);
    }

    private String extractApiKey(String apiKeyHeader, String authorization) {
        String apiKey = normalize(apiKeyHeader);
        if (apiKey != null) {
            return apiKey;
        }
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return normalize(authorization.substring(BEARER_PREFIX.length()));
        }
        return null;
    }

    private ApiKey validateApiKey(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        ApiKey apiKeyEntity = apiKeyService.getByKey(apiKey);
        if (apiKeyEntity == null || apiKeyEntity.getStatus() != ApiKeyStatus.ACTIVE) {
            return null;
        }
        if (apiKeyEntity.getExpireTime() != null && apiKeyEntity.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        return apiKeyEntity;
    }

    private boolean validateInterfacePermission(Long apiKeyId, Long interfaceId) {
        if (interfaceId == null) {
            return false;
        }
        return apiKeyInterfaceService.hasInterfacePermission(apiKeyId, interfaceId);
    }

    private String validateParams(Long interfaceId, Map<String, Object> params) {
        Result<List<InterfaceParamDTO>> paramResult = apiInterfaceFeignClient.listParams(interfaceId);
        List<InterfaceParamDTO> definitions = paramResult != null ? paramResult.getData() : Collections.emptyList();
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        Map<String, Object> safeParams = params != null ? params : Collections.emptyMap();
        for (InterfaceParamDTO definition : definitions) {
            String name = normalize(definition.getParamName());
            if (name == null) {
                continue;
            }
            Object value = safeParams.get(name);
            if (Boolean.TRUE.equals(definition.getRequired()) && isMissing(value)) {
                return name + "不能为空";
            }
            if (!isMissing(value) && !matchesParamType(value, normalize(definition.getParamType()))) {
                return name + "类型必须为" + definition.getParamType();
            }
        }
        return null;
    }

    private boolean isMissing(Object value) {
        return value == null || (value instanceof String text && text.trim().isEmpty());
    }

    private boolean matchesParamType(Object value, String type) {
        if (type == null || "string".equalsIgnoreCase(type)) {
            return value instanceof String;
        }
        if ("number".equalsIgnoreCase(type)) {
            return value instanceof Number;
        }
        if ("boolean".equalsIgnoreCase(type)) {
            return value instanceof Boolean;
        }
        if ("object".equalsIgnoreCase(type)) {
            return value instanceof Map;
        }
        if ("array".equalsIgnoreCase(type)) {
            return value instanceof List;
        }
        return false;
    }

    private boolean checkRateLimit(ApiKey apiKeyEntity) {
        Integer rateLimit = apiKeyEntity.getRateLimit() != null ? apiKeyEntity.getRateLimit() : DEFAULT_RATE_LIMIT;
        return rateLimitService.checkRateLimit(apiKeyEntity.getApiKey(), rateLimit);
    }

    private ApiRoute resolveApiRoute(String apiCode, GrayVendorResolver.GrayRequestContext grayCtx) {
        Result<ApiInterfaceDTO> interfaceResult = apiInterfaceFeignClient.getByInterfaceCode(apiCode);
        ApiInterfaceDTO apiInterface = interfaceResult != null ? interfaceResult.getData() : null;
        if (apiInterface == null || apiInterface.getId() == null) {
            return null;
        }

        Result<List<VendorConfigDTO>> configResult = vendorConfigFeignClient.list(
                null,
                null,
                apiInterface.getId(),
                StatusConstants.ACTIVE);
        List<VendorConfigDTO> configs = configResult != null ? configResult.getData() : Collections.emptyList();
        if (configs == null || configs.isEmpty()) {
            return null;
        }

        VendorConfigDTO config = configs.get(0);
        if (configs.size() >= 2) {
            VendorConfigDTO graySelected = grayVendorResolver.resolve(apiCode, configs, grayCtx);
            if (graySelected != null) {
                config = graySelected;
            }
        }

        VendorInfoDTO vendor = getVendor(config.getVendorId());
        if (vendor == null || normalize(vendor.getVendorCode()) == null || normalize(config.getDataTypeCode()) == null) {
            return null;
        }
        return new ApiRoute(apiInterface.getId(), config.getVendorId(), vendor.getVendorCode(), config.getDataTypeCode(), config);
    }

    private VendorInfoDTO getVendor(Long vendorId) {
        if (vendorId == null) {
            return null;
        }
        Result<VendorInfoDTO> vendorResult = vendorFeignClient.getById(vendorId);
        return vendorResult != null ? vendorResult.getData() : null;
    }

    private OpenApiBatchQueryRespVO buildBatchResp(OpenApiBatchQueryReqVO request, ApiKey apiKey,
                                                   CallerInfo caller,
                                                   CallerProduct product, CallScene scene, ApiRoute route,
                                                   String traceId) {
        int success = 0;
        int failed = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (OpenApiBatchQueryReqVO.QueryItem item : request.getItems()) {
            OpenApiCallContext context = buildContext(request, apiKey, caller, product, scene, route,
                    item.getParams() != null ? item.getParams() : Collections.emptyMap());
            context.setTraceId(traceId);
            OpenApiQueryRespVO itemResp = openApiQueryService.query(context);
            if (Boolean.TRUE.equals(itemResp.getSuccess())) {
                success++;
            } else {
                failed++;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("itemId", item.getItemId());
            result.put("requestId", itemResp.getPlatformRequestId());
            result.put("success", itemResp.getSuccess());
            result.put("cached", itemResp.getCached());
            result.put("durationMs", itemResp.getDurationMs());
            result.put("cost", itemResp.getCost());
            result.put("data", itemResp.getData());
            result.put("errorCode", itemResp.getErrorCode());
            result.put("errorMsg", itemResp.getErrorMsg());
            results.add(result);
        }

        OpenApiBatchQueryRespVO resp = new OpenApiBatchQueryRespVO();
        resp.setRequestId(request.getRequestId());
        resp.setBatchId("batch_" + System.currentTimeMillis());
        resp.setApiCode(request.getApiCode());
        resp.setApiVersion(normalize(request.getApiVersion()) != null ? request.getApiVersion() : DEFAULT_API_VERSION);
        resp.setProductCode(request.getProductCode());
        resp.setSceneCode(request.getSceneCode());
        resp.setTotal(request.getItems().size());
        resp.setSuccess(success);
        resp.setFailed(failed);
        resp.setResults(results);
        return resp;
    }

    private OpenApiCallContext buildContext(OpenApiQueryReqVO request, ApiKey apiKey, CallerInfo caller,
                                            CallerProduct product, CallScene scene, ApiRoute route,
                                            Map<String, Object> params) {
        OpenApiCallContext context = new OpenApiCallContext();
        context.setExternalRequestId(request.getRequestId());
        context.setApiCode(request.getApiCode());
        context.setApiVersion(request.getApiVersion());
        context.setCallerId(apiKey.getCallerId());
        context.setTenantId(caller.getTenantId());
        context.setApiKeyId(apiKey.getId());
        context.setVendorId(route.vendorId());
        context.setVendorCode(route.vendorCode());
        context.setDataTypeCode(route.dataTypeCode());
        context.setVendorConfig(route.config());
        context.setProductId(product.getId());
        context.setProductCode(product.getProductCode());
        context.setProductName(product.getProductName());
        context.setSceneCode(scene.getSceneCode());
        context.setSceneName(scene.getSceneName());
        context.setUseCache(request.getUseCache());
        context.setCacheDays(request.getCacheDays());
        context.setCacheScope(product.getCacheScope());
        context.setParams(params);
        return context;
    }

    private OpenApiCallContext buildContext(OpenApiBatchQueryReqVO request, ApiKey apiKey, CallerInfo caller,
                                            CallerProduct product, CallScene scene, ApiRoute route,
                                            Map<String, Object> params) {
        OpenApiQueryReqVO singleRequest = new OpenApiQueryReqVO();
        singleRequest.setRequestId(request.getRequestId());
        singleRequest.setApiCode(request.getApiCode());
        singleRequest.setApiVersion(request.getApiVersion());
        singleRequest.setProductCode(request.getProductCode());
        singleRequest.setSceneCode(request.getSceneCode());
        singleRequest.setUseCache(request.getUseCache());
        singleRequest.setCacheDays(request.getCacheDays());
        return buildContext(singleRequest, apiKey, caller, product, scene, route, params);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record ApiRoute(Long interfaceId, Long vendorId, String vendorCode, String dataTypeCode, VendorConfigDTO config) {
    }
}
