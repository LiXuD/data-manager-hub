package com.dataplatform.access.caller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mockStatic;

class CallerProductControllerUpdateTest {

    private CallerService callerService;
    private CallerProductService callerProductService;
    private CallerProductController controller;

    @BeforeEach
    void setUp() {
        callerService = mock(CallerService.class);
        callerProductService = mock(CallerProductService.class);
        controller = new CallerProductController(callerService, callerProductService);
    }

    @Test
    void updatesReuseScopeForProductOwnedByCaller() {
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        CallerProduct request = product("信贷风控", "GLOBAL", "active");
        CallerProduct updated = product("信贷风控", "GLOBAL", "active");
        updated.setId(11L);
        updated.setCallerId(1L);
        when(callerService.getById(1L)).thenReturn(caller);
        when(callerProductService.updateProduct(1L, 11L, request)).thenReturn(updated);

        var response = responseAsAdmin(() -> controller.update(1L, 11L, request));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("GLOBAL", response.getBody().getData().getCacheScope());
        verify(callerProductService).updateProduct(1L, 11L, request);
    }

    @Test
    void rejectsInvalidReuseScopeBeforeWriting() {
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        when(callerService.getById(1L)).thenReturn(caller);
        CallerProduct request = product("信贷风控", "TENANT", "active");

        var response = responseAsAdmin(() -> controller.update(1L, 11L, request));

        assertEquals(400, response.getStatusCode().value());
        verify(callerProductService, never()).updateProduct(1L, 11L, request);
    }

    @Test
    void rejectsProductFromAnotherCaller() {
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        when(callerService.getById(1L)).thenReturn(caller);
        CallerProduct request = product("信贷风控", "CALLER", "inactive");
        when(callerProductService.updateProduct(1L, 99L, request)).thenReturn(null);

        var response = responseAsAdmin(() -> controller.update(1L, 99L, request));

        assertEquals(404, response.getStatusCode().value());
    }

    private CallerProduct product(String name, String cacheScope, String status) {
        CallerProduct product = new CallerProduct();
        product.setProductName(name);
        product.setCacheScope(cacheScope);
        product.setStatus(status);
        return product;
    }

    private <T> T responseAsAdmin(java.util.function.Supplier<T> invocation) {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            return invocation.get();
        }
    }
}
