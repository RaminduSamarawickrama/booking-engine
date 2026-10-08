package com.booking.auth.config;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import com.booking.auth.users.Accounts;
import com.booking.auth.users.Role;
import com.booking.auth.users.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin on an empty database so the admin dashboard can be used at all.
 * With no password configured, a random one is generated and printed once to the log.
 */
@Component
public class SeedAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAdmin.class);

    private final Accounts accounts;
    private final UserRepository users;
    private final AuthProperties properties;

    public SeedAdmin(Accounts accounts, UserRepository users, AuthProperties properties) {
        this.accounts = accounts;
        this.users = users;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.seedAdmin().email();
        if (email == null || email.isBlank() || users.existsWithRole(Role.ADMIN)) {
            return;
        }
        String password = properties.seedAdmin().password();
        boolean generated = password == null || password.isBlank();
        if (generated) {
            byte[] bytes = new byte[18];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        accounts.create(email, password, "Administrator", null, Set.of(Role.ADMIN));
        if (generated) {
            log.warn("Created admin {} with generated password: {}  (shown once; set AUTH_SEED_ADMIN_PASSWORD to choose one)",
                    email, password);
        } else {
            log.info("Created admin {}", email);
        }
    }
}
