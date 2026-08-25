package com.dataplatform.access.connector.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.api.dto.ConnectorMigrationObservationReqDTO;
import com.dataplatform.access.connector.api.feign.ConnectorMigrationObservationInternalFeignClient;
import com.dataplatform.access.connector.service.ConnectorMigrationObservationService;
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

class ConnectorMigrationObservationInternalControllerTest {

    @Test
    void controllerAndFeignContractResolveTheSameScopedEndpoint() throws Exception {
        RequestMapping controllerPath = ConnectorMigrationObservationInternalController.class
                .getAnnotation(RequestMapping.class);
        FeignClient feign = ConnectorMigrationObservationInternalFeignClient.class
                .getAnnotation(FeignClient.class);
        Method contractMethod = ConnectorMigrationObservationInternalFeignClient.class
                .getMethod("observation", ConnectorMigrationObservationReqDTO.class);
        Method controllerMethod = ConnectorMigrationObservationInternalController.class
                .getMethod("observation", ConnectorMigrationObservationReqDTO.class);

        assertEquals(feign.path(), controllerPath.value()[0]);
        assertEquals("/observation", contractMethod.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("access:connector-runtime:read",
                controllerMethod.getAnnotation(InternalScope.class).value());
    }

    @Test
    void onlyAcceptsServiceJwtWithObservationReadScope() throws Exception {
        assertFalse(authorize(Set.of("access:connector-runtime:test")));
        assertTrue(authorize(Set.of("access:connector-runtime:read")));
    }

    private boolean authorize(Set<String> scopes) throws Exception {
        InternalJwtService jwtService = mock(InternalJwtService.class);
        when(jwtService.verify("token")).thenReturn(new InternalPrincipal(
                "data-platform-masterdata", "data-platform-access", scopes));
        InternalAuthenticationInterceptor interceptor = new InternalAuthenticationInterceptor(
                jwtService, new ObjectMapper(), mock(BeanFactory.class));
        ConnectorMigrationObservationInternalController controller =
                new ConnectorMigrationObservationInternalController(
                        mock(ConnectorMigrationObservationService.class));
        HandlerMethod handler = new HandlerMethod(controller,
                ConnectorMigrationObservationInternalController.class
                        .getMethod("observation", ConnectorMigrationObservationReqDTO.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        return interceptor.preHandle(request, new MockHttpServletResponse(), handler);
    }
}
