package com.dataplatform.common.interceptor;

import com.dataplatform.common.util.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.mockStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor();

    @Test
    void shouldAllowExactLoginPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/auth/login");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldNotBypassAuthenticationWhenPublicPathIsOnlyASubstring() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/private/auth/login/details");

        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldRejectAuthenticatedUserWithoutRoutePermission() throws Exception {
        MockHttpServletRequest request = request("GET", "/vendor/42");
        request.addHeader("Authorization", "Bearer session-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (var context = mockStatic(UserContext.class)) {
            context.when(UserContext::isLoggedIn).thenReturn(true);
            assertFalse(interceptor.preHandle(request, response, new Object()));
        }

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldAllowExactPermissionAndSystemCapability() throws Exception {
        MockHttpServletRequest request = request("GET", "/vendor/42");
        request.addHeader("Authorization", "Bearer session-token");

        try (var context = mockStatic(UserContext.class)) {
            context.when(UserContext::isLoggedIn).thenReturn(true);
            context.when(() -> UserContext.hasPermission("vendor:view")).thenReturn(true);
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }

        try (var context = mockStatic(UserContext.class)) {
            context.when(UserContext::isLoggedIn).thenReturn(true);
            context.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }
    }

    @Test
    void shouldRejectAuthenticatedUnknownRouteFailClosed() throws Exception {
        MockHttpServletRequest request = request("GET", "/unmapped-management-route");
        request.addHeader("Authorization", "Bearer session-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (var context = mockStatic(UserContext.class)) {
            context.when(UserContext::isLoggedIn).thenReturn(true);
            assertFalse(interceptor.preHandle(request, response, new Object()));
        }

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldAllowKnownAuthenticatedSelfServiceRoute() throws Exception {
        MockHttpServletRequest request = request("GET", "/auth/userinfo");
        request.addHeader("Authorization", "Bearer session-token");

        try (var context = mockStatic(UserContext.class)) {
            context.when(UserContext::isLoggedIn).thenReturn(true);
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
