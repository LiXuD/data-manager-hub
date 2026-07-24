package com.dataplatform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class InternalAuthenticationInterceptorTest {

    private InternalJwtService jwtService;
    private InternalAuthenticationInterceptor interceptor;
    private BeanFactory beanFactory;

    @BeforeEach
    void setUp() {
        jwtService = mock(InternalJwtService.class);
        beanFactory = mock(BeanFactory.class);
        interceptor = new InternalAuthenticationInterceptor(jwtService, new ObjectMapper(), beanFactory);
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

    @Test
    void resolvesScopeFromTargetClassBehindJdkProxy() throws Exception {
        when(jwtService.verify("token")).thenReturn(
                new InternalPrincipal("data-platform-access", "data-platform-identity",
                        Set.of("identity:access:read")));
        ProxyFactory proxyFactory = new ProxyFactory(new ClassScopedHandler());
        proxyFactory.setInterfaces(TestContract.class);
        Object proxy = proxyFactory.getProxy();
        HandlerMethod handler = new HandlerMethod(proxy, TestContract.class.getMethod("read"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void resolvesScopeWhenHandlerStillReferencesBeanName() throws Exception {
        when(jwtService.verify("token")).thenReturn(
                new InternalPrincipal("data-platform-access", "data-platform-identity",
                        Set.of("identity:access:read")));
        DefaultListableBeanFactory namedBeanFactory = new DefaultListableBeanFactory();
        namedBeanFactory.registerSingleton("identityAccessInternalController", new ClassScopedHandler());
        InternalAuthenticationInterceptor namedBeanInterceptor =
                new InternalAuthenticationInterceptor(jwtService, new ObjectMapper(), namedBeanFactory);
        HandlerMethod handler = new HandlerMethod(
                "identityAccessInternalController", namedBeanFactory, TestContract.class.getMethod("read"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");

        assertTrue(namedBeanInterceptor.preHandle(request, new MockHttpServletResponse(), handler));
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

    private interface TestContract {
        void read();
    }

    @InternalScope("identity:access:read")
    private static class ClassScopedHandler implements TestContract {
        @Override
        public void read() {
        }
    }
}
