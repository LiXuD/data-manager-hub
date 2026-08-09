package com.dataplatform.access.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.plugin.spi.ConnectorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    void lazyProviderReceivesOnlyTheCurrentStageReferences() throws Exception {
        ScopedConnectorSecretResolver resolver = new ScopedConnectorSecretResolver();
        AtomicReference<Set<String>> requested = new AtomicReference<>();
        var config = new ObjectMapper().readTree(
                "{\"auth\":{\"secretRef\":\"stage-a\"},\"irrelevant\":\"stage-b\"}");

        resolver.withSecretProvider(refs -> {
            requested.set(refs);
            return Map.of("stage-a", "one");
        }, () -> {
            resolver.enter(config);
            try (var secret = resolver.resolve("stage-a")) {
                assertEquals("one", secret.materialize());
            } finally {
                resolver.leave();
            }
            return null;
        });

        assertEquals(Set.of("stage-a"), requested.get());
    }

    @Test
    void rejectsAnOverBroadSecretResolutionResponse() throws Exception {
        ScopedConnectorSecretResolver resolver = new ScopedConnectorSecretResolver();
        var config = new ObjectMapper().readTree("{\"secretRef\":\"stage-a\"}");
        assertThrows(IllegalStateException.class, () -> resolver.withSecretProvider(
                refs -> Map.of("stage-a", "one", "other", "hidden"), () -> {
                    resolver.enter(config);
                    return null;
                }));
    }

    @Test
    void usesSignedSchemaReferencesForStringFieldsWithArbitraryNames() throws Exception {
        ScopedConnectorSecretResolver resolver = new ScopedConnectorSecretResolver();
        AtomicReference<Set<String>> requested = new AtomicReference<>();
        var config = new ObjectMapper().readTree("{\"signingMaterial\":\"vendor.signing\"}");

        resolver.withSecretProvider(refs -> {
            requested.set(refs);
            return Map.of("vendor.signing", "key-material");
        }, () -> {
            resolver.enter(config, Set.of("vendor.signing"));
            try (var secret = resolver.resolve("vendor.signing")) {
                assertEquals("key-material", secret.materialize());
            } finally {
                resolver.leave();
            }
            return null;
        });

        assertEquals(Set.of("vendor.signing"), requested.get());
    }
}
