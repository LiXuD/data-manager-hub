package com.dataplatform.common.circuitbreaker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 熔断器配置类
 */
@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    public CircuitBreakerManager circuitBreakerManager() {
        return new CircuitBreakerManager();
    }
}
