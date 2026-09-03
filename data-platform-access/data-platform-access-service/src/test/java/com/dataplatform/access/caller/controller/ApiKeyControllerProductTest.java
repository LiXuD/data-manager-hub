package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyProductService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.util.UserContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyControllerProductTest {

    private ApiKeyService apiKeyService;
    private ApiKeyProductService apiKeyProductService;
    private CallerProductService callerProductService;
    private CallerService callerService;
    private ApiKeyController controller;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        apiKeyProductService = mock(ApiKeyProductService.class);
        callerProductService = mock(CallerProductService.class);
        callerService = mock(CallerService.class);
        controller = new ApiKeyController();
        ReflectionTestUtils.setField(controller, "apiKeyService", apiKeyService);
        ReflectionTestUtils.setField(controller, "apiKeyProductService", apiKeyProductService);
        ReflectionTestUtils.setField(controller, "callerProductService", callerProductService);
        ReflectionTestUtils.setField(controller, "callerService", callerService);
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        when(callerService.getById(1L)).thenReturn(caller);
    }

    @Test
    void assignsOnlyActiveProductsFromTheKeysCaller() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(9L);
        apiKey.setCallerId(1L);
        CallerProduct activeProduct = product(10L, 1L, "active");
        when(apiKeyService.getById(9L)).thenReturn(apiKey);
        when(callerProductService.listByIds(List.of(10L))).thenReturn(List.of(activeProduct));

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            assertEquals(200, controller.assignProducts(9L, List.of(10L)).getStatusCode().value());
        }

        verify(apiKeyProductService).assignProducts(9L, List.of(10L));
    }

    @Test
    void rejectsInactiveProductWithoutReplacingExistingGrants() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(9L);
        apiKey.setCallerId(1L);
        CallerProduct inactiveProduct = product(10L, 1L, "inactive");
        when(apiKeyService.getById(9L)).thenReturn(apiKey);
        when(callerProductService.listByIds(List.of(10L))).thenReturn(List.of(inactiveProduct));

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            assertEquals(400, controller.assignProducts(9L, List.of(10L)).getStatusCode().value());
        }

        verifyNoInteractions(apiKeyProductService);
    }

    private CallerProduct product(Long id, Long callerId, String status) {
        CallerProduct product = new CallerProduct();
        product.setId(id);
        product.setCallerId(callerId);
        product.setStatus(status);
        return product;
    }
}
