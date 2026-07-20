package com.dataplatform.identity.security.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {
    private final PasswordService passwordService = new PasswordService();

    @Test
    void hashesAndMatchesPassword() {
        String encoded = passwordService.encode("StrongPassword123");

        assertNotEquals("StrongPassword123", encoded);
        assertTrue(passwordService.isEncoded(encoded));
        assertTrue(passwordService.matches("StrongPassword123", encoded));
        assertFalse(passwordService.matches("wrong", encoded));
    }

    @Test
    void acceptsLegacyPlaintextOnlyForMigration() {
        assertTrue(passwordService.matches("legacy-password", "legacy-password"));
        assertFalse(passwordService.isEncoded("legacy-password"));
    }
}
