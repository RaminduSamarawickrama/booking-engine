package com.booking.platform.security;

import com.booking.platform.config.PlatformProperties;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Default security for a service: stateless, every request needs an ES256 access token from
 * auth-service except actuator endpoints and the configured public paths. Roles come from the
 * token's {@code roles} claim and become {@code ROLE_*} authorities, so controllers can use
 * {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
public final class ResourceServerSecurity {

    public static final String ROLES_CLAIM = "roles";

    private ResourceServerSecurity() {
    }

    public static SecurityFilterChain defaultChain(HttpSecurity http, PlatformProperties properties) throws Exception {
        String[] publicPaths = properties.security().publicPaths().toArray(String[]::new);
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll();
                    if (publicPaths.length > 0) {
                        auth.requestMatchers(publicPaths).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter());
        return converter;
    }

    /** {@code "roles": ["ADMIN"]} becomes the authority {@code ROLE_ADMIN}. */
    public static JwtGrantedAuthoritiesConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(ROLES_CLAIM);
        roles.setAuthorityPrefix("ROLE_");
        return roles;
    }
}
