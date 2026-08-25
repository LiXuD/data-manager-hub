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
    void rejectsPlaintextPasswords() {
        assertFalse(passwordService.matches("plaintext-password", "plaintext-password"));
        assertFalse(passwordService.isEncoded("plaintext-password"));
    }

    @Test
    void enforcesLetterAndDigitPolicyWithoutBacktrackingRegex() {
        assertTrue(passwordService.isStrongEnough("Password123"));
        assertFalse(passwordService.isStrongEnough(null));
        assertFalse(passwordService.isStrongEnough("12345678"));
        assertFalse(passwordService.isStrongEnough("password"));
        assertFalse(passwordService.isStrongEnough("Pass123"));
    }
}
