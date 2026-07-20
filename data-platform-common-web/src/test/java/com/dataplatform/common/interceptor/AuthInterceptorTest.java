package com.dataplatform.common.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
