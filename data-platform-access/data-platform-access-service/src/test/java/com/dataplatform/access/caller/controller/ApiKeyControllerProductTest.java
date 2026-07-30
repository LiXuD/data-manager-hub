package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.ApiKeyProductService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerProductService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyControllerProductTest {

    private ApiKeyService apiKeyService;
    private ApiKeyProductService apiKeyProductService;
    private CallerProductService callerProductService;
    private ApiKeyController controller;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        apiKeyProductService = mock(ApiKeyProductService.class);
        callerProductService = mock(CallerProductService.class);
        controller = new ApiKeyController();
        ReflectionTestUtils.setField(controller, "apiKeyService", apiKeyService);
        ReflectionTestUtils.setField(controller, "apiKeyProductService", apiKeyProductService);
        ReflectionTestUtils.setField(controller, "callerProductService", callerProductService);
    }

    @Test
    void assignsOnlyActiveProductsFromTheKeysCaller() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(9L);
        apiKey.setCallerId(1L);
        CallerProduct activeProduct = product(10L, 1L, "active");
        when(apiKeyService.getById(9L)).thenReturn(apiKey);
        when(callerProductService.listByIds(List.of(10L))).thenReturn(List.of(activeProduct));

        assertEquals(200, controller.assignProducts(9L, List.of(10L)).getStatusCode().value());

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

        assertEquals(400, controller.assignProducts(9L, List.of(10L)).getStatusCode().value());

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
