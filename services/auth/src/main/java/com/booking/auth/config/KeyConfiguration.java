package com.booking.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
public class KeyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KeyConfiguration.class);

    @Bean
    SigningKey signingKey(AuthProperties properties) {
        String configured = properties.signingKeyB64();
        if (configured != null && !configured.isBlank()) {
            return SigningKey.fromBase64Pem(configured);
        }
        if (properties.requireSigningKey()) {
            throw new IllegalStateException("AUTH_JWT_PRIVATE_KEY_B64 is required in this profile");
        }
        log.warn("No AUTH_JWT_PRIVATE_KEY_B64 set: using a throwaway signing key. Tokens stop working on restart.");
        return SigningKey.generate();
    }

    @Bean
    JwtEncoder jwtEncoder(SigningKey key) {
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(key.jwk()));
        return new NimbusJwtEncoder(source);
    }

    /** auth-service checks its own tokens locally instead of fetching its own JWKS over HTTP. */
    @Bean
    JwtDecoder jwtDecoder(SigningKey key, TokenSettings settings) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSource(new ImmutableJWKSet<SecurityContext>(new JWKSet(key.jwk().toPublicJWK())))
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.issuer()),
                settings.audienceValidator()));
        return decoder;
    }
}
