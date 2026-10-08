package com.booking.platform;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A minimal service built only from the platform library, used by the integration tests. */
@SpringBootApplication
@EnableMethodSecurity
public class PlatformTestApplication {

    @RestController
    static class Endpoints {

        @GetMapping("/v1/public/ping")
        String ping() {
            return "pong";
        }

        @GetMapping("/v1/me")
        String me(java.security.Principal principal) {
            return principal.getName();
        }

        @GetMapping("/v1/admin/ping")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "admin";
        }
    }
}
