package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class Slf4jPluginLoggerTest {

    @Test
    void permitsOnlySafeFieldsAndRedactsSensitiveValues() {
        Logger logger = mock(Logger.class);
        Slf4jPluginLogger pluginLogger = new Slf4jPluginLogger(logger);

        pluginLogger.info("bad event with spaces", Map.of(
                "pluginId", "demo", "requestBody", "secret-payload", "vendorCode", "high-cardinality"));

        ArgumentCaptor<Object> fields = ArgumentCaptor.forClass(Object.class);
        verify(logger).info(eq("{} {}"), eq("plugin_event"), fields.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> safe = (Map<String, Object>) fields.getValue();
        assertEquals("demo", safe.get("pluginId"));
        assertEquals("[REDACTED]", safe.get("requestBody"));
        assertFalse(safe.containsKey("vendorCode"));
    }
}
