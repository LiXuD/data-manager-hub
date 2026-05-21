package com.dataplatform.common.feign;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 全局配置 - 追踪上下文传播
 */
@Configuration
public class TraceFeignConfig {

    @Bean
    public TraceFeignRequestInterceptor traceFeignRequestInterceptor() {
        return new TraceFeignRequestInterceptor();
    }
}
