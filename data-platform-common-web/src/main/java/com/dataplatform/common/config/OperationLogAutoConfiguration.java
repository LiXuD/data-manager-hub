package com.dataplatform.common.config;

import com.dataplatform.common.log.OperationLogAspect;
import com.dataplatform.common.log.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OperationLogAspect.class)
public class OperationLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OperationLogAspect operationLogAspect(
            ObjectProvider<OperationLogService> operationLogServices,
            ObjectMapper objectMapper) {
        return new OperationLogAspect(operationLogServices, objectMapper);
    }
}
