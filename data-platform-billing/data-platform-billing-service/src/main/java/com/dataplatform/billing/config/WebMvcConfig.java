package com.dataplatform.billing.config;

import com.dataplatform.common.interceptor.AuthInterceptor;
import com.dataplatform.common.security.InternalSecurityProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 计费域配置的 Web Mvc Config。
 * <p>配置组件，集中声明本模块运行所需的 Spring Bean 或框架参数。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final InternalSecurityProperties internalSecurity;

    public WebMvcConfig(InternalSecurityProperties internalSecurity) {
        this.internalSecurity = internalSecurity;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> excludes = new ArrayList<>(List.of(
                "/auth/**",
                "/actuator/**",
                "/health/**",
                "/error"
        ));
        if (internalSecurity.isEnabled()) {
            excludes.add("/internal/**");
        }
        InterceptorRegistration registration = registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/**");
        registration.excludePathPatterns(excludes);
    }
}
