package com.eaglepoint.console.security;

import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

public class TokenService {
    private static final int TOKEN_BYTES = 32; // 256-bit

    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        return DigestUtils.sha256Hex(rawToken);
    }

    public boolean isExpired(String expiresAt) {
        if (expiresAt == null) return true;
        return Instant.parse(expiresAt).isBefore(Instant.now());
    }
}
