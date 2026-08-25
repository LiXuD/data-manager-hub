package com.dataplatform.access.approval.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowableSchemaConfigurationTest {

    @Test
    void qualifiesFlowableTablesWithoutChangingBusinessSchema() {
        ProcessEngineConfigurationConfigurer configurer =
                new FlowableSchemaConfiguration().flowableSchemaConfigurer();
        SpringProcessEngineConfiguration configuration =
                new SpringProcessEngineConfiguration();

        configurer.configure(configuration);

        assertEquals("workflow.", configuration.getDatabaseTablePrefix());
        assertTrue(configuration.isTablePrefixIsSchema());
    }
}
