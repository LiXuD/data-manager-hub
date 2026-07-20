package com.dataplatform.identity.config;

import com.dataplatform.common.security.InternalSecurityProperties;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebMvcConfigTest {

    @Test
    void shouldApplyCentralAuthenticationToProtectedAuthEndpoints() {
        InternalSecurityProperties properties = new InternalSecurityProperties();
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();

        new WebMvcConfig(properties).addInterceptors(registry);

        MappedInterceptor interceptor = (MappedInterceptor) registry.interceptors().get(0);
        List<String> excludes = Arrays.asList(interceptor.getExcludePathPatterns());
        assertFalse(excludes.contains("/auth/**"));
        assertFalse(excludes.contains("/identity/auth/**"));
        assertTrue(Arrays.asList(interceptor.getIncludePathPatterns()).contains("/**"));
    }

    private static final class ExposedInterceptorRegistry extends InterceptorRegistry {
        List<Object> interceptors() {
            return super.getInterceptors();
        }
    }
}
