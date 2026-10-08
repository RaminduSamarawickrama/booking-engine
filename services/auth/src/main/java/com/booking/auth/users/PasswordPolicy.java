package com.booking.auth.users;

import com.booking.platform.web.ApiException;

/**
 * Length over complexity rules, following NIST SP 800-63B: at least 10 characters, at most
 * 72 bytes (bcrypt ignores anything longer, which would give a false sense of strength), and not
 * one of the most common passwords.
 */
public final class PasswordPolicy {

    static final int MIN = 10;
    static final int MAX = 72;

    private static final java.util.Set<String> COMMON = java.util.Set.of(
            "password123", "1234567890", "qwertyuiop", "password12", "iloveyou12", "welcome123",
            "admin12345", "letmein123", "1q2w3e4r5t", "qwerty1234", "abc1234567", "passw0rd12");

    private PasswordPolicy() {
    }

    public static void check(String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN) {
            throw ApiException.unprocessable("password_too_short", "Use at least " + MIN + " characters.");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX) {
            throw ApiException.unprocessable("password_too_long", "Use at most " + MAX + " bytes.");
        }
        if (COMMON.contains(password.toLowerCase(java.util.Locale.ROOT))) {
            throw ApiException.unprocessable("password_too_common", "This password is too common; choose another.");
        }
    }
}
