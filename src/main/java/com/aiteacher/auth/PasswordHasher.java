package com.aiteacher.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Central password hashing component. Uses BCrypt for all new hashes and
 * transparently migrates legacy hashes (the old insecure
 * {@code Integer.toHexString(password.hashCode())} scheme) on successful
 * login: the plain-text comparison happens once, then the stored hash is
 * upgraded to BCrypt.
 */
@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Hash a plain-text password with BCrypt. */
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * Check a raw password against a stored hash. Supports both BCrypt and
     * legacy hashCode hashes so existing H2 accounts keep working.
     *
     * @return true when the password matches the stored hash
     */
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith("$2")) {
            return encoder.matches(rawPassword, storedHash);
        }
        // Legacy scheme: Integer.toHexString(password.hashCode())
        return legacyHash(rawPassword).equals(storedHash);
    }

    /** True when the stored hash predates BCrypt and should be re-hashed. */
    public boolean needsUpgrade(String storedHash) {
        return storedHash != null && !storedHash.isBlank() && !storedHash.startsWith("$2");
    }

    private static String legacyHash(String rawPassword) {
        return Integer.toHexString(rawPassword.hashCode());
    }
}
