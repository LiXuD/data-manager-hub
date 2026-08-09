package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorSafeMessageSanitizerTest {

    @Test
    void redactsResolvedSecretsCredentialsPrivateKeysAndLargeMessages() {
        String actualSecret = "vendor-secret-value-987654";
        String privateKey = "-----BEGIN PRIVATE KEY-----ABCDEF123456-----END PRIVATE KEY-----";
        String unsafe = "upstream=" + actualSecret
                + " Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature"
                + " Authorization=Basic dXNlcjpwYXNz"
                + " token=plain-token password:'plain-password' private_key=" + privateKey
                + " response=" + "x".repeat(4_000);

        String safe = ConnectorSafeMessageSanitizer.sanitize(unsafe, List.of(actualSecret));

        assertFalse(safe.contains(actualSecret));
        assertFalse(safe.contains("eyJhbGci"));
        assertFalse(safe.contains("dXNlcjpwYXNz"));
        assertFalse(safe.contains("plain-token"));
        assertFalse(safe.contains("plain-password"));
        assertFalse(safe.contains("ABCDEF"));
        assertTrue(safe.contains("[REDACTED]"));
        assertTrue(safe.length() <= ConnectorSafeMessageSanitizer.MAX_SAFE_MESSAGE_LENGTH);
    }
}
