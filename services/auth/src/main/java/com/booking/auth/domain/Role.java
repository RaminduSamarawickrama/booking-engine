package com.booking.auth.domain;

/** Roles carried in the access token's {@code roles} claim. */
public enum Role {
    /** Books and pays for transfers. */
    CUSTOMER,
    /** Receives and runs rides; created by an admin when the driver is onboarded. */
    DRIVER,
    /** Operations staff: assigns rides and handles live issues. */
    DISPATCHER,
    /** Full access, including catalog, pricing and user management. */
    ADMIN
}
