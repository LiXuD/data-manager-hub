package com.dataplatform.log.api;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for log-api module.
 * Registers RemoteOperationLogService when LogClient is available.
 */
@Configuration
@ComponentScan(basePackageClasses = LogApiAutoConfiguration.class)
public class LogApiAutoConfiguration {
}
