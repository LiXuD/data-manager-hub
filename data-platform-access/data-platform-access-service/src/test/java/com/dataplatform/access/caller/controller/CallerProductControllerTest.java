package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.service.CallerProductService;
import com.dataplatform.access.caller.service.CallerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallerProductControllerTest {

    @Test
    void createsProductUnderSelectedCaller() {
        CallerService callerService = mock(CallerService.class);
        CallerProductService callerProductService = mock(CallerProductService.class);
        CallerProductController controller = new CallerProductController(callerService, callerProductService);
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        CallerProduct request = new CallerProduct();
        request.setProductCode("loan-risk");
        request.setProductName("信贷风控");
        when(callerService.getById(1L)).thenReturn(caller);
        when(callerProductService.saveProduct(1L, request)).thenReturn(request);

        assertEquals(200, controller.create(1L, request).getStatusCode().value());

        verify(callerProductService).saveProduct(1L, request);
    }
}
