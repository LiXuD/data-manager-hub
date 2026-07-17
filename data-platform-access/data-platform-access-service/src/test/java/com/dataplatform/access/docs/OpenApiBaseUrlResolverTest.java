package com.dataplatform.access.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OpenApiBaseUrlResolverTest {

    @Test
    void normalizesConfiguredOrigin() {
        assertEquals("https://api.example.com:8443",
                new OpenApiBaseUrlResolver(" HTTPS://api.example.com:8443/ ").resolve());
    }

    @Test
    void rejectsHostInjectionAndUrlsWithPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenApiBaseUrlResolver("https://api.example.com@evil.example"));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenApiBaseUrlResolver("https://api.example.com/internal"));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenApiBaseUrlResolver("javascript:alert(1)"));
    }
}
