package com.dataplatform.identity.security.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        return isEncoded(storedPassword) && encoder.matches(rawPassword, storedPassword);
    }

    public boolean isStrongEnough(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < rawPassword.length(); i++) {
            char character = rawPassword.charAt(i);
            hasLetter |= (character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z');
            hasDigit |= character >= '0' && character <= '9';
            if (hasLetter && hasDigit) {
                return true;
            }
        }
        return false;
    }

    public boolean isEncoded(String password) {
        return password != null && (password.startsWith("$2a$")
                || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }
}
