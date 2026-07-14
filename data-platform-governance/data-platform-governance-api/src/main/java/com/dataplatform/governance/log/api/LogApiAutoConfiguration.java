package com.dataplatform.governance.log.api;

import com.dataplatform.common.log.OperationLogService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for log-api module.
 * Registers RemoteOperationLogService when LogClient is available.
 */
@AutoConfiguration
public class LogApiAutoConfiguration {

    @Bean
    @ConditionalOnBean(LogClient.class)
    @ConditionalOnMissingBean(OperationLogService.class)
    public RemoteOperationLogService remoteOperationLogService(LogClient logClient) {
        return new RemoteOperationLogService(logClient);
    }
}
