package com.dataplatform.access.approval.config;

import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps Flowable tables isolated in the workflow schema without changing the
 * connection's default schema used by business MyBatis mappers.
 */
@Configuration
public class FlowableSchemaConfiguration {

    static final String FLOWABLE_TABLE_PREFIX = "workflow.";

    @Bean
    public ProcessEngineConfigurationConfigurer flowableSchemaConfigurer() {
        return configuration -> {
            configuration.setDatabaseTablePrefix(FLOWABLE_TABLE_PREFIX);
            configuration.setTablePrefixIsSchema(true);
        };
    }
}
