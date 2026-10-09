package com.booking.auth.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Issuer and audience come from the shared platform config, so issuing and checking always agree. */
@Component
public class TokenSettings {

    private final String issuer;
    private final List<String> audiences;

    public TokenSettings(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") List<String> audiences) {
        this.issuer = issuer;
        this.audiences = List.copyOf(audiences);
    }

    public String issuer() {
        return issuer;
    }

    public List<String> audiences() {
        return audiences;
    }

    public OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
    }
}
