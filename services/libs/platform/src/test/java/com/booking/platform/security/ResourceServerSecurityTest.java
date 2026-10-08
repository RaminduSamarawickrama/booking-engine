package com.booking.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ResourceServerSecurityTest {

    @Test
    void rolesClaimBecomesRoleAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .subject("user-1")
                .claim("roles", List.of("ADMIN", "DISPATCHER"))
                .claim("scope", "ignored")
                .build();

        var authentication = ResourceServerSecurity.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("user-1");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DISPATCHER");
    }
}
