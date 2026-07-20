package com.dataplatform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class InternalAuthenticationInterceptorTest {

    private InternalJwtService jwtService;
    private InternalAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = mock(InternalJwtService.class);
        interceptor = new InternalAuthenticationInterceptor(jwtService, new ObjectMapper());
    }

    @Test
    void rejectsMissingToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, securedHandler()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsTokenWithoutRequiredScope() throws Exception {
        when(jwtService.verify("token")).thenReturn(
                new InternalPrincipal("data-platform-access", "data-platform-masterdata",
                        Set.of("masterdata:read")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, securedHandler()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsInternalHandlerWithoutDeclaredScope() throws Exception {
        when(jwtService.verify("token")).thenReturn(
                new InternalPrincipal("data-platform-access", "data-platform-masterdata",
                        Set.of("masterdata:read")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new TestHandler(),
                TestHandler.class.getDeclaredMethod("unsecured"));

        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(403, response.getStatus());
    }

    private HandlerMethod securedHandler() throws NoSuchMethodException {
        return new HandlerMethod(new TestHandler(), TestHandler.class.getDeclaredMethod("secured"));
    }

    private static class TestHandler {
        @InternalScope("masterdata:vendor-secret:read")
        public void secured() {
        }

        public void unsecured() {
        }
    }
}
