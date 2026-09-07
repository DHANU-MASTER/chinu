package com.aiteacher.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for BCrypt password hashing and the legacy-hash migration shim.
 */
class PasswordHasherTests {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void encodesAndMatchesWithBcrypt() {
        String hash = hasher.encode("correct horse battery");
        assertTrue(hash.startsWith("$2"), "new hashes must be BCrypt");
        assertTrue(hasher.matches("correct horse battery", hash));
        assertFalse(hasher.matches("wrong password", hash));
    }

    @Test
    void legacyHashCodeHashesStillMatch() {
        String legacy = Integer.toHexString("oldPassword123".hashCode());
        assertTrue(hasher.matches("oldPassword123", legacy));
    }

    @Test
    void legacyHashesAreFlaggedForUpgrade() {
        String legacy = Integer.toHexString("oldPassword123".hashCode());
        assertTrue(hasher.needsUpgrade(legacy));
        assertFalse(hasher.needsUpgrade(hasher.encode("oldPassword123")));
    }

    @Test
    void blankOrNullOrWrongHashNeverMatches() {
        assertFalse(hasher.matches("anything", null));
        assertFalse(hasher.matches("anything", ""));
        assertFalse(hasher.matches(null, hasher.encode("x")));
    }
}
