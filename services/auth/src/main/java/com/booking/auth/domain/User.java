package com.booking.auth.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record User(UUID id, String email, String passwordHash, String fullName, String phone, Set<Role> roles,
        boolean enabled, Instant createdAt) {

    public User {
        roles = Set.copyOf(roles);
    }
}
