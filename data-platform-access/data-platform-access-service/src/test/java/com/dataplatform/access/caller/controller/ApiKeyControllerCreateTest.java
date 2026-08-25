package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyProvisioningService;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.access.caller.vo.ApiKeyCreateReqVO;
import com.dataplatform.common.result.Result;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyControllerCreateTest {

    private ApiKeyProvisioningService apiKeyProvisioningService;
    private CallerService callerService;
    private CallerProductService callerProductService;
    private ApiKeyController controller;

    @BeforeEach
    void setUp() {
        apiKeyProvisioningService = mock(ApiKeyProvisioningService.class);
        callerService = mock(CallerService.class);
        callerProductService = mock(CallerProductService.class);
        controller = new ApiKeyController();
        ReflectionTestUtils.setField(controller, "apiKeyProvisioningService", apiKeyProvisioningService);
        ReflectionTestUtils.setField(controller, "callerService", callerService);
        ReflectionTestUtils.setField(controller, "callerProductService", callerProductService);
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertEquals(400, controller.create(null).getStatusCode().value());

        ApiKeyCreateReqVO missingCaller = request(null, "key", List.of(1L));
        assertEquals(400, controller.create(missingCaller).getStatusCode().value());

        ApiKeyCreateReqVO blankName = request(1L, "   ", List.of(1L));
        assertEquals(400, controller.create(blankName).getStatusCode().value());

        verifyNoInteractions(apiKeyProvisioningService, callerService, callerProductService);
    }

    @Test
    void requiresAtLeastOneProduct() {
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        when(callerService.getById(1L)).thenReturn(caller);

        ResponseEntity<Result<ApiKey>> response = controller.create(request(1L, "key", List.of()));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("请至少选择一个产品", response.getBody().getMessage());
        verifyNoInteractions(apiKeyProvisioningService, callerProductService);
    }

    @Test
    void rejectsProductsFromAnotherCallerOrInactiveProducts() {
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        when(callerService.getById(1L)).thenReturn(caller);

        CallerProduct foreignProduct = product(10L, 2L, "active");
        when(callerProductService.listByIds(List.of(10L))).thenReturn(List.of(foreignProduct));
        assertEquals(400, controller.create(request(1L, "key", List.of(10L))).getStatusCode().value());

        CallerProduct inactiveProduct = product(11L, 1L, "inactive");
        when(callerProductService.listByIds(List.of(11L))).thenReturn(List.of(inactiveProduct));
        ResponseEntity<Result<ApiKey>> response = controller.create(request(1L, "key", List.of(11L)));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("只能授权启用状态的产品", response.getBody().getMessage());
        verifyNoInteractions(apiKeyProvisioningService);
    }

    @Test
    void createsKeyAndProductGrantsTogether() {
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        CallerProduct product = product(10L, 1L, "active");
        ApiKey created = new ApiKey();
        created.setId(7L);
        when(callerService.getById(1L)).thenReturn(caller);
        when(callerProductService.listByIds(List.of(10L))).thenReturn(List.of(product));
        when(apiKeyProvisioningService.create(1L, "key", List.of(10L))).thenReturn(created);

        ResponseEntity<Result<ApiKey>> response =
                controller.create(request(1L, "  key  ", List.of(10L)));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(7L, response.getBody().getData().getId());
        verify(apiKeyProvisioningService).create(1L, "key", List.of(10L));
    }

    private ApiKeyCreateReqVO request(Long callerId, String name, List<Long> productIds) {
        ApiKeyCreateReqVO request = new ApiKeyCreateReqVO();
        request.setCallerId(callerId);
        request.setName(name);
        request.setProductIds(productIds);
        return request;
    }

    private CallerProduct product(Long id, Long callerId, String status) {
        CallerProduct product = new CallerProduct();
        product.setId(id);
        product.setCallerId(callerId);
        product.setStatus(status);
        return product;
    }
}
