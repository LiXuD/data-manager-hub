package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.access.call.service.OpenApiQueryService;
import com.dataplatform.access.call.service.OpenApiQueryException;
import com.dataplatform.access.call.service.OpenApiQueryService.OpenApiCallContext;
import com.dataplatform.access.call.service.RateLimitService;
import com.dataplatform.access.call.vo.OpenApiQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiBatchQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyProductService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.api.Result;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.RoutingReadiness;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenApiQueryControllerTest {

    private OpenApiQueryService openApiQueryService;
    private RateLimitService rateLimitService;
    private ApiKeyService apiKeyService;
    private ApiKeyInterfaceService apiKeyInterfaceService;
    private ApiKeyProductService apiKeyProductService;
    private CallerProductService callerProductService;
    private CallerService callerService;
    private CallSceneService callSceneService;
    private ApiInterfaceFeignClient apiInterfaceFeignClient;
    private VendorConfigInternalFeignClient vendorConfigFeignClient;
    private VendorInternalFeignClient vendorFeignClient;
    private OpenApiQueryController controller;

    @BeforeEach
    void setUp() {
        openApiQueryService = mock(OpenApiQueryService.class);
        rateLimitService = mock(RateLimitService.class);
        apiKeyService = mock(ApiKeyService.class);
        apiKeyInterfaceService = mock(ApiKeyInterfaceService.class);
        apiKeyProductService = mock(ApiKeyProductService.class);
        callerProductService = mock(CallerProductService.class);
        callerService = mock(CallerService.class);
        callSceneService = mock(CallSceneService.class);
        apiInterfaceFeignClient = mock(ApiInterfaceFeignClient.class);
        vendorConfigFeignClient = mock(VendorConfigInternalFeignClient.class);
        vendorFeignClient = mock(VendorInternalFeignClient.class);
        controller = new OpenApiQueryController(
                openApiQueryService,
                rateLimitService,
                apiKeyService,
                apiKeyInterfaceService,
                apiKeyProductService,
                callerProductService,
                callerService,
                callSceneService,
                apiInterfaceFeignClient,
                vendorConfigFeignClient,
                vendorFeignClient);
    }

    @Test
    void shouldQueryByUnifiedOpenApiRequest() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(10L);
        apiKey.setCallerId(20L);
        apiKey.setApiKey("test-key");
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setRateLimit(50);
        when(apiKeyService.getByKey("test-key")).thenReturn(apiKey);
        when(rateLimitService.checkRateLimit("test-key", 50)).thenReturn(true);

        CallerInfo caller = new CallerInfo();
        caller.setId(20L);
        caller.setTenantId(1L);
        caller.setStatus(CommonStatus.ACTIVE);
        when(callerService.getById(20L)).thenReturn(caller);

        CallerProduct product = new CallerProduct();
        product.setId(60L);
        product.setCallerId(20L);
        product.setProductCode("loan-risk");
        product.setProductName("信贷风控");
        product.setCacheScope("GLOBAL");
        when(callerProductService.getActiveProduct(20L, "loan-risk")).thenReturn(product);
        when(apiKeyProductService.hasProductPermission(10L, 60L)).thenReturn(true);

        CallScene scene = new CallScene();
        scene.setId(70L);
        scene.setSceneCode("pre-loan-review");
        scene.setSceneName("贷前审批");
        when(callSceneService.getActiveScene(1L, "pre-loan-review")).thenReturn(scene);

        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(30L);
        apiInterface.setInterfaceCode("PERSONAL_QUERY");
        apiInterface.setRoutingReadiness(RoutingReadiness.READY);
        apiInterface.setPrimaryVendorConfigId(100L);
        when(apiInterfaceFeignClient.getByInterfaceCode("PERSONAL_QUERY")).thenReturn(Result.success(apiInterface));
        ApiKeyInterface interfaceGrant = new ApiKeyInterface();
        interfaceGrant.setApiKeyId(10L);
        interfaceGrant.setInterfaceId(30L);
        interfaceGrant.setCacheEnabled(false);
        when(apiKeyInterfaceService.findEffectiveGrant(10L, 30L)).thenReturn(interfaceGrant);
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setInterfaceId(30L);
        contract.setRequestFields(List.of());
        contract.setResponseFields(List.of());
        when(apiInterfaceFeignClient.getContract(30L)).thenReturn(Result.success(contract));
        when(apiKeyService.validateAndConsumeQuota("test-key", 1)).thenReturn(true);

        VendorConfigDTO config = new VendorConfigDTO();
        config.setId(100L);
        config.setVendorId(40L);
        config.setInterfaceId(30L);
        config.setStatus(StatusConstants.ACTIVE);
        config.setDataTypeCode("personal");
        when(vendorConfigFeignClient.getById(100L)).thenReturn(Result.success(config));

        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(40L);
        vendor.setVendorCode("vendor-a");
        vendor.setStatus(StatusConstants.ACTIVE);
        when(vendorFeignClient.getById(40L)).thenReturn(Result.success(vendor));

        Map<String, Object> params = Map.of("name", "zhangsan");
        OpenApiQueryRespVO queryResp = new OpenApiQueryRespVO();
        queryResp.setRequestId("client-req-1");
        queryResp.setPlatformRequestId("req_platform_1");
        queryResp.setApiCode("PERSONAL_QUERY");
        queryResp.setProductCode("loan-risk");
        queryResp.setSceneCode("pre-loan-review");
        queryResp.setSuccess(true);
        queryResp.setData(Map.of("score", 99));
        queryResp.setCached(false);
        queryResp.setLatency(12L);
        when(openApiQueryService.query(any())).thenReturn(queryResp);

        OpenApiQueryReqVO request = new OpenApiQueryReqVO();
        request.setRequestId("client-req-1");
        request.setApiCode("PERSONAL_QUERY");
        request.setProductCode("loan-risk");
        request.setSceneCode("pre-loan-review");
        request.setParams(params);

        ResponseEntity<Result<OpenApiQueryRespVO>> response =
                controller.query("test-key", null, "trace-1", request, null);
        Result<OpenApiQueryRespVO> result = response.getBody();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("client-req-1", result.getData().getRequestId());
        assertEquals("req_platform_1", result.getData().getPlatformRequestId());
        assertEquals("PERSONAL_QUERY", result.getData().getApiCode());
        assertTrue(result.getData().getSuccess());
        assertEquals(99, result.getData().getData().get("score"));
        assertEquals(12L, result.getData().getLatency());
        verify(vendorConfigFeignClient).getById(100L);
        verify(vendorConfigFeignClient, never()).list(null, null, 30L, StatusConstants.ACTIVE);
        ArgumentCaptor<OpenApiCallContext> contextCaptor = ArgumentCaptor.forClass(OpenApiCallContext.class);
        verify(openApiQueryService).query(contextCaptor.capture());
        assertEquals("trace-1", contextCaptor.getValue().getTraceId());
    }

    @Test
    void shouldSkipAccessRateLimitWhenPolicyIsDisabled() {
        ApiKey apiKey = new ApiKey();
        apiKey.setApiKey("test-key");
        apiKey.setRateLimitEnabled(false);
        apiKey.setRateLimit(50);

        Boolean allowed = ReflectionTestUtils.invokeMethod(controller, "checkRateLimit", apiKey);

        assertTrue(Boolean.TRUE.equals(allowed));
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void shouldRejectMissingApiCode() {
        ResponseEntity<Result<OpenApiQueryRespVO>> response =
                controller.query("test-key", null, null, new OpenApiQueryReqVO(), null);
        Result<OpenApiQueryRespVO> result = response.getBody();

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    void shouldRejectNullSingleRequestWithoutServerError() {
        ResponseEntity<Result<OpenApiQueryRespVO>> response =
                controller.query(null, null, null, null, null);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(400, response.getBody().getCode());
        verifyNoInteractions(apiKeyService, callerService, openApiQueryService);
    }

    @Test
    void shouldRejectNullBatchRequestWithoutServerError() {
        ResponseEntity<Result<com.dataplatform.access.call.vo.OpenApiBatchQueryRespVO>> response =
                controller.batchQuery(null, null, null, null, null);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(400, response.getBody().getCode());
        verifyNoInteractions(apiKeyService, callerService, openApiQueryService);
    }

    @Test
    void mapsDependencyFailureToStructuredResponseAtControllerBoundary() {
        ResponseEntity<Result<Object>> response = controller.handleOpenApiQueryException(
                OpenApiQueryException.serviceUnavailable("OPENAPI_API_KEY_UNAVAILABLE", "API Key服务暂不可用"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals(503, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("OPENAPI_API_KEY_UNAVAILABLE"));
    }

    @Test
    void shouldMapContractDependencyFailureToStructuredBadGateway() {
        ApiKey apiKey = activeApiKey();
        when(apiKeyService.getByKey("test-key")).thenReturn(apiKey);
        when(callerService.getById(20L)).thenReturn(activeCaller());

        CallerProduct product = new CallerProduct();
        product.setId(60L);
        product.setCallerId(20L);
        product.setProductCode("loan-risk");
        when(callerProductService.getActiveProduct(20L, "loan-risk")).thenReturn(product);
        when(apiKeyProductService.hasProductPermission(10L, 60L)).thenReturn(true);

        CallScene scene = new CallScene();
        scene.setSceneCode("pre-loan-review");
        when(callSceneService.getActiveScene(1L, "pre-loan-review")).thenReturn(scene);

        when(apiInterfaceFeignClient.getByInterfaceCode("PERSONAL_QUERY"))
                .thenReturn(Result.success(routeInterface(RoutingReadiness.READY, null)));
        when(vendorConfigFeignClient.getById(100L))
                .thenReturn(Result.success(routeConfig(100L, 40L, "personal")));
        when(vendorFeignClient.getById(40L))
                .thenReturn(Result.success(routeVendor(40L, "vendor-a")));
        when(apiInterfaceFeignClient.getContract(30L))
                .thenReturn(Result.error(503, "contract service unavailable"));

        ResponseEntity<Result<OpenApiQueryRespVO>> response =
                controller.query("test-key", null, null, validRequest(), null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(502, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("OPENAPI_CONTRACT_UNAVAILABLE"));
        verifyNoInteractions(openApiQueryService);
    }

    @Test
    void shouldRejectInactiveCallerBeforeCallingProducts() {
        ApiKey apiKey = activeApiKey();
        CallerInfo caller = new CallerInfo();
        caller.setId(20L);
        caller.setStatus(CommonStatus.INACTIVE);
        when(apiKeyService.getByKey("test-key")).thenReturn(apiKey);
        when(callerService.getById(20L)).thenReturn(caller);

        ResponseEntity<Result<OpenApiQueryRespVO>> response =
                controller.query("test-key", null, null, validRequest(), null);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("调用方已停用", response.getBody().getMsg());
        verifyNoInteractions(callerProductService, apiKeyProductService, openApiQueryService);
    }

    @Test
    void shouldFailClosedWhenInterfaceHasNoExplicitPrimaryRoute() {
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(30L);
        apiInterface.setRoutingReadiness(RoutingReadiness.UNBOUND);
        when(apiInterfaceFeignClient.getByInterfaceCode("PERSONAL_QUERY"))
                .thenReturn(Result.success(apiInterface));

        Object route = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                controller, "resolveApiRoute", "PERSONAL_QUERY");

        assertNull(route);
        verifyNoInteractions(vendorConfigFeignClient, vendorFeignClient);
    }

    @Test
    void shouldKeepPrimaryRouteWhenFallbackIsNotReady() {
        when(apiInterfaceFeignClient.getByInterfaceCode("PERSONAL_QUERY"))
                .thenReturn(Result.success(routeInterface(RoutingReadiness.FALLBACK_NOT_READY, 200L)));
        when(vendorConfigFeignClient.getById(100L))
                .thenReturn(Result.success(routeConfig(100L, 40L, "personal")));
        when(vendorFeignClient.getById(40L)).thenReturn(Result.success(routeVendor(40L, "vendor-a")));
        when(apiInterfaceFeignClient.getContract(30L)).thenReturn(Result.success(new InterfaceContractDTO()));

        Object route = ReflectionTestUtils.invokeMethod(controller, "resolveApiRoute", "PERSONAL_QUERY");

        assertNotNull(route);
        assertNull(ReflectionTestUtils.invokeMethod(route, "fallbackConfig"));
        verify(vendorConfigFeignClient, never()).getById(200L);
    }

    @Test
    void shouldDisableInvalidFallbackWithoutBlockingPrimaryRoute() {
        when(apiInterfaceFeignClient.getByInterfaceCode("PERSONAL_QUERY"))
                .thenReturn(Result.success(routeInterface(RoutingReadiness.READY, 200L)));
        when(vendorConfigFeignClient.getById(100L))
                .thenReturn(Result.success(routeConfig(100L, 40L, "personal")));
        when(vendorConfigFeignClient.getById(200L))
                .thenReturn(Result.success(routeConfig(200L, 50L, "other")));
        when(vendorFeignClient.getById(40L)).thenReturn(Result.success(routeVendor(40L, "vendor-a")));
        when(vendorFeignClient.getById(50L)).thenReturn(Result.success(routeVendor(50L, "vendor-b")));
        when(apiInterfaceFeignClient.getContract(30L)).thenReturn(Result.success(new InterfaceContractDTO()));

        Object route = ReflectionTestUtils.invokeMethod(controller, "resolveApiRoute", "PERSONAL_QUERY");

        assertNotNull(route);
        assertNull(ReflectionTestUtils.invokeMethod(route, "fallbackConfig"));
    }

    @Test
    void shouldKeepExplicitValidFallbackRoute() {
        when(apiInterfaceFeignClient.getByInterfaceCode("PERSONAL_QUERY"))
                .thenReturn(Result.success(routeInterface(RoutingReadiness.READY, 200L)));
        when(vendorConfigFeignClient.getById(100L))
                .thenReturn(Result.success(routeConfig(100L, 40L, "personal")));
        when(vendorConfigFeignClient.getById(200L))
                .thenReturn(Result.success(routeConfig(200L, 50L, "personal")));
        when(vendorFeignClient.getById(40L)).thenReturn(Result.success(routeVendor(40L, "vendor-a")));
        when(vendorFeignClient.getById(50L)).thenReturn(Result.success(routeVendor(50L, "vendor-b")));
        when(apiInterfaceFeignClient.getContract(30L)).thenReturn(Result.success(new InterfaceContractDTO()));

        Object route = ReflectionTestUtils.invokeMethod(controller, "resolveApiRoute", "PERSONAL_QUERY");

        assertNotNull(route);
        Object fallback = ReflectionTestUtils.invokeMethod(route, "fallbackConfig");
        assertNotNull(fallback);
        assertEquals(Long.valueOf(200L), ReflectionTestUtils.invokeMethod(fallback, "getId"));
        assertEquals("vendor-b", ReflectionTestUtils.invokeMethod(route, "fallbackVendorCode"));
    }

    @Test
    void shouldRejectProductThatIsNotConfiguredForCaller() {
        ApiKey apiKey = activeApiKey();
        when(apiKeyService.getByKey("test-key")).thenReturn(apiKey);
        when(callerService.getById(20L)).thenReturn(activeCaller());
        when(callerProductService.getActiveProduct(20L, "loan-risk")).thenReturn(null);

        ResponseEntity<Result<OpenApiQueryRespVO>> response =
                controller.query("test-key", null, null, validRequest(), null);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("调用方未配置该产品", response.getBody().getMsg());
        verifyNoInteractions(apiKeyProductService);
    }

    @Test
    void shouldRejectProductThatIsNotGrantedToApiKey() {
        ApiKey apiKey = activeApiKey();
        CallerProduct product = new CallerProduct();
        product.setId(60L);
        product.setCallerId(20L);
        product.setProductCode("loan-risk");
        when(apiKeyService.getByKey("test-key")).thenReturn(apiKey);
        when(callerService.getById(20L)).thenReturn(activeCaller());
        when(callerProductService.getActiveProduct(20L, "loan-risk")).thenReturn(product);
        when(apiKeyProductService.hasProductPermission(10L, 60L)).thenReturn(false);

        ResponseEntity<Result<OpenApiQueryRespVO>> response =
                controller.query("test-key", null, null, validRequest(), null);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("API Key没有访问该产品的权限", response.getBody().getMsg());
    }

    @Test
    void shouldRejectNullBatchItemBeforeAuthentication() {
        OpenApiBatchQueryReqVO request = new OpenApiBatchQueryReqVO();
        request.setApiCode("WORLD_TIME");
        request.setProductCode("time-service");
        request.setSceneCode("internal-call");
        List<OpenApiBatchQueryReqVO.QueryItem> items = new ArrayList<>();
        items.add(null);
        request.setItems(items);

        ResponseEntity<Result<com.dataplatform.access.call.vo.OpenApiBatchQueryRespVO>> response =
                controller.batchQuery(null, null, null, request, null);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("items[0]"));
    }

    @Test
    void shouldRejectCacheWhenGrantDidNotApproveIt() {
        ApiKeyInterface grant = new ApiKeyInterface();
        grant.setCacheEnabled(false);

        String error = ReflectionTestUtils.invokeMethod(
                controller, "validateApprovedCachePolicy", true, 2, grant);

        assertEquals("当前接口授权未批准使用缓存", error);
    }

    @Test
    void shouldRejectCacheDaysAboveApprovedLimit() {
        ApiKeyInterface grant = new ApiKeyInterface();
        grant.setCacheEnabled(true);
        grant.setApprovedCacheDays(2);

        String error = ReflectionTestUtils.invokeMethod(
                controller, "validateApprovedCachePolicy", true, 10, grant);

        assertEquals("请求缓存时效超过审批上限2天", error);
    }

    @Test
    void shouldAllowShorterCacheDaysForSameApprovedInterface() {
        ApiKeyInterface grant = new ApiKeyInterface();
        grant.setCacheEnabled(true);
        grant.setApprovedCacheDays(10);

        String error = ReflectionTestUtils.invokeMethod(
                controller, "validateApprovedCachePolicy", true, 2, grant);

        assertEquals(null, error);
    }

    private ApiKey activeApiKey() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(10L);
        apiKey.setCallerId(20L);
        apiKey.setApiKey("test-key");
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        return apiKey;
    }

    private CallerInfo activeCaller() {
        CallerInfo caller = new CallerInfo();
        caller.setId(20L);
        caller.setTenantId(1L);
        caller.setStatus(CommonStatus.ACTIVE);
        return caller;
    }

    private OpenApiQueryReqVO validRequest() {
        OpenApiQueryReqVO request = new OpenApiQueryReqVO();
        request.setApiCode("PERSONAL_QUERY");
        request.setProductCode("loan-risk");
        request.setSceneCode("pre-loan-review");
        request.setParams(Map.of());
        return request;
    }

    private ApiInterfaceDTO routeInterface(RoutingReadiness readiness, Long fallbackConfigId) {
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(30L);
        apiInterface.setRoutingReadiness(readiness);
        apiInterface.setPrimaryVendorConfigId(100L);
        apiInterface.setFallbackVendorConfigId(fallbackConfigId);
        return apiInterface;
    }

    private VendorConfigDTO routeConfig(Long id, Long vendorId, String dataTypeCode) {
        VendorConfigDTO config = new VendorConfigDTO();
        config.setId(id);
        config.setVendorId(vendorId);
        config.setInterfaceId(30L);
        config.setStatus(StatusConstants.ACTIVE);
        config.setDataTypeCode(dataTypeCode);
        return config;
    }

    private VendorInfoDTO routeVendor(Long id, String vendorCode) {
        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(id);
        vendor.setVendorCode(vendorCode);
        vendor.setStatus(StatusConstants.ACTIVE);
        return vendor;
    }
}
