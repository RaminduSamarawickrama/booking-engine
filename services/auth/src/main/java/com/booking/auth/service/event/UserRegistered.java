package com.booking.auth.service.event;


import java.util.List;

import com.booking.platform.messaging.DomainEvent;

/** Published when any account is created; notification-service sends the welcome email. */
public record UserRegistered(String userId, String email, String fullName, List<String> roles) implements DomainEvent {

    @Override
    public String type() {
        return "user.registered";
    }

    @Override
    public String aggregateType() {
        return "user";
    }

    @Override
    public String aggregateId() {
        return userId;
    }
}
