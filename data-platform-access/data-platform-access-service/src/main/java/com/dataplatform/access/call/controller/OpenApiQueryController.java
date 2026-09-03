package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.access.call.service.OpenApiQueryException;
import com.dataplatform.access.call.service.OpenApiQueryService;
import com.dataplatform.access.call.service.OpenApiQueryService.OpenApiCallContext;
import com.dataplatform.access.call.service.RateLimitService;
import com.dataplatform.access.call.service.InterfaceContractValidator;
import com.dataplatform.access.call.vo.OpenApiBatchQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiBatchQueryRespVO;
import com.dataplatform.access.call.vo.OpenApiQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
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
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.RoutingReadiness;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
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
                                  VendorInternalFeignClient vendorFeignClient) {
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
    }

    @PostMapping("/query")
    public ResponseEntity<Result<OpenApiQueryRespVO>> query(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody OpenApiQueryReqVO request,
            HttpServletRequest httpRequest) {

        if (request == null) {
            return error(400, "请求体不能为空");
        }
        String apiCode = normalize(request != null ? request.getApiCode() : null);
        if (apiCode == null) {
            return error(400, "apiCode不能为空");
        }
        String productCode = normalize(request != null ? request.getProductCode() : null);
        if (productCode == null) {
            return error(400, "productCode不能为空");
        }
        String sceneCode = normalize(request != null ? request.getSceneCode() : null);
        if (sceneCode == null) {
            return error(400, "sceneCode不能为空");
        }
        if (!validateCacheRequest(request.getUseCache(), request.getCacheDays())) {
            return error(400, "useCache=true时cacheDays必须大于0");
        }

        ApiKey apiKeyEntity = validateApiKey(extractApiKey(apiKeyHeader, authorization));
        if (apiKeyEntity == null) {
            return error(401, "无效的API Key");
        }
        CallerInfo caller = loadCaller(apiKeyEntity.getCallerId());
        if (caller == null) {
            return error(403, "调用方不存在");
        }
        if (!CommonStatus.ACTIVE.equals(caller.getStatus())) {
            return error(403, "调用方已停用");
        }
        CallerProduct product = loadActiveProduct(apiKeyEntity.getCallerId(), productCode);
        if (product == null) {
            return error(403, "调用方未配置该产品");
        }
        if (!hasProductPermission(apiKeyEntity.getId(), product.getId())) {
            return error(403, "API Key没有访问该产品的权限");
        }
        CallScene scene = loadActiveScene(sceneCode);
        if (scene == null) {
            return error(403, "调用场景不存在或未启用");
        }

        ApiRoute route;
        try {
            route = resolveApiRoute(apiCode);
        } catch (OpenApiQueryException exception) {
            return error(exception);
        }
        if (route == null) {
            return error(404, "接口配置不存在");
        }
        ApiKeyInterface interfaceGrant = loadEffectiveGrant(apiKeyEntity.getId(), route.interfaceId());
        if (interfaceGrant == null) {
            return error(403, "API Key没有访问该接口的权限");
        }
        String cachePolicyError = validateApprovedCachePolicy(
                request.getUseCache(), request.getCacheDays(), interfaceGrant);
        if (cachePolicyError != null) {
            return error(403, cachePolicyError);
        }
        Map<String, Object> requestParams = request.getParams() != null
                ? new HashMap<>(request.getParams()) : new HashMap<>();
        String paramError = validateParams(route.contract(), requestParams);
        if (paramError != null) {
            return error(400, paramError);
        }
        request.setParams(requestParams);
        if (!checkRateLimit(apiKeyEntity)) {
            return error(429, "请求过于频繁，请稍后再试");
        }
        if (!consumeQuota(apiKeyEntity.getApiKey(), 1)) {
            return error(429, "API Key配额不足");
        }

        OpenApiCallContext context = buildContext(request, apiKeyEntity, caller, product, scene, route,
                requestParams);
        context.setInterfaceContract(route.contract());
        context.setTraceId(traceId);
        try {
            return ResponseEntity.ok(Result.success(openApiQueryService.query(context)));
        } catch (OpenApiQueryException exception) {
            return error(exception);
        }
    }

    @PostMapping("/batch-query")
    public ResponseEntity<Result<OpenApiBatchQueryRespVO>> batchQuery(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody OpenApiBatchQueryReqVO request,
            HttpServletRequest httpRequest) {

        if (request == null) {
            return error(400, "请求体不能为空");
        }
        String apiCode = normalize(request != null ? request.getApiCode() : null);
        if (apiCode == null) {
            return error(400, "apiCode不能为空");
        }
        String productCode = normalize(request != null ? request.getProductCode() : null);
        if (productCode == null) {
            return error(400, "productCode不能为空");
        }
        String sceneCode = normalize(request != null ? request.getSceneCode() : null);
        if (sceneCode == null) {
            return error(400, "sceneCode不能为空");
        }
        if (!validateCacheRequest(request.getUseCache(), request.getCacheDays())) {
            return error(400, "useCache=true时cacheDays必须大于0");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return error(400, "items不能为空");
        }
        for (int index = 0; index < request.getItems().size(); index++) {
            if (request.getItems().get(index) == null) {
                return error(400, "items[" + index + "]不能为空");
            }
        }

        ApiKey apiKeyEntity = validateApiKey(extractApiKey(apiKeyHeader, authorization));
        if (apiKeyEntity == null) {
            return error(401, "无效的API Key");
        }
        CallerInfo caller = loadCaller(apiKeyEntity.getCallerId());
        if (caller == null) {
            return error(403, "调用方不存在");
        }
        if (!CommonStatus.ACTIVE.equals(caller.getStatus())) {
            return error(403, "调用方已停用");
        }
        CallerProduct product = loadActiveProduct(apiKeyEntity.getCallerId(), productCode);
        if (product == null) {
            return error(403, "调用方未配置该产品");
        }
        if (!hasProductPermission(apiKeyEntity.getId(), product.getId())) {
            return error(403, "API Key没有访问该产品的权限");
        }
        CallScene scene = loadActiveScene(sceneCode);
        if (scene == null) {
            return error(403, "调用场景不存在或未启用");
        }

        ApiRoute route;
        try {
            route = resolveApiRoute(apiCode);
        } catch (OpenApiQueryException exception) {
            return error(exception);
        }
        if (route == null) {
            return error(404, "接口配置不存在");
        }
        ApiKeyInterface interfaceGrant = loadEffectiveGrant(apiKeyEntity.getId(), route.interfaceId());
        if (interfaceGrant == null) {
            return error(403, "API Key没有访问该接口的权限");
        }
        String cachePolicyError = validateApprovedCachePolicy(
                request.getUseCache(), request.getCacheDays(), interfaceGrant);
        if (cachePolicyError != null) {
            return error(403, cachePolicyError);
        }
        for (int index = 0; index < request.getItems().size(); index++) {
            OpenApiBatchQueryReqVO.QueryItem item = request.getItems().get(index);
            Map<String, Object> itemParams = item.getParams() != null
                    ? new HashMap<>(item.getParams()) : new HashMap<>();
            String paramError = validateParams(route.contract(), itemParams);
            if (paramError != null) {
                return error(400, paramError);
            }
            item.setParams(itemParams);
        }
        if (!checkRateLimit(apiKeyEntity)) {
            return error(429, "请求过于频繁，请稍后再试");
        }
        if (!consumeQuota(apiKeyEntity.getApiKey(), request.getItems().size())) {
            return error(429, "API Key配额不足");
        }

        try {
            return ResponseEntity.ok(Result.success(
                    buildBatchResp(request, apiKeyEntity, caller, product, scene, route, traceId)));
        } catch (OpenApiQueryException exception) {
            return error(exception);
        }
    }

    private <T> ResponseEntity<Result<T>> error(int code, String message) {
        return ResponseEntity.status(code).body(Result.error(code, message));
    }

    private <T> ResponseEntity<Result<T>> error(OpenApiQueryException exception) {
        return error(exception.getStatus(), exception.getErrorCode() + ": " + exception.getMessage());
    }

    @ExceptionHandler(OpenApiQueryException.class)
    public ResponseEntity<Result<Object>> handleOpenApiQueryException(OpenApiQueryException exception) {
        return error(exception);
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
        ApiKey apiKeyEntity;
        try {
            apiKeyEntity = apiKeyService.getByKey(apiKey);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_API_KEY_UNAVAILABLE", "API Key服务暂不可用");
        }
        if (apiKeyEntity == null || apiKeyEntity.getStatus() != ApiKeyStatus.ACTIVE) {
            return null;
        }
        if (apiKeyEntity.getExpireTime() != null
                && !apiKeyEntity.getExpireTime().isAfter(LocalDateTime.now())) {
            return null;
        }
        return apiKeyEntity;
    }

    private String validateApprovedCachePolicy(
            Boolean useCache,
            Integer cacheDays,
            ApiKeyInterface grant) {
        if (!Boolean.TRUE.equals(useCache)) {
            return null;
        }
        if (!Boolean.TRUE.equals(grant.getCacheEnabled())
                || grant.getApprovedCacheDays() == null) {
            return "当前接口授权未批准使用缓存";
        }
        if (cacheDays > grant.getApprovedCacheDays()) {
            return "请求缓存时效超过审批上限"
                    + grant.getApprovedCacheDays() + "天";
        }
        return null;
    }

    private String validateParams(InterfaceContractDTO contract, Map<String, Object> params) {
        List<InterfaceParamDTO> definitions = contract != null
                ? contract.getRequestFields() : Collections.emptyList();
        return InterfaceContractValidator.validate(definitions, params, true).firstError();
    }

    private boolean checkRateLimit(ApiKey apiKeyEntity) {
        if (Boolean.FALSE.equals(apiKeyEntity.getRateLimitEnabled())) {
            return true;
        }
        Integer rateLimit = apiKeyEntity.getRateLimit() != null ? apiKeyEntity.getRateLimit() : DEFAULT_RATE_LIMIT;
        try {
            return rateLimitService.checkRateLimit(apiKeyEntity.getApiKey(), rateLimit);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_RATE_LIMIT_UNAVAILABLE", "限流服务暂不可用");
        }
    }

    private CallerInfo loadCaller(Long callerId) {
        try {
            return callerService.getById(callerId);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_CALLER_UNAVAILABLE", "调用方服务暂不可用");
        }
    }

    private CallerProduct loadActiveProduct(Long callerId, String productCode) {
        try {
            return callerProductService.getActiveProduct(callerId, productCode);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_PRODUCT_UNAVAILABLE", "产品服务暂不可用");
        }
    }

    private boolean hasProductPermission(Long apiKeyId, Long productId) {
        try {
            return apiKeyProductService.hasProductPermission(apiKeyId, productId);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_PRODUCT_GRANT_UNAVAILABLE", "API Key产品授权服务暂不可用");
        }
    }

    private CallScene loadActiveScene(String sceneCode) {
        try {
            return callSceneService.getActiveScene(sceneCode);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_SCENE_UNAVAILABLE", "调用场景服务暂不可用");
        }
    }

    private ApiKeyInterface loadEffectiveGrant(Long apiKeyId, Long interfaceId) {
        try {
            return apiKeyInterfaceService.findEffectiveGrant(apiKeyId, interfaceId);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_INTERFACE_GRANT_UNAVAILABLE", "API Key接口授权服务暂不可用");
        }
    }

    private boolean consumeQuota(String apiKey, int count) {
        try {
            return apiKeyService.validateAndConsumeQuota(apiKey, count);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.serviceUnavailable(
                    "OPENAPI_QUOTA_UNAVAILABLE", "API Key配额服务暂不可用");
        }
    }

    private ApiRoute resolveApiRoute(String apiCode) {
        Result<ApiInterfaceDTO> interfaceResult;
        try {
            interfaceResult = apiInterfaceFeignClient.getByInterfaceCode(apiCode);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.badGateway(
                    "OPENAPI_INTERFACE_UNAVAILABLE", "接口路由服务暂不可用");
        }
        if (isDependencyFailure(interfaceResult)) {
            throw OpenApiQueryException.badGateway(
                    "OPENAPI_INTERFACE_UNAVAILABLE", "接口路由服务暂不可用");
        }
        ApiInterfaceDTO apiInterface = interfaceResult != null && Integer.valueOf(200).equals(interfaceResult.getCode())
                ? interfaceResult.getData() : null;
        if (apiInterface == null || apiInterface.getId() == null
                || apiInterface.getPrimaryVendorConfigId() == null
                || !(RoutingReadiness.READY.equals(apiInterface.getRoutingReadiness())
                || RoutingReadiness.FALLBACK_NOT_READY.equals(apiInterface.getRoutingReadiness()))) {
            return null;
        }

        VendorConfigDTO primary = getActiveConfig(apiInterface.getPrimaryVendorConfigId(), apiInterface.getId());
        if (primary == null) {
            return null;
        }
        VendorConfigDTO fallback = null;
        if (RoutingReadiness.READY.equals(apiInterface.getRoutingReadiness())
                && apiInterface.getFallbackVendorConfigId() != null) {
            fallback = getActiveConfig(apiInterface.getFallbackVendorConfigId(), apiInterface.getId());
        }

        VendorInfoDTO primaryVendor = getActiveVendor(primary.getVendorId());
        VendorInfoDTO fallbackVendor = fallback == null ? null : getActiveVendor(fallback.getVendorId());
        String primaryDataTypeCode = normalize(primary.getDataTypeCode());
        String fallbackDataTypeCode = fallback == null ? null : normalize(fallback.getDataTypeCode());
        if (primaryVendor == null || normalize(primaryVendor.getVendorCode()) == null
                || primaryDataTypeCode == null) {
            return null;
        }
        if (fallback != null && (fallbackVendor == null || normalize(fallbackVendor.getVendorCode()) == null
                || !primaryDataTypeCode.equals(fallbackDataTypeCode))) {
            fallback = null;
            fallbackVendor = null;
        }
        InterfaceContractDTO contract = loadContract(apiInterface.getId());
        return new ApiRoute(apiInterface.getId(), primary.getVendorId(), primaryVendor.getVendorCode(),
                fallback == null ? null : fallbackVendor.getVendorCode(), primaryDataTypeCode,
                primary, fallback, contract);
    }

    private InterfaceContractDTO loadContract(Long interfaceId) {
        Result<InterfaceContractDTO> contractResult;
        try {
            contractResult = apiInterfaceFeignClient.getContract(interfaceId);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.badGateway(
                    "OPENAPI_CONTRACT_UNAVAILABLE", "接口契约服务暂不可用");
        }
        if (isDependencyFailure(contractResult) || !Integer.valueOf(200).equals(contractResult.getCode())
                || contractResult.getData() == null) {
            throw OpenApiQueryException.badGateway(
                    "OPENAPI_CONTRACT_UNAVAILABLE", "接口契约服务暂不可用");
        }
        return contractResult.getData();
    }

    private VendorInfoDTO getVendor(Long vendorId) {
        if (vendorId == null) {
            return null;
        }
        Result<VendorInfoDTO> vendorResult;
        try {
            vendorResult = vendorFeignClient.getById(vendorId);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.badGateway(
                    "OPENAPI_VENDOR_UNAVAILABLE", "厂商路由服务暂不可用");
        }
        if (isDependencyFailure(vendorResult)) {
            throw OpenApiQueryException.badGateway(
                    "OPENAPI_VENDOR_UNAVAILABLE", "厂商路由服务暂不可用");
        }
        return vendorResult != null && Integer.valueOf(200).equals(vendorResult.getCode())
                ? vendorResult.getData() : null;
    }

    private VendorConfigDTO getActiveConfig(Long configId, Long interfaceId) {
        Result<VendorConfigDTO> result;
        try {
            result = vendorConfigFeignClient.getById(configId);
        } catch (RuntimeException exception) {
            throw OpenApiQueryException.badGateway(
                    "OPENAPI_VENDOR_CONFIG_UNAVAILABLE", "厂商配置服务暂不可用");
        }
        if (isDependencyFailure(result)) {
            throw OpenApiQueryException.badGateway(
                    "OPENAPI_VENDOR_CONFIG_UNAVAILABLE", "厂商配置服务暂不可用");
        }
        VendorConfigDTO config = result != null && Integer.valueOf(200).equals(result.getCode())
                ? result.getData() : null;
        if (config == null || !configId.equals(config.getId())
                || !interfaceId.equals(config.getInterfaceId())
                || !StatusConstants.ACTIVE.equals(config.getStatus())) {
            return null;
        }
        return config;
    }

    private VendorInfoDTO getActiveVendor(Long vendorId) {
        VendorInfoDTO vendor = getVendor(vendorId);
        return vendor != null && StatusConstants.ACTIVE.equals(vendor.getStatus()) ? vendor : null;
    }

    private boolean isDependencyFailure(Result<?> result) {
        return result == null || result.getCode() == null
                || (!Integer.valueOf(200).equals(result.getCode())
                && !Integer.valueOf(404).equals(result.getCode()));
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
            context.setInterfaceContract(route.contract());
            OpenApiQueryRespVO itemResp = openApiQueryService.query(context);
            if (itemResp == null) {
                throw OpenApiQueryException.serviceUnavailable(
                        "OPENAPI_QUERY_UNAVAILABLE", "调用服务未返回有效结果");
            }
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
        context.setInterfaceId(route.interfaceId());
        context.setVendorId(route.vendorId());
        context.setVendorCode(route.vendorCode());
        context.setFallbackVendorCode(route.fallbackVendorCode());
        context.setDataTypeCode(route.dataTypeCode());
        context.setVendorConfig(route.primaryConfig());
        context.setPrimaryVendorConfig(route.primaryConfig());
        context.setFallbackVendorConfig(route.fallbackConfig());
        context.setProductId(product.getId());
        context.setProductCode(product.getProductCode());
        context.setProductName(product.getProductName());
        context.setSceneCode(scene.getSceneCode());
        context.setSceneName(scene.getSceneName());
        context.setUseCache(request.getUseCache());
        context.setCacheDays(request.getCacheDays());
        context.setCacheScope(product.getCacheScope());
        context.setParams(params);
        context.setInterfaceContract(route.contract());
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

    private record ApiRoute(Long interfaceId, Long vendorId, String vendorCode, String fallbackVendorCode,
                            String dataTypeCode, VendorConfigDTO primaryConfig,
                            VendorConfigDTO fallbackConfig, InterfaceContractDTO contract) {
    }
}
