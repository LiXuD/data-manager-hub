package com.dataplatform.access.connector.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.access.connector.service.VendorConnectorControlledTestService;
import com.dataplatform.common.security.InternalAuthenticationInterceptor;
import com.dataplatform.common.security.InternalJwtService;
import com.dataplatform.common.security.InternalPrincipal;
import com.dataplatform.common.security.InternalScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

class VendorConnectorRuntimeInternalControllerTest {

    @Test
    void controllerAndFeignContractResolveTheSameEndpoint() throws Exception {
        RequestMapping controllerPath = VendorConnectorRuntimeInternalController.class
                .getAnnotation(RequestMapping.class);
        FeignClient feign = VendorConnectorRuntimeInternalFeignClient.class.getAnnotation(FeignClient.class);
        Method contractMethod = VendorConnectorRuntimeInternalFeignClient.class
                .getMethod("test", VendorConnectorTestReqDTO.class);
        Method controllerMethod = VendorConnectorRuntimeInternalController.class
                .getMethod("test", VendorConnectorTestReqDTO.class);

        assertEquals(feign.path(), controllerPath.value()[0]);
        assertEquals("/test", contractMethod.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("access:connector-runtime:test",
                controllerMethod.getAnnotation(InternalScope.class).value());
    }

    @Test
    void rejectsServiceJwtWithoutDedicatedTestScope() throws Exception {
        InternalJwtService jwtService = mock(InternalJwtService.class);
        when(jwtService.verify("token")).thenReturn(new InternalPrincipal(
                "data-platform-masterdata", "data-platform-access",
                Set.of("access:connector-runtime:read")));
        InternalAuthenticationInterceptor interceptor = new InternalAuthenticationInterceptor(
                jwtService, new ObjectMapper(), mock(BeanFactory.class));
        VendorConnectorRuntimeInternalController controller = new VendorConnectorRuntimeInternalController(
                mock(VendorConnectorControlledTestService.class));
        HandlerMethod handler = new HandlerMethod(controller,
                VendorConnectorRuntimeInternalController.class
                        .getMethod("test", VendorConnectorTestReqDTO.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(403, response.getStatus());
    }

    @Test
    void acceptsServiceJwtWithDedicatedTestScope() throws Exception {
        InternalJwtService jwtService = mock(InternalJwtService.class);
        when(jwtService.verify("token")).thenReturn(new InternalPrincipal(
                "data-platform-masterdata", "data-platform-access",
                Set.of("access:connector-runtime:test")));
        InternalAuthenticationInterceptor interceptor = new InternalAuthenticationInterceptor(
                jwtService, new ObjectMapper(), mock(BeanFactory.class));
        VendorConnectorRuntimeInternalController controller = new VendorConnectorRuntimeInternalController(
                mock(VendorConnectorControlledTestService.class));
        HandlerMethod handler = new HandlerMethod(controller,
                VendorConnectorRuntimeInternalController.class
                        .getMethod("test", VendorConnectorTestReqDTO.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }
}
