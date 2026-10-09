package com.booking.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.booking.auth.config.AuthProperties;
import com.booking.auth.config.SigningKey;
import com.booking.auth.config.TokenSettings;
import com.booking.auth.domain.Role;
import com.booking.auth.domain.User;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues short-lived ES256 access tokens. Other services verify them against the public JWKS. */
@Component
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final SigningKey key;
    private final TokenSettings settings;
    private final AuthProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, SigningKey key, TokenSettings settings, AuthProperties properties,
            Clock clock) {
        this.encoder = encoder;
        this.key = key;
        this.settings = settings;
        this.properties = properties;
        this.clock = clock;
    }

    public record Issued(String token, Instant expiresAt) {
    }

    public Issued issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        List<String> roles = user.roles().stream().map(Role::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .audience(settings.audiences())
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .claim("email", user.email())
                .claim("name", user.fullName())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(key.keyId()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Issued(token, expiresAt);
    }
}
