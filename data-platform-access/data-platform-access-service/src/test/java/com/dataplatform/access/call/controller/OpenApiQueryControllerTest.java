package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.access.call.service.GrayVendorResolver;
import com.dataplatform.access.call.service.OpenApiQueryService;
import com.dataplatform.access.call.service.OpenApiQueryService.OpenApiCallContext;
import com.dataplatform.access.call.service.RateLimitService;
import com.dataplatform.access.call.vo.OpenApiQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.access.caller.entity.ApiKey;
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
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorFeignClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    private VendorConfigFeignClient vendorConfigFeignClient;
    private VendorFeignClient vendorFeignClient;
    private GrayVendorResolver grayVendorResolver;
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
        vendorConfigFeignClient = mock(VendorConfigFeignClient.class);
        vendorFeignClient = mock(VendorFeignClient.class);
        grayVendorResolver = mock(GrayVendorResolver.class);
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
                vendorFeignClient,
                grayVendorResolver);
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
        when(callSceneService.getActiveScene("pre-loan-review")).thenReturn(scene);

        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(30L);
        apiInterface.setInterfaceCode("PERSONAL_QUERY");
        when(apiInterfaceFeignClient.getByInterfaceCode("PERSONAL_QUERY")).thenReturn(Result.success(apiInterface));
        when(apiKeyInterfaceService.hasInterfacePermission(10L, 30L)).thenReturn(true);

        VendorConfigDTO config = new VendorConfigDTO();
        config.setVendorId(40L);
        config.setDataTypeCode("personal");
        when(vendorConfigFeignClient.list(null, null, 30L, StatusConstants.ACTIVE))
                .thenReturn(Result.success(List.of(config)));

        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(40L);
        vendor.setVendorCode("vendor-a");
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

        Result<OpenApiQueryRespVO> result = controller.query("test-key", null, "trace-1", request, null);

        assertEquals(200, result.getCode());
        assertEquals("client-req-1", result.getData().getRequestId());
        assertEquals("req_platform_1", result.getData().getPlatformRequestId());
        assertEquals("PERSONAL_QUERY", result.getData().getApiCode());
        assertTrue(result.getData().getSuccess());
        assertEquals(99, result.getData().getData().get("score"));
        assertEquals(12L, result.getData().getLatency());
        ArgumentCaptor<OpenApiCallContext> contextCaptor = ArgumentCaptor.forClass(OpenApiCallContext.class);
        verify(openApiQueryService).query(contextCaptor.capture());
        assertEquals("trace-1", contextCaptor.getValue().getTraceId());
    }

    @Test
    void shouldRejectMissingApiCode() {
        Result<OpenApiQueryRespVO> result = controller.query("test-key", null, null, new OpenApiQueryReqVO(), null);

        assertEquals(400, result.getCode());
    }
}
