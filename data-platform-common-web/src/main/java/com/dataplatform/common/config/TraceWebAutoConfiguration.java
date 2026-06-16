package com.dataplatform.common.config;

import com.dataplatform.common.filter.TraceIdMdcFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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
}
