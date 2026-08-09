package com.dataplatform.billing.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.billing.api.dto.ConnectorBillingObservationReqDTO;
import com.dataplatform.billing.api.feign.ConnectorBillingObservationInternalFeignClient;
import com.dataplatform.billing.service.ConnectorBillingObservationService;
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

class ConnectorBillingObservationInternalControllerTest {

    @Test
    void controllerAndFeignContractResolveTheSameScopedEndpoint() throws Exception {
        RequestMapping controllerPath = ConnectorBillingObservationInternalController.class
                .getAnnotation(RequestMapping.class);
        FeignClient feign = ConnectorBillingObservationInternalFeignClient.class
                .getAnnotation(FeignClient.class);
        Method contractMethod = ConnectorBillingObservationInternalFeignClient.class
                .getMethod("observation", ConnectorBillingObservationReqDTO.class);
        Method controllerMethod = ConnectorBillingObservationInternalController.class
                .getMethod("observation", ConnectorBillingObservationReqDTO.class);

        assertEquals(feign.path(), controllerPath.value()[0]);
        assertEquals("/observation", contractMethod.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("billing:connector-observation:read",
                controllerMethod.getAnnotation(InternalScope.class).value());
    }

    @Test
    void onlyAcceptsServiceJwtWithObservationReadScope() throws Exception {
        assertFalse(authorize(Set.of("billing:charge")));
        assertTrue(authorize(Set.of("billing:connector-observation:read")));
    }

    private boolean authorize(Set<String> scopes) throws Exception {
        InternalJwtService jwtService = mock(InternalJwtService.class);
        when(jwtService.verify("token")).thenReturn(new InternalPrincipal(
                "data-platform-masterdata", "data-platform-billing", scopes));
        InternalAuthenticationInterceptor interceptor = new InternalAuthenticationInterceptor(
                jwtService, new ObjectMapper(), mock(BeanFactory.class));
        ConnectorBillingObservationInternalController controller =
                new ConnectorBillingObservationInternalController(
                        mock(ConnectorBillingObservationService.class));
        HandlerMethod handler = new HandlerMethod(controller,
                ConnectorBillingObservationInternalController.class
                        .getMethod("observation", ConnectorBillingObservationReqDTO.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        return interceptor.preHandle(request, new MockHttpServletResponse(), handler);
    }
}
