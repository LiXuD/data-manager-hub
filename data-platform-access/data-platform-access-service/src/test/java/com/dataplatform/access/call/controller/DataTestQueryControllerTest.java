package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.vo.DataTestQueryReqVO;
import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.access.call.vo.DataTestOptionsVO;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyProductService;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CurrentUserApiKeyOptionService;
import com.dataplatform.access.caller.vo.CurrentUserApiKeyOptionVO;
import com.dataplatform.access.caller.vo.CurrentUserApiKeyOptionsVO;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import com.dataplatform.common.util.UserContext;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataTestQueryControllerTest {

    private final CurrentUserApiKeyOptionService optionService =
            mock(CurrentUserApiKeyOptionService.class);
    private final ApiKeyInterfaceService apiKeyInterfaceService = mock(ApiKeyInterfaceService.class);
    private final ApiKeyProductService apiKeyProductService = mock(ApiKeyProductService.class);
    private final CallerProductService callerProductService = mock(CallerProductService.class);
    private final ApiInterfaceFeignClient apiInterfaceFeignClient = mock(ApiInterfaceFeignClient.class);
    private final VendorConfigInternalFeignClient vendorConfigInternalFeignClient =
            mock(VendorConfigInternalFeignClient.class);
    private final CallSceneService callSceneService = mock(CallSceneService.class);
    private final OpenApiQueryController openApiQueryController =
            mock(OpenApiQueryController.class);
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);
    private final DataTestQueryController controller =
            new DataTestQueryController(optionService, apiKeyInterfaceService, apiKeyProductService,
                    callerProductService, apiInterfaceFeignClient, vendorConfigInternalFeignClient,
                    callSceneService, openApiQueryController);

    @Test
    void rejectsMissingOrUnauthorizedApiKey() {
        assertEquals(400, controller.queryForUser(10L, 20L, null, null, httpRequest)
                .getStatusCode().value());

        DataTestQueryReqVO request = request(99L);
        when(optionService.findUsableKey(10L, 20L, 99L)).thenReturn(null);
        assertEquals(403, controller.queryForUser(10L, 20L, null, request, httpRequest)
                .getStatusCode().value());

        verifyNoInteractions(openApiQueryController);
    }

    @Test
    void delegatesToCanonicalOpenApiFlowWithServerResolvedKey() {
        DataTestQueryReqVO request = request(11L);
        ApiKey apiKey = new ApiKey();
        apiKey.setId(11L);
        apiKey.setApiKey("dp_live_secret");
        when(optionService.findUsableKey(10L, 20L, 11L)).thenReturn(apiKey);

        ResponseEntity<Result<OpenApiQueryRespVO>> expected =
                ResponseEntity.ok(Result.success(new OpenApiQueryRespVO()));
        when(openApiQueryController.query(
                "dp_live_secret", null, "trace-1", request, httpRequest))
                .thenReturn(expected);

        ResponseEntity<Result<OpenApiQueryRespVO>> actual = controller.queryForUser(
                10L, 20L, "trace-1", request, httpRequest);

        assertEquals(expected, actual);
        verify(openApiQueryController).query(
                "dp_live_secret", null, "trace-1", request, httpRequest);
    }

    @Test
    void readsContractOnlyForUsableKeyWithEffectiveGrant() {
        when(optionService.findUsableKey(10L, 20L, 11L)).thenReturn(apiKey(11L));
        when(apiKeyInterfaceService.findEffectiveGrant(11L, 30L))
                .thenReturn(new com.dataplatform.access.caller.entity.ApiKeyInterface());
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(30L);
        apiInterface.setStatus("active");
        when(apiInterfaceFeignClient.getById(30L)).thenReturn(Result.success(apiInterface));
        InterfaceContractDTO contract = new InterfaceContractDTO();
        when(apiInterfaceFeignClient.getContract(30L)).thenReturn(Result.success(contract));

        ResponseEntity<Result<InterfaceContractDTO>> response = controller.contractForUser(10L, 20L, 11L, 30L);

        assertEquals(200, response.getStatusCode().value());
        verify(apiInterfaceFeignClient).getById(30L);
        verify(apiInterfaceFeignClient).getContract(30L);
    }

    @Test
    void refusesContractForDisabledInterfaceBeforeReadingContract() {
        when(optionService.findUsableKey(10L, 20L, 11L)).thenReturn(apiKey(11L));
        when(apiKeyInterfaceService.findEffectiveGrant(11L, 30L))
                .thenReturn(new com.dataplatform.access.caller.entity.ApiKeyInterface());
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(30L);
        apiInterface.setStatus("inactive");
        when(apiInterfaceFeignClient.getById(30L)).thenReturn(Result.success(apiInterface));

        assertEquals(404, controller.contractForUser(10L, 20L, 11L, 30L)
                .getStatusCode().value());
        verify(apiInterfaceFeignClient, org.mockito.Mockito.never()).getContract(30L);
    }

    @Test
    void rejectsContractWithoutGrantBeforeCrossDomainRead() {
        when(optionService.findUsableKey(10L, 20L, 11L)).thenReturn(apiKey(11L));
        when(apiKeyInterfaceService.findEffectiveGrant(11L, 30L)).thenReturn(null);

        assertEquals(403, controller.contractForUser(10L, 20L, 11L, 30L).getStatusCode().value());
        verifyNoInteractions(apiInterfaceFeignClient);
    }

    @Test
    void returnsOnlyActiveConfiguredOptionsWithoutUsingManagementEndpoints() {
        VendorConfigDTO config = new VendorConfigDTO();
        config.setId(41L);
        config.setVendorId(7L);
        config.setVendorName("Example Vendor");
        config.setDataTypeId(8L);
        config.setDataTypeCode("company");
        config.setDataTypeName("企业");
        config.setInterfaceId(9L);

        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(9L);
        apiInterface.setInterfaceCode("company.query");
        apiInterface.setInterfaceName("企业查询");
        apiInterface.setStatus("active");

        CallScene scene = new CallScene();
        scene.setId(10L);
        scene.setSceneCode("browser");
        scene.setSceneName("浏览器测试");
        scene.setStatus("active");
        scene.setDeleted(false);
        scene.setTenantId(20L);

        when(vendorConfigInternalFeignClient.list(null, null, null, "active"))
                .thenReturn(Result.success(List.of(config)));
        when(apiInterfaceFeignClient.getOptions(null)).thenReturn(Result.success(List.of(apiInterface)));
        ApiKeyInterface grant = new ApiKeyInterface();
        grant.setApiKeyId(11L);
        grant.setInterfaceId(9L);
        when(optionService.listOptions(10L, 20L)).thenReturn(new CurrentUserApiKeyOptionsVO(
                true, List.of(new CurrentUserApiKeyOptionVO(11L, 12L, "caller", "Caller", "key", "dp****ret"))));
        when(apiKeyInterfaceService.listEffectiveGrants(11L)).thenReturn(List.of(grant));
        when(callSceneService.listManagedScenes(20L)).thenReturn(List.of(scene));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("api-permission:view")).thenReturn(true);
            userContext.when(UserContext::getCurrentUserId).thenReturn(10L);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);
            ResponseEntity<Result<DataTestOptionsVO>> response = controller.options(null);

            assertEquals(200, response.getStatusCode().value());
            assertEquals(1, response.getBody().getData().vendors().size());
            assertEquals(1, response.getBody().getData().dataTypes().size());
            assertEquals(1, response.getBody().getData().interfaces().size());
            assertEquals(1, response.getBody().getData().scenes().size());
            assertEquals(0, response.getBody().getData().products().size());
        }
    }

    @Test
    void returnsOnlyActiveProductsGrantedToTheSelectedApiKey() {
        ApiKey key = apiKey(11L);
        key.setCallerId(12L);
        when(optionService.findUsableKey(10L, 20L, 11L)).thenReturn(key);
        ApiKeyInterface grant = new ApiKeyInterface();
        grant.setApiKeyId(11L);
        grant.setInterfaceId(9L);
        when(apiKeyInterfaceService.listEffectiveGrants(11L)).thenReturn(List.of(grant));

        VendorConfigDTO config = new VendorConfigDTO();
        config.setId(41L);
        config.setVendorId(7L);
        config.setVendorName("Example Vendor");
        config.setDataTypeId(8L);
        config.setDataTypeCode("company");
        config.setDataTypeName("企业");
        config.setInterfaceId(9L);
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(9L);
        apiInterface.setInterfaceCode("company.query");
        apiInterface.setInterfaceName("企业查询");
        apiInterface.setStatus("active");
        CallScene scene = new CallScene();
        scene.setId(10L);
        scene.setSceneCode("browser");
        scene.setSceneName("浏览器测试");
        scene.setStatus("active");
        scene.setDeleted(false);
        scene.setTenantId(20L);

        CallerProduct active = product(21L, 12L, "active");
        active.setProductCode("credit");
        active.setProductName("信贷产品");
        CallerProduct inactive = product(22L, 12L, "inactive");
        when(apiKeyProductService.getProductIdsByApiKeyId(11L)).thenReturn(List.of(21L, 22L));
        when(callerProductService.listByCaller(12L)).thenReturn(List.of(active, inactive));
        when(vendorConfigInternalFeignClient.list(null, null, null, "active"))
                .thenReturn(Result.success(List.of(config)));
        when(apiInterfaceFeignClient.getOptions(null)).thenReturn(Result.success(List.of(apiInterface)));
        when(callSceneService.listManagedScenes(20L)).thenReturn(List.of(scene));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("api-permission:view")).thenReturn(true);
            userContext.when(UserContext::getCurrentUserId).thenReturn(10L);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);

            ResponseEntity<Result<DataTestOptionsVO>> response = controller.options(11L);

            assertEquals(200, response.getStatusCode().value());
            assertEquals(1, response.getBody().getData().products().size());
            assertEquals("credit", response.getBody().getData().products().get(0).productCode());
        }
    }

    private CallerProduct product(Long id, Long callerId, String status) {
        CallerProduct product = new CallerProduct();
        product.setId(id);
        product.setCallerId(callerId);
        product.setStatus(status);
        return product;
    }

    @Test
    void rejectsExplicitUnusableKeyBeforeLoadingOptionCatalog() {
        when(optionService.findUsableKey(10L, 20L, 99L)).thenReturn(null);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("api-permission:view")).thenReturn(true);
            userContext.when(UserContext::getCurrentUserId).thenReturn(10L);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);

            assertEquals(403, controller.options(99L).getStatusCode().value());
        }
        verifyNoInteractions(vendorConfigInternalFeignClient, apiInterfaceFeignClient, callSceneService);
    }

    @Test
    void returnsBadGatewayWhenOptionContractIsUnavailable() {
        when(optionService.listOptions(10L, 20L))
                .thenReturn(new CurrentUserApiKeyOptionsVO(false, List.of()));
        when(vendorConfigInternalFeignClient.list(null, null, null, "active")).thenReturn(null);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("api-permission:view")).thenReturn(true);
            userContext.when(UserContext::getCurrentUserId).thenReturn(10L);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);
            assertEquals(502, controller.options(null).getStatusCode().value());
        }
        verifyNoInteractions(callSceneService);
    }

    @Test
    void mapsIdentityFailureDuringContractAuthorizationToBadGateway() {
        when(optionService.findUsableKey(10L, 20L, 11L))
                .thenThrow(new IllegalStateException("identity unavailable"));

        ResponseEntity<Result<InterfaceContractDTO>> response =
                controller.contractForUser(10L, 20L, 11L, 30L);

        assertEquals(502, response.getStatusCode().value());
    }

    @Test
    void mapsIdentityFailureDuringQueryAuthorizationToBadGateway() {
        when(optionService.findUsableKey(10L, 20L, 11L))
                .thenThrow(new IllegalStateException("identity unavailable"));

        ResponseEntity<Result<OpenApiQueryRespVO>> response = controller.queryForUser(
                10L, 20L, null, request(11L), httpRequest);

        assertEquals(502, response.getStatusCode().value());
        verifyNoInteractions(openApiQueryController);
    }

    private DataTestQueryReqVO request(Long apiKeyId) {
        DataTestQueryReqVO request = new DataTestQueryReqVO();
        request.setApiKeyId(apiKeyId);
        request.setApiCode("identity-query");
        request.setProductCode("product-a");
        request.setSceneCode("scene-a");
        return request;
    }

    private ApiKey apiKey(Long id) {
        ApiKey key = new ApiKey();
        key.setId(id);
        key.setApiKey("dp_live_secret");
        return key;
    }
}
