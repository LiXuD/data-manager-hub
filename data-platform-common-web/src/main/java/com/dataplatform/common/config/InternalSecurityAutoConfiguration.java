package com.dataplatform.common.config;

import com.dataplatform.common.feign.InternalAuthFeignInterceptor;
import com.dataplatform.common.security.InternalAuthenticationInterceptor;
import com.dataplatform.common.security.InternalJwtService;
import com.dataplatform.common.security.InternalSecurityProperties;
import com.dataplatform.common.security.ServiceTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(InternalSecurityProperties.class)
public class InternalSecurityAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.security.internal", name = "enabled", havingValue = "true")
    public InternalJwtService internalJwtService(InternalSecurityProperties properties, ResourceLoader resourceLoader) {
        return new InternalJwtService(properties, resourceLoader);
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.security.internal", name = "enabled", havingValue = "true")
    public InternalAuthenticationInterceptor internalAuthenticationInterceptor(
            InternalJwtService jwtService, ObjectMapper objectMapper) {
        return new InternalAuthenticationInterceptor(jwtService, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.security.internal", name = "enabled", havingValue = "true")
    public WebMvcConfigurer internalSecurityWebMvcConfigurer(InternalAuthenticationInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/internal/**");
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.security.internal", name = "enabled", havingValue = "true")
    public ServiceTokenProvider serviceTokenProvider(InternalSecurityProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTokenConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTokenReadTimeout());
        return new ServiceTokenProvider(properties, RestClient.builder().requestFactory(requestFactory));
    }

    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnProperty(prefix = "platform.security.internal", name = "enabled", havingValue = "true")
    public InternalAuthFeignInterceptor internalAuthFeignInterceptor(ServiceTokenProvider tokenProvider) {
        return new InternalAuthFeignInterceptor(tokenProvider);
    }
}
