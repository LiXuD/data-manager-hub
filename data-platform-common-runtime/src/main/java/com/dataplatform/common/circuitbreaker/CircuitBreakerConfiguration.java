package com.dataplatform.common.circuitbreaker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 熔断器配置类
 * 仅当 resilience4j-circuitbreaker 在 classpath 时生效
 * 使用字符串类名避免类加载问题
 */
@ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreakerConfig")
@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerManager circuitBreakerManager() {
        return new CircuitBreakerManager();
    }
}
