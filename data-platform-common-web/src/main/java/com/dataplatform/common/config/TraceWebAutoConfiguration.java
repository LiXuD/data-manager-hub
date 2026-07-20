package com.dataplatform.common.config;

import com.dataplatform.common.filter.HttpLoggingFilter;
import com.dataplatform.common.filter.TraceIdMdcFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 公共 Web 层配置的 Trace Web Auto Configuration。
 * <p>配置组件，集中声明本模块运行所需的 Spring Bean 或框架参数。</p>
 */
@Configuration
@ConditionalOnWebApplication
public class TraceWebAutoConfiguration {

    @Bean
    public FilterRegistrationBean<TraceIdMdcFilter> traceIdMdcFilter() {
        FilterRegistrationBean<TraceIdMdcFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdMdcFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<HttpLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new HttpLoggingFilter(objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
