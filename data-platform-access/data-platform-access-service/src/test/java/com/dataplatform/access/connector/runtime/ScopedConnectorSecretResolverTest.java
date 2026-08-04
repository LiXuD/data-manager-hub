package com.dataplatform.access.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.plugin.spi.ConnectorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScopedConnectorSecretResolverTest {

    @Test
    void exposesOnlyReferencesDeclaredByCurrentStageConfig() throws Exception {
        ScopedConnectorSecretResolver resolver = new ScopedConnectorSecretResolver();
        var config = new ObjectMapper().readTree(
                "{\"auth\":{\"secretRef\":\"stage-a\"},\"secretRefs\":{\"alias\":\"stage-b\"}}");

        resolver.withSecrets(Map.of("stage-a", "one", "stage-b", "two", "other-stage", "hidden"), () -> {
            resolver.enter(config);
            try {
                try (var first = resolver.resolve("stage-a"); var second = resolver.resolve("stage-b")) {
                    assertEquals("one", first.materialize());
                    assertEquals("two", second.materialize());
                }
                assertThrows(ConnectorException.class, () -> resolver.resolve("other-stage"));
            } finally {
                resolver.leave();
            }
            return null;
        });
    }
}
