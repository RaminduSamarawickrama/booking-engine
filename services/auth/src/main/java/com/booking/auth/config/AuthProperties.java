package com.booking.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("booking.auth")
public record AuthProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String signingKeyB64,
        boolean requireSigningKey,
        SeedAdmin seedAdmin) {

    public AuthProperties {
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
        seedAdmin = seedAdmin == null ? new SeedAdmin(null, null) : seedAdmin;
    }

    public record SeedAdmin(String email, String password) {
    }
}
